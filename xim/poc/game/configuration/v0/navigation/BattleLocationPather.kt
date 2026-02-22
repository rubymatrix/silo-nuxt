package xim.poc.game.configuration.v0.navigation

import xim.math.Vector3f
import xim.poc.Area
import xim.poc.Collider
import xim.poc.SceneManager
import xim.poc.game.configuration.v0.zones.PathingSettings

data class GridPosQuery(val gridPos: GridPos, val basePosition: Vector3f)

object BattleLocationPather {

    fun generateNavigator(pathingSettings: PathingSettings): BattleLocationNavigator {
        val grid = HashMap<GridPos, Vector3f>()
        val rootPosition = Vector3f(pathingSettings.root)

        val areas = SceneManager.getCurrentScene().getAreas()

        val query = ArrayDeque<GridPosQuery>()
        query += GridPosQuery(GridPos(0, 0), rootPosition)

        val rejected = HashSet<GridPos>()

        while (query.isNotEmpty()) {
            val node = query.removeFirst()
            val gridPos = node.gridPos

            if (grid.containsKey(gridPos) || rejected.contains(gridPos)) { continue }

            val dY = node.basePosition.y - rootPosition.y
            val position = rootPosition + Vector3f(gridPos.x.toFloat(), dY, gridPos.z.toFloat())

            if (Vector3f.distance(position, pathingSettings.radialCenter) > pathingSettings.radius) {
                rejected += gridPos
                continue
            } else if (
                position.x < pathingSettings.xMin ||
                position.x > pathingSettings.xMax ||
                position.z < pathingSettings.zMin ||
                position.z > pathingSettings.zMax
            ) {
                rejected += gridPos
                continue
            }

            if (!adjustPosition(gridPos, position, areas)) {
                rejected += gridPos
                continue
            }

            grid[gridPos] = position
            gridPos.neighbors().forEach { query += GridPosQuery(it, Vector3f(position)) }
        }

        return BattleLocationNavigator(pathingSettings, grid, rootPosition)
    }

    private fun adjustPosition(gridPos: GridPos, position: Vector3f, areas: List<Area>): Boolean {
        var accumulatedAdjustment = 0f
        val copy = Vector3f(position)

        while (true) {
            val maxVerticalEscapeRequired = Collider.collideNavSphere(copy, 0.5f, areas)

            if (maxVerticalEscapeRequired == null) {
                if (accumulatedAdjustment > 0f) { break }

                val nearestFloor = SceneManager.getCurrentScene().getNearestFloorPosition(copy)
                return if (nearestFloor != null && Vector3f.distance(nearestFloor, copy) < 4f) {
                    position.copyFrom(nearestFloor)
                    true
                } else {
                    false
                }
            }

            accumulatedAdjustment += maxVerticalEscapeRequired
            if (accumulatedAdjustment > 0.5f) {
                return false
            }

            copy.y -= maxVerticalEscapeRequired
        }

        position.copyFrom(copy)
        return true
    }

}