/**
 * Zone collision raycasting for ground placement.
 *
 * Simplified port of xim/poc/Collider.kt nearestFloor() and
 * RayTriangleCollider/RayPlaneCollider intersection logic.
 */

import type { Vector3 } from './byteReader'
import type {
  CollisionMap,
  CollisionObject,
  CollisionObjectGroup,
  CollisionTriangle,
  Matrix4x4,
} from './zoneResource'

export interface RaycastHit {
  readonly position: Vector3
  readonly distance: number
}

/**
 * Cast a ray downward from the given XZ position to find the ground.
 * FFXI uses Y-positive as down, so the ray direction is (0, 1, 0).
 * We cast from a high Y (negative = up in FFXI) to find the first floor hit.
 */
export function findGroundY(
  collisionMap: CollisionMap,
  x: number,
  z: number,
  startY = -200,
): number | null {
  const origin: Vector3 = { x, y: startY, z }
  const direction: Vector3 = { x: 0, y: 1, z: 0 } // down in FFXI

  const groups = getCollisionGroupsAtPosition(collisionMap, origin)
  let nearest: RaycastHit | null = null

  for (const group of groups) {
    for (const obj of group.collisionObjects) {
      const hit = raycastCollisionObject(origin, direction, obj)
      if (hit && (!nearest || hit.distance < nearest.distance)) {
        nearest = hit
      }
    }
  }

  return nearest?.position.y ?? null
}

/**
 * Get collision object groups near a world position.
 */
function getCollisionGroupsAtPosition(
  collisionMap: CollisionMap,
  position: Vector3,
  maxSteps = 2,
): CollisionObjectGroup[] {
  const { xBlock, zBlock } = positionToBlock(collisionMap, position)
  const results: CollisionObjectGroup[] = []

  for (let dx = -maxSteps; dx <= maxSteps; dx++) {
    for (let dz = -maxSteps; dz <= maxSteps; dz++) {
      const bx = xBlock + dx
      const bz = zBlock + dz

      if (bx < 0 || bx >= collisionMap.numBlocksWide * collisionMap.subBlocksX) continue
      if (bz < 0 || bz >= collisionMap.numBlocksLong * collisionMap.subBlocksZ) continue

      const group = collisionMap.entries[bz]?.[bx]
      if (group) results.push(group)
    }
  }

  return results
}

function positionToBlock(map: CollisionMap, position: Vector3): { xBlock: number, zBlock: number } {
  const halfMapWidth = map.blockWidth * map.numBlocksWide / 2
  const positiveX = position.x + halfMapWidth
  const xBlock = Math.floor(positiveX / (map.blockWidth / map.subBlocksX))

  const halfMapLength = map.blockLength * map.numBlocksLong / 2
  const positiveZ = position.z + halfMapLength
  const zBlock = Math.floor(positiveZ / (map.blockLength / map.subBlocksZ))

  return { xBlock, zBlock }
}

/**
 * Raycast against a collision object, transforming to collision space.
 */
function raycastCollisionObject(
  origin: Vector3,
  direction: Vector3,
  obj: CollisionObject,
): RaycastHit | null {
  // Transform ray to collision space
  const csOrigin = transformPoint(obj.transformInfo.toCollisionSpace, origin)
  const csDir = transformDirection(obj.transformInfo.toCollisionSpace, direction)

  let nearestHit: { point: Vector3, distance: number } | null = null

  for (const tri of obj.collisionMesh.triangles) {
    if (tri.hitWall) continue

    const hit = rayTriangleIntersect(tri, csOrigin, csDir)
    if (!hit) continue

    if (!nearestHit || hit.distance < nearestHit.distance) {
      // Transform hit point back to world space
      const worldPoint = transformPoint(obj.transformInfo.toWorldSpace, hit.point)
      nearestHit = { point: worldPoint, distance: hit.distance }
    }
  }

  if (!nearestHit) return null

  return {
    position: nearestHit.point,
    distance: nearestHit.distance,
  }
}

/**
 * Ray-triangle intersection using Möller-Trumbore-like approach.
 * Matches the Kotlin RayTriangleCollider logic.
 */
function rayTriangleIntersect(
  tri: CollisionTriangle,
  origin: Vector3,
  direction: Vector3,
): { point: Vector3, distance: number } | null {
  // First, ray-plane intersection
  const d = dot(tri.normal, direction)
  if (Math.abs(d) <= 1e-5) return null

  const planeConstant = dot(tri.normal, tri.p0)
  const rayDistance = (planeConstant - dot(tri.normal, origin)) / d

  if (rayDistance < 0) return null

  // Intersection point
  const q: Vector3 = {
    x: origin.x + direction.x * rayDistance,
    y: origin.y + direction.y * rayDistance,
    z: origin.z + direction.z * rayDistance,
  }

  // Inside-triangle test (barycentric via cross products)
  // Edge directions match Kotlin: (t0-t1), (t1-t2), (t2-t0)
  const edge0 = sub(tri.p0, tri.p1)
  const vp0 = sub(q, tri.p0)
  if (dot(cross(edge0, vp0), tri.normal) < -1e-6) return null

  const edge1 = sub(tri.p1, tri.p2)
  const vp1 = sub(q, tri.p1)
  if (dot(cross(edge1, vp1), tri.normal) < -1e-6) return null

  const edge2 = sub(tri.p2, tri.p0)
  const vp2 = sub(q, tri.p2)
  if (dot(cross(edge2, vp2), tri.normal) < -1e-6) return null

  return { point: q, distance: rayDistance }
}

// ─── Math helpers ────────────────────────────────────────────────────────────

function dot(a: Vector3, b: Vector3): number {
  return a.x * b.x + a.y * b.y + a.z * b.z
}

function sub(a: Vector3, b: Vector3): Vector3 {
  return { x: a.x - b.x, y: a.y - b.y, z: a.z - b.z }
}

function cross(a: Vector3, b: Vector3): Vector3 {
  return {
    x: a.y * b.z - a.z * b.y,
    y: a.z * b.x - a.x * b.z,
    z: a.x * b.y - a.y * b.x,
  }
}

/**
 * Transform a point by a 4x4 matrix (column-major, matching FFXI layout).
 */
function transformPoint(mat: Matrix4x4, p: Vector3): Vector3 {
  const m = mat.m
  return {
    x: m[0]! * p.x + m[4]! * p.y + m[8]! * p.z + m[12]!,
    y: m[1]! * p.x + m[5]! * p.y + m[9]! * p.z + m[13]!,
    z: m[2]! * p.x + m[6]! * p.y + m[10]! * p.z + m[14]!,
  }
}

/**
 * Transform a direction vector by a 4x4 matrix (no translation).
 */
function transformDirection(mat: Matrix4x4, d: Vector3): Vector3 {
  const m = mat.m
  return {
    x: m[0]! * d.x + m[4]! * d.y + m[8]! * d.z,
    y: m[1]! * d.x + m[5]! * d.y + m[9]! * d.z,
    z: m[2]! * d.x + m[6]! * d.y + m[10]! * d.z,
  }
}
