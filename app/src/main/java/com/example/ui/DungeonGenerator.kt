package com.example.ui

import com.example.data.DungeonMapState
import com.example.data.TrapState
import kotlin.random.Random

object DungeonGenerator {
    const val MW = 70
    const val MH = 45

    // Tile type constants
    const val TL_VOID = 0
    const val TL_WALL = 1
    const val TL_FLOOR = 2
    const val TL_CORR = 3
    const val TL_DOOR = 4
    const val TL_STAIR = 5
    const val TL_WATER = 6
    const val TL_FOUNTAIN = 7
    const val TL_SHRINE = 8
    const val TL_CHEST = 9
    const val TL_MERCHANT = 10

    data class Room(val x: Int, val y: Int, val w: Int, val h: Int) {
        val cx: Int = x + w / 2
        val cy: Int = y + h / 2

        fun intersects(other: Room): Boolean {
            return (x < other.x + other.w + 1 && x + w + 1 > other.x &&
                    y < other.y + other.h + 1 && y + h + 1 > other.y)
        }
    }

    fun genDungeon(floor: Int): DungeonData {
        val grid = Array(MH) { IntArray(MW) { TL_WALL } }
        val rooms = mutableListOf<Room>()
        var attempts = 0
        val rc = Random.nextInt(8, 15) + (floor / 3)

        while (rooms.size < rc && attempts < 300) {
            attempts++
            val w = Random.nextInt(5, 13)
            val h = Random.nextInt(4, 10)
            val x = Random.nextInt(1, MW - w - 1)
            val y = Random.nextInt(1, MH - h - 1)
            val newRoom = Room(x, y, w, h)

            var overlap = false
            for (r in rooms) {
                if (newRoom.intersects(r)) {
                    overlap = true
                    break
                }
            }

            if (!overlap) {
                rooms.add(newRoom)
                // Carve room
                for (ry in y until y + h) {
                    for (rx in x until x + w) {
                        grid[ry][rx] = TL_FLOOR
                    }
                }
            }
        }

        // Carve corridors between sequential rooms
        for (i in 1 until rooms.size) {
            val a = rooms[i - 1]
            val b = rooms[i]
            carve(grid, a.cx, a.cy, b.cx, b.cy)
        }

        // Add some random cross-loop corridors for variety
        val loops = rooms.size / 3
        for (i in 0 until loops) {
            val a = rooms.random()
            val b = rooms.random()
            if (a != b) {
                carve(grid, a.cx, a.cy, b.cx, b.cy)
            }
        }

        // Stairs in the final room
        val lr = rooms.last()
        grid[lr.cy][lr.cx] = TL_STAIR

        // Water elements
        val waterCount = Random.nextInt(3, 9)
        for (i in 0 until waterCount) {
            val rx = Random.nextInt(1, MW - 1)
            val ry = Random.nextInt(1, MH - 1)
            if (grid[ry][rx] == TL_FLOOR) {
                grid[ry][rx] = TL_WATER
            }
        }

        // Fountains (occasionally)
        val fountainChance = if (Random.nextFloat() < 0.4f) 1 else 0
        val eligibleRooms = rooms.subList(1, rooms.size - 1)
        if (fountainChance > 0 && eligibleRooms.isNotEmpty()) {
            val rm = eligibleRooms.random()
            val fx = Random.nextInt(rm.x + 1, rm.x + rm.w - 1)
            val fy = Random.nextInt(rm.y + 1, rm.y + rm.h - 1)
            if (grid[fy][fx] == TL_FLOOR) {
                grid[fy][fx] = TL_FOUNTAIN
            }
        }

        // Shrines (12% + floor * 1% chance)
        val shrineChance = 0.12f + (floor * 0.01f)
        if (Random.nextFloat() < shrineChance && eligibleRooms.isNotEmpty()) {
            val rm = eligibleRooms.random()
            val sx = Random.nextInt(rm.x + 1, rm.x + rm.w - 1)
            val sy = Random.nextInt(rm.y + 1, rm.y + rm.h - 1)
            if (grid[sy][sx] == TL_FLOOR) {
                grid[sy][sx] = TL_SHRINE
            }
        }

        // Chests (1 to 2 per floor)
        val chestCount = Random.nextInt(1, 3)
        for (i in 0 until chestCount) {
            if (eligibleRooms.isNotEmpty()) {
                val rm = eligibleRooms.random()
                val cx = Random.nextInt(rm.x + 1, rm.x + rm.w - 1)
                val cy = Random.nextInt(rm.y + 1, rm.y + rm.h - 1)
                if (grid[cy][cx] == TL_FLOOR) {
                    grid[cy][cx] = TL_CHEST
                }
            }
        }

        // Wandering Merchants (25% + floor * 0.8% chance)
        val merchantChance = 0.25f + (floor * 0.008f)
        if (Random.nextFloat() < merchantChance && eligibleRooms.isNotEmpty()) {
            val rm = eligibleRooms.random()
            val mx = Random.nextInt(rm.x + 1, rm.x + rm.w - 1)
            val my = Random.nextInt(rm.y + 1, rm.y + rm.h - 1)
            if (grid[my][mx] == TL_FLOOR) {
                grid[my][mx] = TL_MERCHANT
            }
        }

        // Generate traps
        val trapCount = Random.nextInt(1, 3) + (floor / 4)
        val trapsList = mutableListOf<TrapState>()
        val forceMultiplier = 1f + (floor - 1) * 0.15f
        for (i in 0 until trapCount) {
            if (rooms.isNotEmpty()) {
                val rm = rooms.random()
                val tx = Random.nextInt(rm.x + 1, rm.x + rm.w - 1)
                val ty = Random.nextInt(rm.y + 1, rm.y + rm.h - 1)
                if (grid[ty][tx] == TL_FLOOR) {
                    val trapType = chooseTrapType(floor)
                    val dmgVal = (trapType.baseDmg * forceMultiplier).toInt()
                    trapsList.add(
                        TrapState(
                            name = trapType.name,
                            dmg = dmgVal,
                            color = trapType.color,
                            ds = trapType.ds,
                            ef = trapType.ef,
                            dur = trapType.dur,
                            x = tx,
                            y = ty,
                            triggered = false,
                            hidden = Random.nextFloat() < 0.35f,
                            playerTrap = false
                        )
                    )
                }
            }
        }

        // Explored grid starting as false
        val explored = List(MH) { List(MW) { false } }
        val nestedGrid = grid.map { row -> row.toList() }

        val mapState = DungeonMapState(
            floor = floor,
            width = MW,
            height = MH,
            grid = nestedGrid,
            exploredGrid = explored,
            visibleGrid = explored,
            stairX = lr.cx,
            stairY = lr.cy
        )

        val startingRoom = rooms[0]
        return DungeonData(mapState, rooms, startingRoom, trapsList)
    }

