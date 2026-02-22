package xim.poc

import xim.math.Vector3f
import xim.poc.game.ActorState
import xim.resource.*
import xim.util.RandHelper
import kotlin.math.*

class RayCollision(val collisionProperty: CollisionProperty, val position: Vector3f, val distance: Float)

class CollisionDepth(var amount: Float = Float.POSITIVE_INFINITY, var axis: Vector3f = Vector3f.ZERO, var verticalEscape: Float? = null) {

    fun applyIfSmaller(other: CollisionDepth) {
        val myEscape = verticalEscape
        if (myEscape != null && myEscape > 0f && myEscape < (other.verticalEscape ?: Float.POSITIVE_INFINITY)) {
            other.verticalEscape = myEscape
        }

        if (abs(amount) >= abs(other.amount)) { return }
        other.amount = amount
        other.axis = axis
    }

}

data class ProjectionRange(val min: Float, val max: Float) {

    fun overlapAmount(other: ProjectionRange) : Float? {
        val overlap = overlap(min, max, other.min, other.max) ?: return null
        return if (abs(overlap) < 0.0001f) { null } else { overlap }
    }

    companion object {
        fun overlap(amin: Float, amax: Float, bmin: Float, bmax: Float): Float? {
            if (amax < bmin || bmax < amin) return null
            val depth = 0f

            //we've proven that they do intersect, so...
            if (amin <= bmin) {
                //the case where bs are inside as
                return if (amax > bmax) {
                    //if the distance between maxs is less...
                    if (abs(amax - bmax) < abs(amin - bmin)) {
                        bmin - amax
                    } else bmax - amin
                } else {
                    bmin - amax
                }
            }
            return if (bmin <= amin) {
                //the case where as are inside bs
                if (bmax > amax) {
                    //if the distance between maxs is less...
                    if (abs(amax - bmax) < abs(amin - bmin)) {
                        bmax - amin
                    } else bmin - amax
                } else {
                    bmax - amin
                }
            } else depth
        }

    }

}

data class CollisionProperty(val mapId: Int?, val environmentId: DatId?, val cullingTableIndex: Int?, val lightIndices: List<Int>, val terrainType: TerrainType)

class CollisionContext(
    val collisionSize: Vector3f,
    val gravityPass: Boolean,
    val interactions: List<ZoneInteraction>,
)

object Collider {

    private const val maxVerticalEscapeStep = 0.4001f

    fun updatePosition(areas: List<Area>, actorState: ActorState, velocity: Vector3f, context: CollisionContext): Map<Area, List<CollisionProperty>> {
        val magnitude = velocity.magnitude()
        val stepDirection = velocity.normalize()

        var accumulator = 0f
        var collisionResults: Map<Area, List<CollisionProperty>> = emptyMap()

        while (accumulator < magnitude) {
            val stepSize = min(magnitude - accumulator, 0.05f)
            accumulator += stepSize

            val startingPosition = Vector3f(actorState.position)
            actorState.position += (stepDirection * stepSize)

            val terrainCollision = updatePositionIteration(areas, actorState, context)
            if (terrainCollision.isNotEmpty()) { collisionResults = terrainCollision }

            val interactionCollision = context.interactions.mapNotNull { updatePositionAgainstInteraction(actorState, context.collisionSize, it) }
            if (interactionCollision.isNotEmpty()) { collisionResults = mapOf(SceneManager.getCurrentScene().getMainArea() to interactionCollision) }

            if (Vector3f.distanceSquared(startingPosition, actorState.position) < 0.001f) { break }
        }

        return collisionResults
    }

    private fun updatePositionAgainstInteraction(actorState: ActorState, bbScale: Vector3f, interaction: ZoneInteraction): CollisionProperty? {
        val boundingBox = BoundingBox.scaled(bbScale, actorState.position)
        val depth = SatCollider.boxBoxIntersection(boundingBox, interaction.boundingBox) ?: return null

        val verticalEscape = depth.verticalEscape
        actorState.position += if (verticalEscape != null && verticalEscape < 0.25f) {
            Vector3f.NegY * verticalEscape
        } else {
            depth.axis * (depth.amount * 1.005f)
        }

        return SceneManager.getCurrentScene().mapInteractionToCollisionProperty(interaction)
    }

