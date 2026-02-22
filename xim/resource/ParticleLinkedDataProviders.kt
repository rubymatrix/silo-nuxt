package xim.resource

import xim.math.Vector3f
import xim.poc.*
import xim.poc.audio.AudioManager
import xim.poc.camera.CameraReference
import xim.poc.game.ActorPromise
import xim.poc.game.GameEngine
import xim.poc.gl.ByteColor
import xim.poc.gl.FrameBufferManager
import xim.poc.gl.MeshBuffer
import xim.poc.gl.RingMeshBuilder
import xim.util.OnceLogger
import xim.util.fallOff

fun interface MeshProvider {
    fun hasMeshes(particle: Particle): Boolean { return true }
    fun getMeshes(particle: Particle) : List<MeshBuffer>
}

class RingMeshProvider() : MeshProvider {

    override fun getMeshes(particle: Particle): List<MeshBuffer> {
        val ringParams = particle.ringMeshParams ?: throw IllegalStateException("[${particle.datId}] Ring params weren't specified?")
        return RingMeshBuilder.buildMesh(ringParams)
    }
}

class DistortionMeshProvider(): MeshProvider {

    override fun getMeshes(particle: Particle): List<MeshBuffer> {
        return RingMeshBuilder.buildMesh(makeRingParams())
    }

    private fun makeRingParams(): RingParams {
        // In the client, the inner layer is actually the particle's t-factor, and the t-factor is just (80,80,80,80).
        // The difference should be negligible, and this is easier to model.
        // The outer-layer is accurate at a constant (80,80,80,00)
        return RingParams(
            layerRadius = listOf(0f, 1f),
            layerColor = listOf(ByteColor(0x80, 0x80, 0x80, 0x80), ByteColor(0x80, 0x80, 0x80, 0x00)),
            verticesPerLayer = 4,
            numLayers = 2,
            textureLink = FrameBufferManager.getHazeBuffer().texture
        )
    }

}

class StaticMeshProvider(val meshes: List<MeshBuffer>, val resource: DatResource) : MeshProvider {
    override fun getMeshes(particle: Particle): List<MeshBuffer> {
        return meshes
    }
}

class SpriteSheetMeshProvider(val spriteSheet: SpriteSheet) : MeshProvider {
    override fun getMeshes(particle: Particle): List<MeshBuffer> {
        return listOf(spriteSheet.meshes[particle.spriteSheetIndex])
    }
}

class NoMeshProvider : MeshProvider {
    override fun hasMeshes(particle: Particle): Boolean { return false }

    override fun getMeshes(particle: Particle): List<MeshBuffer> {
        return emptyList()
    }
}

class WeightedMeshProvider(val weightedMesh: WeightedMesh) : MeshProvider {
    override fun getMeshes(particle: Particle): List<MeshBuffer> {
        return listOf(weightedMesh.getWeightedBuffer(particle.weightedMeshWeights))
    }
}

class AudioEmitter(link: DatLink<DatResource>, effectDirectory: DirectoryResource) {

    private val soundPointerResource: SoundPointerResource?

    init {
        soundPointerResource = link.getOrPut {
            effectDirectory.searchLocalAndParentsById(it, SoundPointerResource::class) ?:
            GlobalDirectory.directoryResource.getNullableChildRecursivelyAs(it, SoundPointerResource::class)
        } as SoundPointerResource?

        if (soundPointerResource == null) {
            OnceLogger.warn("[Particle Audio] Not found! ${link.id}")
        }
    }

    fun update(particle: Particle) {
        soundPointerResource ?: return

        val currentVolume = volumeFn(positionFn(particle), particle)
        val shouldCull = currentVolume != null && currentVolume <= 0f

        if (!shouldCull && particle.emittedAudio.isEmpty()) {
            val emitted = AudioManager.playSoundEffect(
                soundPointerResource,
                particle.association,
                looping = particle.audioConfiguration.looping,
                highPriority = particle.audioConfiguration.looping,
                positionFn = { positionFn(particle) },
                volumeFn = { volumeFn(it, particle) }
            )

            if (emitted != null) {
                particle.emittedAudio += emitted
            }
        }

        if (shouldCull) {
            particle.emittedAudio.forEach { it.stop() }
            particle.emittedAudio.removeAll { it.isComplete() }
        }
    }

    private fun positionFn(particle: Particle): Vector3f {
        val pathLink = particle.audioConfiguration.pathLink?.getIfPresent()
        return if (pathLink != null) {
            val nearestPoint = pathLink.pathDefinition.nearestPoint(CameraReference.getInstance().getPosition())
            nearestPoint.first
        } else {
            particle.getWorldSpacePosition()
        }
    }

