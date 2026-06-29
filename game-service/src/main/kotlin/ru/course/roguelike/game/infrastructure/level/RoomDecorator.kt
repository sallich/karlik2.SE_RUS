package ru.course.roguelike.game.infrastructure.level

import ru.course.roguelike.game.domain.level.Room
import ru.course.roguelike.shared.model.GridPos
import ru.course.roguelike.shared.model.TileType
import kotlin.random.Random

/**
 * Декорирование комнат лабиринта (issue #3): колонны и сегменты лавы.
 *
 * Объекты расставляются «бакетами»: внутренняя область комнаты (с отступом в
 * один тайл от стен) делится на бакеты [BUCKET]x[BUCKET], и в каждом бакете
 * правый столбец и нижняя строка всегда остаются полом — это гарантированные
 * проходы-дорожки. Вместе с чистым кольцом по периметру комнаты и нетронутым
 * центром это сохраняет устройство комнаты и не даёт сделать её непроходимой.
 * Финальная страховка — проверка проходимости в генераторе ([MapConnectivity]).
 *
 * - Колонны (COLUMN): кластеры-препятствия, вокруг которых надо маневрировать.
 * - Лава (LAVA): сплошные сегменты, проходимые, но наносящие урон.
 * - Комната-босс: реже и только колонны (арена), без лавы.
 */
object RoomDecorator {
    /** Сторона бакета в тайлах (последняя строка/столбец — дорожка-проход). */
    private const val BUCKET = 3

    /** Шанс задекорировать отдельный бакет обычной комнаты. */
    private const val DECORATE_CHANCE = 0.5

    /** Доля лавы среди декорируемых бакетов (остальное — колонны). */
    private const val LAVA_SHARE = 0.45

    /** Плотность колонн внутри бакета. */
    private const val COLUMN_DENSITY = 0.55

    /** Шанс поставить кластер колонн в бакете комнаты-босса (арена — разреженно). */
    private const val BOSS_COLUMN_CHANCE = 0.3

    fun decorate(random: Random, tiles: Array<TileType>, width: Int, rooms: List<Room>) {
        for (room in rooms) {
            decorateRoom(random, tiles, width, room)
        }
    }

    private fun decorateRoom(random: Random, tiles: Array<TileType>, width: Int, room: Room) {
        // Внутренняя область с отступом 1 от стен — периметр комнаты остаётся полом.
        val x0 = room.x + 1
        val y0 = room.y + 1
        val xMax = room.x + room.width - 2
        val yMax = room.y + room.height - 2
        if (xMax < x0 || yMax < y0) return // слишком маленькая комната — не трогаем

        val center = room.center
        var by = y0
        while (by <= yMax) {
            var bx = x0
            while (bx <= xMax) {
                // Под-область бакета: резервируем правый столбец и нижнюю строку под проход.
                val subXMax = minOf(bx + BUCKET - 2, xMax)
                val subYMax = minOf(by + BUCKET - 2, yMax)
                decorateBucket(random, tiles, width, room.isBoss, bx, by, subXMax, subYMax, center)
                bx += BUCKET
            }
            by += BUCKET
        }
    }

    @Suppress("LongParameterList")
    private fun decorateBucket(
        random: Random,
        tiles: Array<TileType>,
        width: Int,
        isBoss: Boolean,
        bx: Int,
        by: Int,
        subXMax: Int,
        subYMax: Int,
        center: GridPos,
    ) {
        if (isBoss) {
            if (random.nextDouble() < BOSS_COLUMN_CHANCE) {
                placeColumns(random, tiles, width, bx, by, subXMax, subYMax, center)
            }
            return
        }
        if (random.nextDouble() >= DECORATE_CHANCE) return
        if (random.nextDouble() < LAVA_SHARE) {
            placeLava(tiles, width, bx, by, subXMax, subYMax, center)
        } else {
            placeColumns(random, tiles, width, bx, by, subXMax, subYMax, center)
        }
    }

    @Suppress("LongParameterList")
    private fun placeColumns(
        random: Random,
        tiles: Array<TileType>,
        width: Int,
        bx: Int,
        by: Int,
        subXMax: Int,
        subYMax: Int,
        center: GridPos,
    ) {
        for (y in by..subYMax) {
            for (x in bx..subXMax) {
                if (GridPos(x, y) == center) continue
                if (random.nextDouble() < COLUMN_DENSITY) {
                    tiles[y * width + x] = TileType.COLUMN
                }
            }
        }
    }

    @Suppress("LongParameterList")
    private fun placeLava(
        tiles: Array<TileType>,
        width: Int,
        bx: Int,
        by: Int,
        subXMax: Int,
        subYMax: Int,
        center: GridPos,
    ) {
        for (y in by..subYMax) {
            for (x in bx..subXMax) {
                if (GridPos(x, y) == center) continue
                tiles[y * width + x] = TileType.LAVA
            }
        }
    }
}