    private fun updatePositionIteration(areas: List<Area>, actorState: ActorState, context: CollisionContext): Map<Area, List<CollisionProperty>> {
        val perAreaCollision = HashMap<Area, List<CollisionProperty>>()

        for (area in areas) {
            val areaCollisionMap = area.getZoneResource().zoneCollisionMap ?: continue
            val collisionObjects = areaCollisionMap.getCollisionObjects(actorState.position)

            val collisionTriangles = collisionObjects.flatMap { checkObjectCollision(actorState, context.collisionSize, it) }
                .flatMap { it.worldSpaceTriangle }
                .sortedByDescending { abs(it.normal.y) }

            var chosenTriangle: Triangle? = null

            for (collisionTriangle in collisionTriangles) {
                if (!collideWithTriangle(actorState, collisionTriangle, context)) { continue }
                chosenTriangle = collisionTriangle
            }

            val obj = chosenTriangle?.obj ?: continue
            perAreaCollision[area] = listOf(mapToCollisionProperty(obj, chosenTriangle))
        }

        return perAreaCollision
    }

    private fun checkObjectCollision(actorState: ActorState, scale: Vector3f, collisionObjectGroup: CollisionObjectGroup): List<CollisionObject> {
        val collisions = ArrayList<CollisionObject>()

        val offsetPosition = Vector3f(actorState.position).also { it.y -= scale.x }
        val actorSphere = Sphere(offsetPosition, scale.x)

        val currentScene = SceneManager.getCurrentScene()

        for (obj in collisionObjectGroup.collisionObjects) {
            if (obj.transformInfo.subAreaLinkId != null && currentScene.isCurrentSubArea(obj.transformInfo.subAreaLinkId)) {
                continue // defer to the loaded sub-area's collision instead
            }

            if (!Sphere.intersects(actorSphere, obj.worldSpaceBoundingSphere)) { continue }
            collisions += obj
        }

        return collisions
    }

    private fun collideWithTriangle(actorState: ActorState, tri: Triangle, collisionContext: CollisionContext): Boolean {
        val center = Vector3f(actorState.position).also { it.y -= collisionContext.collisionSize.x }
        val sphere = Sphere(center, radius = collisionContext.collisionSize.x)

        // Ignore triangles that the actor is on the "wrong side"
        // This is necessary for hit-walls & one-way walls, but also helps with stability in general.
        RayPlaneCollider.intersect(planeNormal = tri.normal, planePoint = tri.t0, origin = center, direction = tri.normal * -1f, bidirectional = false)
            ?: return false

        val depthResult = TriangleSphereCollider.intersect(tri, sphere) ?: return false

        resolveCollision(actorState, tri, depthResult, collisionContext)
        return true
    }

    private fun resolveCollision(actorState: ActorState, tri: Triangle, depth: CollisionDepth, context: CollisionContext) {
        val verticalEscape = limitVerticalEscape(actorState, tri, depth, context)

        actorState.position += if (verticalEscape > 0f) {
            Vector3f.NegY * verticalEscape
        } else {
            depth.axis * (depth.amount * 1.015f)
        }
    }

    private fun limitVerticalEscape(actorState: ActorState, tri: Triangle, depth: CollisionDepth, context: CollisionContext): Float {
        val verticalEscape = depth.verticalEscape ?: return 0f

        // Allow all types of NPCs to always vertical escape (avoid getting caught on weird terrain)
        if (!actorState.isPc() && context.gravityPass) { return verticalEscape }

        // Don't allow jumps that are too large. Be more lenient for NPCs to avoid getting stuck due to bad pathing.
        if (verticalEscape > maxVerticalEscapeStep) { return 0f }

        // Small hack to prevent climbing onto medium-sized objects
        if (tri.type == TerrainType.Object && verticalEscape > maxVerticalEscapeStep / 2f) { return 0f }

        // Slope checks - want to prevent sliding on relatively flat terrain, while also accounting for stairs.
        // Stairs aren't necessarily 90-degree corners - there are tons of sloped stairs (ex: in [Selbina]).
        val slope = tri.normal.dot(Vector3f.NegY)

        if (slope < 0.75f && tri.height() > 0.4f) {
            // Allow stepping onto (& prevent sliding on) ~flat triangles, or triangles that are "short".
            // This helps with a variety of terrain & slopped steps.
            return 0f
        } else if (slope < 0.5f) {
            // Slide down steep surfaces
            return 0f
        }

        // Don't snap in free-fall. It allows ledge-walking bugs in high frame-rates (less gravity per frame)
        if (actorState.lastCollisionResult.isInFreeFall()) { return 0f }

        return verticalEscape
    }