    private fun volumeFn(position: Vector3f?, particle: Particle): Float? {
        if (position == null) { return 0f }
        if (particle.audioConfiguration.farDistance <= 0f) { return null }

        val listenerPos = AudioManager.getListenerPosition()
        val distance = Vector3f.distance(position, listenerPos)

        val far = particle.audioConfiguration.farDistance
        val near = particle.audioConfiguration.nearDistance

        return AudioManager.volumeSettings.effectVolume * particle.audioConfiguration.volumeMultiplier * distance.fallOff(near, far)
    }

}

class ParticleActorLink(
    val routineId: DatId,
    val actorPromise: ActorPromise,
    val directory: DirectoryResource,
    val targetId: ActorId?,
    val originalAssociation: ActorAssociation,
) {

    private var routineStarted = false

    fun update() {
        if (routineStarted) { return }
        val actor = ActorManager[actorPromise.getIfReady()] ?: return

        routineStarted = true
        actor.enqueueModelRoutine(routineId = routineId, actorContext = ActorContext(
            originalActor = actor.id,
            primaryTargetId = targetId ?: actor.id,
            targetAoeCenter = originalAssociation.context.targetAoeCenter,
        ))
    }

    fun cleanUp() {
        actorPromise.onReady { GameEngine.submitDeleteActor(it.id) }
    }

}

object ParticleMeshResolver {

    fun getParticleMesh(linkedDataType: LinkedDataType, link: DatLink<DatResource>, localDir: DirectoryResource, generatorId: DatId): MeshProvider {
        return when (linkedDataType) {
            LinkedDataType.Actor -> NoMeshProvider()
            LinkedDataType.StaticMesh -> resolveStaticMeshLink(link, localDir)
            LinkedDataType.SpriteSheet -> resolveSpriteSheetLink(link, localDir)
            LinkedDataType.WeightedMesh -> resolveWeightedMesh(link, localDir)
            LinkedDataType.Distortion -> DistortionMeshProvider()
            LinkedDataType.RingMesh -> RingMeshProvider()
            LinkedDataType.LensFlare -> resolveSpriteSheetLink(link, localDir)
            LinkedDataType.Audio -> NoMeshProvider()
            LinkedDataType.PointLight -> NoMeshProvider()
            LinkedDataType.Null -> NoMeshProvider() // nulp
            LinkedDataType.Unknown -> throw IllegalStateException("Don't know how to handle linked data type ${linkedDataType}: ${link.id}")
        }
    }

    private fun resolveStaticMeshLink(link: DatLink<DatResource>, effectDirectory: DirectoryResource) : StaticMeshProvider {
        val resource = link.getOrPut {
            effectDirectory.searchLocalAndParentsById(it, ParticleMeshResource::class)
                ?: effectDirectory.searchLocalAndParentsById(it, ZoneMeshResource::class)
                ?: effectDirectory.root().getNullableChildRecursivelyAs(it, ParticleMeshResource::class)
                ?: effectDirectory.root().getNullableChildRecursivelyAs(it, ZoneMeshResource::class)
                ?: GlobalDirectory.directoryResource.getNullableChildRecursivelyAs(it, ParticleMeshResource::class)
                ?: GlobalDirectory.directoryResource.getNullableChildRecursivelyAs(it, ZoneMeshResource::class)
        }

        return when (resource) {
            is ZoneMeshResource -> StaticMeshProvider(resource.meshes, resource)
            is ParticleMeshResource -> StaticMeshProvider(resource.particleDef.particleMeshes, resource)
            else -> throw IllegalStateException("[StaticMesh] Not found! ${link.id}")
        }
    }

    private fun resolveSpriteSheetLink(link: DatLink<DatResource>, effectDirectory: DirectoryResource) : SpriteSheetMeshProvider {
        val resource = link.getOrPut {
            effectDirectory.getNullableChildRecursivelyAs(it, SpriteSheetResource::class)
                ?: effectDirectory.root().getNullableChildRecursivelyAs(it, SpriteSheetResource::class)
                ?: GlobalDirectory.directoryResource.getNullableChildRecursivelyAs(it, SpriteSheetResource::class)
        } ?: throw IllegalStateException("[SpriteSheet] Not found! ${link.id}")

        return SpriteSheetMeshProvider((resource as SpriteSheetResource).spriteSheet)
    }

    private fun resolveWeightedMesh(link: DatLink<DatResource>, effectDirectory: DirectoryResource): WeightedMeshProvider {
        val resource = link.getOrPut { effectDirectory.searchLocalAndParentsById(it, WeightedMeshResource::class) }
            ?: throw IllegalStateException("[WeightedMesh] Not found! ${link.id}")

        return WeightedMeshProvider((resource as WeightedMeshResource).weightedMesh)
    }

}