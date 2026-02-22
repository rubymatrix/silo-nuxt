package xim.resource

import xim.util.OnceLogger

interface ParticleExpirationHandler {
    fun read(byteReader: ByteReader, readContext: ReadContext)
    fun onExpire(particle: Particle)
}

interface NoDataExpirationHandler: ParticleExpirationHandler {
    override fun read(byteReader: ByteReader, readContext: ReadContext) { }
}

class RepeatExpirationHandler: NoDataExpirationHandler {
    override fun onExpire(particle: Particle) {
        particle.resetAge()
    }
}

class EmitChildHandler: ParticleExpirationHandler {

    private lateinit var generatorLink: DatLink<EffectResource>

    override fun read(byteReader: ByteReader, readContext: ReadContext) {
        expectZero32(byteReader)
        generatorLink = DatLink(byteReader.nextDatId())
    }

    override fun onExpire(particle: Particle) {
        val resource = generatorLink.getOrPut {
            val localDir = particle.creator.localDir
            localDir.getNullableChildRecursivelyAs(it, EffectResource::class)
                ?: localDir.root().getNullableChildRecursivelyAs(it, EffectResource::class)
        }

        if (resource == null) {
            OnceLogger.warn("[${particle.datId}] Couldn't find child generator for expiration: ${generatorLink.id}")
            return
        }

        val particleGenerator = ParticleGenerator(resource, particle.association, parent = particle)
        particle.children += particleGenerator.emit(0f)  {
            it.useParentAssociatedPositionOnly = true
        }
    }

}