    fun nearestFloor(position: Vector3f): RayCollision? {
        val (_, collision) = nearestLocalCollision(ray = Ray(origin = position, direction = Vector3f.Y)) ?: return null
        return collision
    }

    fun nearestLocalCollision(ray: Ray, localPosition: Vector3f = ray.origin, ignoreHitWalls: Boolean = false, includeInteractionCollision: Boolean = false, maxSteps: Int = 1, mainAreaOnly: Boolean = false): Pair<Area, RayCollision>? {
        val scene = SceneManager.getNullableCurrentScene() ?: return null
        val areas = if (mainAreaOnly) { listOf(scene.getMainArea()) } else { scene.getAreas() }

        var nearestCollision: Pair<Area, RayCollision>? = null

        for (area in areas) {
            val areaCollision = nearestLocalCollision(ray, area, localPosition, ignoreHitWalls, maxSteps) ?: continue
            if (nearestCollision != null && areaCollision.distance > nearestCollision.second.distance) { continue }
            nearestCollision = area to areaCollision
        }

        if (!includeInteractionCollision) { return nearestCollision }

        for (interaction in scene.getInteractionCollision()) {
            val interactionCollision = RayBoxCollider.intersect(ray, interaction.boundingBox) ?: continue
            if (nearestCollision != null && interactionCollision.second > nearestCollision.second.distance) { continue }

            val property = scene.mapInteractionToCollisionProperty(interaction)
            nearestCollision = scene.getMainArea() to RayCollision(property, interactionCollision.first, interactionCollision.second)
        }

        return nearestCollision
    }

    private fun nearestLocalCollision(ray: Ray, area: Area, localPosition: Vector3f, ignoreHitWalls: Boolean, maxSteps: Int): RayCollision? {
        val collisionMap = area.getZoneResource().zoneCollisionMap ?: return null
        val collisionGroups = collisionMap.getCollisionObjects(localPosition, maxSteps)
        return collisionGroups.mapNotNull { nearestObjectGroupCollision(ray, it, ignoreHitWalls) }.minByOrNull { it.distance }
    }

    fun nearestObjectGroupCollision(ray: Ray, collisionObjectGroup: CollisionObjectGroup, ignoreHitWalls: Boolean): RayCollision? {
        val currentScene = SceneManager.getCurrentScene()
        var nearest: RayCollision? = null

        for (collisionObject in collisionObjectGroup.collisionObjects) {
            if (collisionObject.transformInfo.subAreaLinkId != null && currentScene.isCurrentSubArea(collisionObject.transformInfo.subAreaLinkId)) {
                continue // defer to the loaded sub-area's collision instead
            }

            val intersection = getObjectDistance(ray, collisionObject, ignoreHitWalls) ?: continue

            if (nearest == null) {
                nearest = intersection
            } else if (nearest.distance > intersection.distance) {
                nearest = intersection
            }
        }

        return nearest
    }