    private fun carve(grid: Array<IntArray>, x1: Int, y1: Int, x2: Int, y2: Int) {
        var x = x1
        var y = y1
        while (x != x2) {
            if (grid[y][x] == TL_WALL) grid[y][x] = TL_CORR
            x += if (x < x2) 1 else -1
        }
        while (y != y2) {
            if (grid[y][x] == TL_WALL) grid[y][x] = TL_CORR
            y += if (y < y2) 1 else -1
        }
    }

    private class LocalTrapType(val name: String, val baseDmg: Int, val color: String, val ds: String, val ef: String = "", val dur: Int = 0)

    private fun chooseTrapType(floor: Int): LocalTrapType {
        val types = listOf(
            LocalTrapType("Spike Trap", 8, "#a0522d", "Spikes shoot from the floor!"),
            LocalTrapType("Fire Trap", 12, "#ff4500", "Flames erupt!"),
            LocalTrapType("Poison Trap", 5, "#32cd32", "Noxious gas!", "poison_dot", 5),
            LocalTrapType("Teleport Trap", 0, "#9b5de5", "The world shifts!", "teleport")
        )
        val maxIndex = Math.min(types.size - 1, floor / 3)
        return types[Random.nextInt(0, maxIndex + 1)]
    }

    fun computeFOV(grid: List<List<Int>>, px: Int, py: Int, r: Int): List<List<Boolean>> {
        val fov = MutableList(MH) { MutableList(MW) { false } }
        if (py in 0 until MH && px in 0 until MW) {
            fov[py][px] = true
        }
        val stepCount = r * 2
        for (i in 0 until 360 step 3) {
            val rad = Math.toRadians(i.toDouble())
            val dx = Math.cos(rad)
            val dy = Math.sin(rad)
            var cx = px.toFloat()
            var cy = py.toFloat()
            for (step in 1..stepCount) {
                cx += dx.toFloat()
                cy += dy.toFloat()
                val ix = Math.round(cx)
                val iy = Math.round(cy)
                if (iy !in 0 until MH || ix !in 0 until MW) break
                fov[iy][ix] = true
                val tile = grid[iy][ix]
                if (tile == TL_WALL) {
                    break
                }
            }
        }
        return fov
    }
}

data class DungeonData(
    val mapState: DungeonMapState,
    val rooms: List<DungeonGenerator.Room>,
    val startRoom: DungeonGenerator.Room,
    val traps: List<TrapState>
)
