package xim.poc.game.configuration.v0.navigation

import xim.math.Vector3f
import xim.poc.ActorId
import xim.poc.game.ActorState
import xim.poc.game.ActorStateManager
import xim.poc.game.configuration.v0.zones.PathingSettings
import xim.poc.gl.ByteColor
import xim.poc.tools.SphereDrawingTool
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

data class GridPath(var node: GridPos, var parent: GridPath?)

data class GridPos(val x: Int, val z: Int) {

    fun neighbors() = listOf(
        GridPos(x+1,z),
        GridPos(x-1,z),
        GridPos(x,z+1),
        GridPos(x,z-1),
    )

    fun neighborsAndDiagonals() = listOf(
        GridPos(x+1,z),
        GridPos(x-1,z),
        GridPos(x,z+1),
        GridPos(x,z-1),

        GridPos(x+1,z+1),
        GridPos(x+1,z-1),
        GridPos(x-1,z+1),
        GridPos(x-1,z-1),
    )

}

private data class MemoizedResult(val value: Vector3f?) {
    var age = 0f

    fun isStale(): Boolean {
        return age > 5f
    }

}

class BattleLocationNavigator(val pathingSettings: PathingSettings, private val grid: Map<GridPos, Vector3f>, private val gridRoot: Vector3f) {

    private val memoization = HashMap<ActorId, MemoizedResult>()
    private val nearestPoint = HashMap<GridPos, GridPos>()

    private val trimmedGrid by lazy { makeTrimmedGrid() }

    fun update(elapsedFrames: Float) {
        memoization.forEach { it.value.age += elapsedFrames }
        memoization.entries.removeAll { it.value.isStale() }
    }

    fun getNextPosition(actorState: ActorState, desiredEnd: Vector3f): Vector3f? {
        return memoization.getOrPut(actorState.id) {
            MemoizedResult(getNextPositionInternal(actorState, desiredEnd))
        }.value
    }

    fun nearestGridPoint(point: Vector3f): Vector3f {
        return grid.values.minBy { Vector3f.distanceSquared(point, it) }
    }

    fun draw(target: Vector3f? = null) {
        val playerPos = ActorStateManager.player().position

        val startNode = toGrid(playerPos)
        val endNode = toGrid(target ?: gridRoot)

        val nodes = bsf(startNode, endNode)

        nodes.forEach {
            val copy = Vector3f(grid[it]!!)
            copy.y -= 0.5f
            SphereDrawingTool.drawSphere(copy, 0.5f, ByteColor.opaqueB)
        }
    }

    fun drawGrid() {
        trimmedGrid.values.forEach {
            val copy = Vector3f(it)
            copy.y -= 0.5f
            SphereDrawingTool.drawSphere(copy, 0.5f, ByteColor.half)
        }
    }

    fun printGrid() {
        val strBuilder = StringBuilder()

        for (x in -50 ..50) {
            for (z in -50 .. 50) {
                val c = if (grid.containsKey(GridPos(x,z))) { "O" } else { " " }
                strBuilder.append(c)
            }
            strBuilder.appendLine()
        }

        println(strBuilder)
    }

    private fun getNextPositionInternal(actorState: ActorState, desiredEnd: Vector3f): Vector3f? {
        val startNode = toGrid(actorState.position)
        val endNode = toGrid(desiredEnd)

        val isDirect = direct(startNode, endNode)
        if (isDirect) { return desiredEnd }

        val path = bsf(startNode, endNode)
        if (path.isEmpty()) { return null }

        val n = if (path.size == 1) { path.first() } else { path[1] }
        return grid[n]
    }

    private fun direct(startNode: GridPos, endNode: GridPos): Boolean {
        val dX = endNode.x - startNode.x
        val dZ = endNode.z - startNode.z
        if (dX == 0 && dZ == 0) { return true }

        val vector = Vector3f(dX.toFloat(), 0f, dZ.toFloat()).normalizeInPlace()

        var cX = startNode.x.toFloat()
        var cZ = startNode.z.toFloat()

        while (true) {
            cX += vector.x
            cZ += vector.z

            val positions = setOf(
                GridPos(floor(cX).roundToInt(), floor(cZ).roundToInt()),
                GridPos(floor(cX).roundToInt(), ceil(cZ).roundToInt()),
                GridPos(ceil(cX).roundToInt(), floor(cZ).roundToInt()),
                GridPos(ceil(cX).roundToInt(), ceil(cZ).roundToInt()),
            )

            if (positions.any { !grid.containsKey(it) }) { return false }
            if (positions.any { it == endNode }) { return true }
        }
    }

    private fun bsf(startNode: GridPos, endNode: GridPos): List<GridPos> {
        val checked = HashSet<GridPos>()

        val query = ArrayDeque<GridPath>()
        query += GridPath(node = startNode, parent = null)

        var chosenPath: GridPath? = null

        while (query.isNotEmpty()) {
            val current = query.removeFirst()

            val tail = current.node
            if (checked.contains(tail)) { continue }

            if (tail == endNode) {
                chosenPath = current
                break
            }

            checked += tail

            val neighbors = tail.neighborsAndDiagonals().filter { !checked.contains(it) }.filter { grid.containsKey(it) }
            for (neighbor in neighbors) { query += GridPath(node = neighbor, parent = current)
            }
        }

        val answer = ArrayDeque<GridPos>()
        while (chosenPath != null) {
            answer.addFirst(chosenPath.node)
            chosenPath = chosenPath.parent
        }

        return answer.toList()
    }

    private fun toGrid(position: Vector3f): GridPos {
        val gX = (position.x - gridRoot.x).roundToInt()
        val gZ = (position.z - gridRoot.z).roundToInt()

        val gridPos = GridPos(gX, gZ)
        if (grid.containsKey(gridPos)) { return gridPos }

        return nearestPoint.getOrPut(gridPos) {
            val nearby = nearestNeighbor(gridPos)
            if (nearby != null) { return@getOrPut nearby }
            grid.entries.minBy { Vector3f.distanceSquared(position, it.value) }.key
        }
    }

    private fun nearestNeighbor(origin: GridPos): GridPos? {
        val neighbors = origin.neighborsAndDiagonals()

        for (neighbor in neighbors) {
            if (grid.containsKey(neighbor)) { return neighbor }
        }

        return null
    }

    private fun makeTrimmedGrid(): HashMap<GridPos, Vector3f> {
        val toRemove = grid.keys.filter {
            it.neighborsAndDiagonals().all { n -> grid.containsKey(n) }
        }.toSet()

        val copy = HashMap(grid)
        copy.keys.removeAll(toRemove)

        return copy
    }

}