    private fun getObjectDistance(ray: Ray, collisionObject: CollisionObject, ignoreHitWalls: Boolean): RayCollision? {
        if (!RaySphereCollider.intersect(ray, collisionObject.worldSpaceBoundingSphere)) { return null }

        val collisionSpaceRay = collisionObject.transformInfo.toCollisionSpace.transformDirectionVector(ray.direction)
        val collisionSpacePosition = collisionObject.transformInfo.toCollisionSpace.transform(ray.origin)

        var nearestFloor: Pair<Vector3f, Float>? = null
        var chosenTriangle: Triangle? = null

        for (tri in collisionObject.collisionMesh.tris) {
            if (ignoreHitWalls && tri.material.hitWall) { continue }
            val intersection = RayTriangleCollider.intersect(tri, collisionSpacePosition, collisionSpaceRay) ?: continue

            if (nearestFloor != null && nearestFloor.second < intersection.second) { continue }

            val worldSpaceIntersection = collisionObject.transformInfo.toWorldSpace.transform(intersection.first)
            nearestFloor = Pair(worldSpaceIntersection, intersection.second)
            chosenTriangle = tri
        }

        if (nearestFloor == null || chosenTriangle == null) { return null }

        val property = mapToCollisionProperty(collisionObject, chosenTriangle)
        return RayCollision(property, nearestFloor.first, nearestFloor.second)
    }

    fun collideNavSphere(position: Vector3f, radius: Float, areas: List<Area>): Float? {
        return areas.mapNotNull { collideNavSphere(position, radius, it) }.maxOrNull()
    }

    private fun collideNavSphere(position: Vector3f, radius: Float, area: Area): Float? {
        val collisionMap = area.getZoneResource().zoneCollisionMap ?: return null
        val collisionGroups = collisionMap.getCollisionObjects(position)

        var maxEscapeRequired: Float? = null

        for (collisionObjectGroup in collisionGroups) {
            for (collisionObject in collisionObjectGroup.collisionObjects) {
                val escapeRequired = collideNavSphereWithObject(position, radius, collisionObject) ?: continue
                maxEscapeRequired = max(escapeRequired, maxEscapeRequired ?: 0f)
            }
        }

        return maxEscapeRequired
    }

    private fun collideNavSphereWithObject(position: Vector3f, radius: Float, obj: CollisionObject): Float? {
        val offsetPosition = Vector3f(position).also { it.y -= radius }

        val actorSphere = Sphere(offsetPosition, radius)
        if (!Sphere.intersects(actorSphere, obj.worldSpaceBoundingSphere)) { return null }

        var maxEscapeRequired: Float? = null

        for (tri in obj.worldSpaceTriangle) {
            val depthResult = TriangleSphereCollider.intersect(tri, actorSphere) ?: continue

            if (tri.material.hitWall || tri.type == TerrainType.Object) {
                val isFlat = abs(tri.normal.dot(Vector3f.Y)) > 0.8f
                if (!isFlat) { return Float.POSITIVE_INFINITY }
            }

            if (depthResult.amount < 1e-5) { continue }

            val slope = abs(tri.normal.dot(Vector3f.NegY))
            val height = tri.height()
            if (slope < 0.80f && height > radius) { depthResult.verticalEscape = null }

            val verticalEscape = depthResult.verticalEscape ?: return Float.POSITIVE_INFINITY
            maxEscapeRequired = max(verticalEscape, maxEscapeRequired ?: 0f)
        }

        return maxEscapeRequired
    }

    fun pushAgainst(actors: Collection<ActorState>, bbRadius: Float, maxPushPerFrame: Float, elapsedFrames: Float) {
        val maxPush = maxPushPerFrame * elapsedFrames

        val remainingActors = ArrayDeque(actors)

        while (remainingActors.isNotEmpty()) {
            val actor = remainingActors.removeFirst()

            for (other in remainingActors) {
                val targetingDistance = actor.getTargetingDistance(other)
                if (targetingDistance > bbRadius) { continue }

                var direction = actor.position - other.position
                val magnitude = direction.magnitude()

                direction = if (magnitude < 1e-7f) {
                    Vector3f(RandHelper.rand(), 0f, RandHelper.rand()).normalizeInPlace()
                } else {
                    direction * (1f/magnitude)
                }

                val fixAmount = min(maxPush, bbRadius - targetingDistance)

                val actorCollision = actor.behaviorController.getActorCollisionType()
                val otherCollision = other.behaviorController.getActorCollisionType()

                if (actorCollision.canBeMoved && otherCollision.canMoveOthers) { actor.position += direction * fixAmount }
                if (otherCollision.canBeMoved && actorCollision.canMoveOthers) { other.position -= direction * fixAmount }
            }
        }
    }

