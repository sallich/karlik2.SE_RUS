package ru.course.roguelike.agent.llm

import ru.course.roguelike.shared.dto.GameSnapshot
import ru.course.roguelike.shared.model.MobKind
import java.util.Locale

object MapRenderer {

    private fun getMobChar(
        x: Int,
        y: Int,
        snapshot: GameSnapshot,
    ): Char? =
        snapshot.mobs
            .firstOrNull { it.x.toInt() == x && it.y.toInt() == y }
            ?.let { mob ->
                when (mob.kind) {
                    MobKind.MELEE -> 'M'
                    MobKind.RANGED -> 'R'
                    MobKind.LLM_GUARD -> 'G'
                    else -> '?'
                }
            }

    private fun getLocalCellChar(
        x: Int,
        y: Int,
        playerX: Int,
        playerY: Int,
        snapshot: GameSnapshot,
    ): Char {
        if (x !in 0 until snapshot.width || y !in 0 until snapshot.height) return '#'
        return getSpecialCellChar(
            x,
            y,
            playerX,
            playerY,
            snapshot
        )
            ?: getTileChar(
                x,
                y,
                snapshot
            )
    }

    private fun getSpecialCellChar(
        x: Int,
        y: Int,
        playerX: Int,
        playerY: Int,
        snapshot: GameSnapshot,
    ): Char? =
        when {
            x == playerX && y == playerY -> '@'
            else ->
                getMobChar(
                    x,
                    y,
                    snapshot
                )
                    ?: if (snapshot.keyPickups.any { it.x.toInt() == x && it.y.toInt() == y }) {
                        'K'
                    } else if (snapshot.exitGate?.x == x && snapshot.exitGate?.y == y) {
                        'E'
                    } else {
                        null
                    }
        }

    private fun getTileChar(
        x: Int,
        y: Int,
        snapshot: GameSnapshot,
    ): Char {
        val tile = snapshot.tiles[y * snapshot.width + x]
        return when {
            tile.damaging -> 'L'
            tile.walkable -> '.'
            else -> '#'
        }
    }

    fun getLocalMap(
        snapshot: GameSnapshot,
        radius: Int = 4,
    ): String {
        val playerX = (snapshot.agent?.pose?.x ?: snapshot.player.pose.x).toInt()
        val playerY = (snapshot.agent?.pose?.y ?: snapshot.player.pose.y).toInt()
        val sb = StringBuilder()
        sb.append("Локальная карта ${radius * 2 + 1}x${radius * 2 + 1} (центр = игрок @):\n")
        for (dy in -radius..radius) {
            sb.append("  ")
            for (dx in -radius..radius) {
                val x = playerX + dx
                val y = playerY + dy
                val ch = getLocalCellChar(
                    x,
                    y,
                    playerX,
                    playerY,
                    snapshot
                )
                sb.append(ch).append(' ')
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun getCellChar(
        x: Int,
        y: Int,
        snapshot: GameSnapshot,
        playerX: Int,
        playerY: Int,
    ): Char {
        // Игрок
        if (x == playerX && y == playerY) return '@'
        // Ключ
        if (snapshot.keyPickups.any { it.x.toInt() == x && it.y.toInt() == y }) return 'K'
        // Выход
        if (snapshot.exitGate?.x == x && snapshot.exitGate?.y == y) return 'E'

        val tileType = snapshot.tiles[y * snapshot.width + x]
        return when {
            tileType.damaging -> 'L'
            tileType.walkable -> '.'
            else -> '#'
        }
    }

    fun formatFullMap(snapshot: GameSnapshot): String {
        val w = snapshot.width
        val h = snapshot.height
        val playerX = snapshot.agent?.pose?.x ?: snapshot.player.pose.x
        val playerY = snapshot.agent?.pose?.y ?: snapshot.player.pose.y

        val grid =
            Array(h) { y ->
                CharArray(w) { x ->
                    getCellChar(
                        x,
                        y,
                        snapshot,
                        playerX.toInt(),
                        playerY.toInt()
                    )
                }
            }
        val rotated = rotateRight(grid)
        val sb = StringBuilder()
        sb.append("     ")
        for (x in rotated[0].indices) sb.append(x % 10)
        sb.append("\n     ")
        repeat(rotated[0].size) { sb.append('-') }
        sb.append('\n')
        for (y in rotated.indices) {
            sb.append(
                String.format(
                    Locale.US,
                    "%3d |",
                    rotated.size - 1 - y
                )
            )
            sb.append(rotated[y])
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun rotateRight(grid: Array<CharArray>): Array<CharArray> {
        val h = grid.size
        val w = grid[0].size
        val rotated = Array(w) { CharArray(h) }
        for (y in 0 until h) {
            for (x in 0 until w) {
                rotated[w - 1 - x][y] = grid[y][x]
            }
        }
        return rotated
    }
}