    private fun mapToCollisionProperty(obj: CollisionObject, tri: Triangle): CollisionProperty {
        return CollisionProperty(
            mapId = obj.transformInfo.mapId,
            environmentId = obj.transformInfo.environmentId,
            cullingTableIndex = obj.transformInfo.cullingTableIndex,
            lightIndices = obj.transformInfo.lightIndices,
            terrainType = tri.type)
    }

}

object SatCollider {

    fun boxTriSeparatingAxisTheorem(b: BoundingBox, t: Triangle) : CollisionDepth? {
        // For SAT, prove there's overlap against all 13 axis
        val minDepth = CollisionDepth()

        // 3 are trivial from the AABB
        boxTriAxisCheck(b, t, Vector3f.X)?.applyIfSmaller(minDepth) ?: return null
        boxTriAxisCheck(b, t, Vector3f.Y)?.applyIfSmaller(minDepth) ?: return null
        boxTriAxisCheck(b, t, Vector3f.Z)?.applyIfSmaller(minDepth) ?: return null

        // 1 from the triangle's normal
        boxTriAxisCheck(b, t, t.normal)?.applyIfSmaller(minDepth) ?: return null

        // And 9 from the combination of edges
        for (unitAxis in listOf(Vector3f.X, Vector3f.Y, Vector3f.Z)) {
            for (edge in t.getEdges()) {
                val axis = unitAxis.cross(edge)
                if (axis.magnitudeSquare() < 0.001f) { continue }
                axis.normalizeInPlace()
                boxTriAxisCheck(b, t, axis)?.applyIfSmaller(minDepth) ?: return null
            }
        }

        return minDepth
    }

    private fun boxTriAxisCheck(b: BoundingBox, t: Triangle, axis: Vector3f) : CollisionDepth? {
        val boxProjection = projectVertices(b.vertices, axis)
        val triProjection = projectVertices(t.vertices, axis)
        val overlapAmount = boxProjection.overlapAmount(triProjection) ?: return null

        val verticalProjection = axis.dot(Vector3f.NegY)
        val verticalEscape = if (abs(verticalProjection) < 0.0001) { Float.POSITIVE_INFINITY } else { overlapAmount / verticalProjection }

        return CollisionDepth(overlapAmount, axis, verticalEscape)
    }

    fun boxBoxOverlapApproximate(a: Box, b: Box) : Boolean {
        val distance = Vector3f.distanceSquared(a.getCenter(), b.getCenter())
        return distance <= a.getRadiusSq() + b.getRadiusSq()
    }

    fun boxBoxOverlap(a: Box, b: Box): Boolean {
        if (!boxBoxOverlapApproximate(a, b)) { return false }

        if (a is AxisAlignedBoundingBox && b is AxisAlignedBoundingBox) {
            return AxisAlignedBoundingBox.intersects(a, b)
        }

        return boxBoxIntersection(a, b) != null
    }

    fun boxBoxIntersection(a: Box, b: Box): CollisionDepth? {
        if (!boxBoxOverlapApproximate(a, b)) { return null }

        // For SAT, prove there's overlap against all 15 axis
        val minDepth = CollisionDepth()
        val aAxes = a.getAxes()
        val bAxes = b.getAxes()

        for (i in 0 until 3) {
            boxBoxAxisCheck(a, b, aAxes[i])?.applyIfSmaller(minDepth) ?: return null
            boxBoxAxisCheck(a, b, bAxes[i])?.applyIfSmaller(minDepth) ?: return null
        }

        for (i in 0 until 3) {
            for (j in 0 until 3) {
                val axis = aAxes[i].cross(bAxes[j])
                if (axis.magnitudeSquare() < 0.0001f) { continue }
                boxBoxAxisCheck(a, b, axis.normalizeInPlace())?.applyIfSmaller(minDepth) ?: return null
            }
        }

        return minDepth
    }

    private fun boxBoxAxisCheck(a: Box, b: Box, axis: Vector3f) : CollisionDepth? {
        val aProjection = projectVertices(a.getVertices(), axis)
        val bProjection = projectVertices(b.getVertices(), axis)
        val overlapAmount = aProjection.overlapAmount(bProjection) ?: return null

        val verticalProjection = axis.dot(Vector3f.NegY)
        val verticalEscape = if (abs(verticalProjection) < 0.0001) { Float.POSITIVE_INFINITY } else { overlapAmount / verticalProjection }

        return CollisionDepth(overlapAmount, axis, verticalEscape)
    }

    private fun projectVertices(vertices: List<Vector3f>, axis: Vector3f) : ProjectionRange {
        var min = Float.POSITIVE_INFINITY
        var max = Float.NEGATIVE_INFINITY

        for (v in vertices) {
            val projection = v.dot(axis)
            min = min(min, projection)
            max = max(max, projection)
        }

        return ProjectionRange(min, max)
    }

    private fun projectVertices(vertices: Array<Vector3f>, axis: Vector3f) : ProjectionRange {
        var min = Float.POSITIVE_INFINITY
        var max = Float.NEGATIVE_INFINITY

        for (v in vertices) {
            val projection = v.dot(axis)
            min = min(min, projection)
            max = max(max, projection)
        }

        return ProjectionRange(min, max)
    }
}

object RayTriangleCollider {

    fun intersect(tri: Triangle, origin: Vector3f, direction: Vector3f, bidirectional: Boolean = false, rayLength: Float? = null): Pair<Vector3f, Float>? {
        val (q, rayDistance) = RayPlaneCollider.intersect(
            planeNormal = tri.normal,
            planePoint = tri.t0,
            origin = origin,
            direction = direction,
            bidirectional = bidirectional,
            rayLength = rayLength
        ) ?: return null

        if ((tri.t0 - tri.t1).cross(q - tri.t0).dot(tri.normal) < -1e-6f) { return null }
        if ((tri.t1 - tri.t2).cross(q - tri.t1).dot(tri.normal) < -1e-6f) { return null }
        if ((tri.t2 - tri.t0).cross(q - tri.t2).dot(tri.normal) < -1e-6f) { return null }

        return Pair(q, rayDistance)
    }

}

object RaySphereCollider {

    fun intersect(ray: Ray, sphere: Sphere): Boolean {
        val dOrigin = Vector3f.distanceSquared(ray.origin, sphere.center)
        if (dOrigin < sphere.radiusSq) { return true }

        val dC = sphere.center - ray.origin
        val proj = ray.direction.dot(dC)

        if (proj < 0) { return false }

        val rayPoint = ray.origin + ray.direction * proj
        val distance = Vector3f.distanceSquared(rayPoint, sphere.center)

        return distance <= sphere.radiusSq
    }

}

object RayBoxCollider {

    fun checkIntersect(ray: Ray, box: Box): Boolean {
        return intersect(ray, box) != null
    }

    fun intersect(ray: Ray, box: Box): Pair<Vector3f, Float>? {
        val boundingSphere = Sphere(center = box.getCenter(), radius = sqrt(box.getRadiusSq()))
        if (!RaySphereCollider.intersect(ray, boundingSphere)) { return null }

        return box.getPlanes().mapNotNull { boxPlaneRayIntersection(it, ray) }.minByOrNull { it.second }
    }

    private fun boxPlaneRayIntersection(boxPlane: BoxPlane, ray: Ray): Pair<Vector3f, Float>? {
        val intersection = RayPlaneCollider.intersect(
            planeNormal = boxPlane.normal,
            planePoint = boxPlane.p0,
            origin = ray.origin,
            direction = ray.direction,
            bidirectional = false,
        ) ?: return null

        if (!barycentricTest(boxPlane.p0, boxPlane.p1, boxPlane.p2, intersection.first)) { return null }
        if (!barycentricTest(boxPlane.p3, boxPlane.p1, boxPlane.p2, intersection.first)) { return null }

        return intersection
    }

    private fun barycentricTest(a: Vector3f, b: Vector3f, c: Vector3f, p: Vector3f): Boolean {
        val dP = p - a
        val d1 = b - a
        val d2 = c - a

        val check1 = dP.dot(d1) / d1.dot(d1)
        if (check1 < 0f || check1 > 1f) { return false }

        val check2 = dP.dot(d2) / d2.dot(d2)
        if (check2 < 0f || check2 > 1f) { return false }

        return true
    }

}

object RayPlaneCollider {

    fun intersect(planeNormal: Vector3f, planePoint: Vector3f, origin: Vector3f, direction: Vector3f, bidirectional: Boolean = false, rayLength: Float? = null): Pair<Vector3f, Float>? {
        val d = planeNormal.dot(direction)
        if (abs(d) <= 1e-5) { return null }

        val planeConstant = planeNormal.dot(planePoint)
        val rayDistance = (planeConstant - planeNormal.dot(origin)) / d

        if (!bidirectional && rayDistance < 0f) { return null }
        if (rayLength != null && rayDistance > rayLength) { return null }

        val q = origin + direction * rayDistance

        return Pair(q, rayDistance)
    }

}

object TriangleSphereCollider {

    fun intersect(triangle: Triangle, sphere: Sphere): CollisionDepth? {
        val minDepth = CollisionDepth()

        val normalIntersection = RayTriangleCollider.intersect(tri = triangle, origin = sphere.center, direction = triangle.normal * -1f, bidirectional = true, rayLength = sphere.radius)

        if (normalIntersection != null) {
            resolvePointCollision(normalIntersection.first, sphere)?.applyIfSmaller(minDepth)
        } else {
            val closestPointOnEdge0 = closestPointOnEdge(triangle.t0, triangle.t1, sphere)
            resolvePointCollision(closestPointOnEdge0, sphere)?.applyIfSmaller(minDepth)

            val closestPointOnEdge1 = closestPointOnEdge(triangle.t1, triangle.t2, sphere)
            resolvePointCollision(closestPointOnEdge1, sphere)?.applyIfSmaller(minDepth)

            val closestPointOnEdge2 = closestPointOnEdge(triangle.t2, triangle.t0, sphere)
            resolvePointCollision(closestPointOnEdge2, sphere)?.applyIfSmaller(minDepth)
        }

        return if (minDepth.amount.isInfinite()) { null } else { minDepth }
    }

    private fun closestPointOnEdge(edgeStart: Vector3f, edgeEnd: Vector3f, sphere: Sphere): Vector3f {
        val edgeVector = edgeEnd - edgeStart
        val t  = edgeVector.dot(sphere.center - edgeStart) / edgeVector.dot(edgeVector)
        return edgeStart + (edgeVector * t.coerceIn(0f, 1f))
    }

    private fun resolvePointCollision(point: Vector3f, sphere: Sphere): CollisionDepth? {
        // Penetration-normal escape
        val sphereToPointVector = sphere.center - point
        val sphereToPointDistance = sphereToPointVector.magnitude()
        if (sphereToPointDistance > sphere.radius) { return null }
        val amount = sphere.radius - sphereToPointDistance

        // Vertical escape
        val horizontal = sphere.center - point.withY(sphere.center.y)
        val verticalLength = sqrt(sphere.radiusSq - horizontal.magnitudeSquare())
        val verticalPoint = sphere.center.y + verticalLength
        val verticalEscape = verticalPoint - point.y

        return CollisionDepth(amount = amount, axis = sphereToPointVector.normalize(), verticalEscape = verticalEscape)
    }

}

object RayGridCollider {

    private class GridNode(val coordinate: Pair<Int, Int>, val stepDistance: Int)

    fun collideWithTerrain(ray: Ray, maxSteps: Int, ignoreHitWalls: Boolean = true): RayCollision? {
        val areas = SceneManager.getCurrentScene().getAreas()
        var nearestAreaCollision: RayCollision? = null

        for (area in areas) {
            val areaCollision = collideWithGrid(area, ray, maxSteps, ignoreHitWalls) ?: continue
            if (nearestAreaCollision == null || nearestAreaCollision.distance > areaCollision.distance) { nearestAreaCollision = areaCollision }
        }

        return nearestAreaCollision
    }

    private fun collideWithGrid(area: Area, ray: Ray, maxSteps: Int, ignoreHitWalls: Boolean): RayCollision? {
        val collisionMap = area.getZoneResource().zoneCollisionMap ?: return null

        val horizontalRay = ray.direction.withY(0f)
        if (horizontalRay.magnitude() > 1e-5f) { horizontalRay.normalizeInPlace() } else { horizontalRay.copyFrom(0f) }

        val signX = sign(horizontalRay.x).roundToInt()
        val signZ = sign(horizontalRay.z).roundToInt()

        val dequeue = ArrayDeque<GridNode>()
        dequeue += GridNode(collisionMap.positionToBlock(ray.origin), stepDistance = 0)

        val checked = HashSet<Pair<Int, Int>>()
        var nearest: RayCollision? = null

        while (dequeue.isNotEmpty()) {
            val next = dequeue.removeFirst()
            val (xBlockIndex, zBlockIndex) = next.coordinate
            val (lowerLeft, upperRight) = collisionMap.blockToBounds(xBlockIndex, zBlockIndex)

            if (!checkAxisAlignedGrid(ray.origin, horizontalRay, lowerLeft, upperRight)) { continue }

            // Some collision objects don't fit within their coordinate bounds, so it's necessary to check neighbors
            // (ex: walls in E. Adoulin near the mog house)
            val neighborList = toNeighborList(next.coordinate)
            for (coord in neighborList) {
                if (checked.contains(coord)) { continue }
                checked += coord

                val collisionObjectGroup = collisionMap.getCollisionObjects(coord.first, coord.second) ?: continue
                val collision = check(ray, collisionObjectGroup, ignoreHitWalls) ?: continue

                if (nearest == null || nearest.distance > collision.distance) { nearest = collision }
            }

            if (nearest != null) { break }

            if (next.stepDistance >= maxSteps - 1) { continue }
            if (signX != 0) { dequeue += GridNode(xBlockIndex + signX to zBlockIndex, next.stepDistance + 1) }
            if (signZ != 0) { dequeue += GridNode(xBlockIndex to zBlockIndex + signZ, next.stepDistance + 1) }
        }

        return nearest
    }

    private fun checkAxisAlignedGrid(origin: Vector3f, xzRayDir: Vector3f, lowerLeft: Vector3f, upperRight: Vector3f): Boolean {
        if (origin.x >= lowerLeft.x && origin.x <= upperRight.x && origin.z >= lowerLeft.z && origin.z <= upperRight.z) {
            return true
        }

        var intersections = 0

        if (abs(xzRayDir.x) > 1e-5f) {
            val tLeft = (lowerLeft.x - origin.x) / xzRayDir.x
            val zLeftIntersect = origin.z + xzRayDir.z * tLeft
            if (zLeftIntersect >= lowerLeft.z && zLeftIntersect <= upperRight.z) { intersections += 1 }

            val tRight = (upperRight.x - origin.x) / xzRayDir.x
            val zRightIntersect = origin.z + xzRayDir.z * tRight
            if (zRightIntersect >= lowerLeft.z && zRightIntersect <= upperRight.z) { intersections += 1 }
        }

        if (abs(xzRayDir.z) > 1e-5f) {
            val tBottom = (lowerLeft.z - origin.z) / xzRayDir.z
            val xBottomIntersect = origin.x + xzRayDir.x * tBottom
            if (xBottomIntersect >= lowerLeft.x && xBottomIntersect <= upperRight.x) { intersections += 1 }

            val tTop = (upperRight.z - origin.z) / xzRayDir.z
            val xTopIntersect = origin.x + xzRayDir.x * tTop
            if (xTopIntersect >= lowerLeft.x && xTopIntersect <= upperRight.x) { intersections += 1 }
        }

        return intersections >= 2
    }

    private fun check(ray: Ray, collisionObjectGroup: CollisionObjectGroup, ignoreHitWalls: Boolean): RayCollision? {
        return Collider.nearestObjectGroupCollision(ray, collisionObjectGroup, ignoreHitWalls)
    }

    private fun toNeighborList(coord: Pair<Int, Int>): List<Pair<Int, Int>> {
        val (x,z) = coord

        return listOf(
            coord,
            x + 1 to z,
            x - 1 to z,
            x to z + 1,
            x to z - 1,
        )
    }

}