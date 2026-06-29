package ru.course.roguelike.game.infrastructure.level

import ru.course.roguelike.game.domain.level.GeneratedLevel
import ru.course.roguelike.game.domain.level.LevelGenerator
import ru.course.roguelike.game.domain.level.MapConnectivity
import ru.course.roguelike.game.domain.level.Room
import ru.course.roguelike.shared.engine.TileMap
import ru.course.roguelike.shared.model.TileType
import kotlin.random.Random

/**
 * Лабиринтная локация (issue #3).
 *
 * На глобальном уровне карта — это сетка комнат (широкие пространства),
 * соединённых узкими коридорами шириной в один тайл. Коридоры строятся как
 * остовное дерево по сетке плюс несколько дополнительных рёбер, что даёт
 * развилки и петли. Одна дальняя комната помечается как комната-босс и делается
 * заметно больше обычных.
 *
 * Генератор отвечает только за структуру (комнаты + коридоры + спавн).
 * Декорирование комнат (колонны, лава) и проверка проходимости выполняются
 * поверх результата — см. задачи #3 и #4. Список [GeneratedLevel.rooms]
 * предоставляется именно для этих шагов.
 */
object LabyrinthLevelGenerator : LevelGenerator {
    const val DEFAULT_WIDTH = 48
    const val DEFAULT_HEIGHT = 48

    /** Сетка комнат: GRID_COLS x GRID_ROWS ячеек, по одной комнате на ячейку. */
    private const val GRID_COLS = 4
    private const val GRID_ROWS = 4

    /** Размеры обычной комнаты внутри ячейки (в тайлах). */
    private const val MIN_ROOM_SIZE = 4
    private const val MAX_ROOM_SIZE = 7

    /** Минимальный зазор-стена между комнатой и границей её ячейки. */
    private const val CELL_MARGIN = 1

    /**
     * Сколько раз перегенерировать карту, если итог оказался непроходимым
     * (актуально после добавления декора — колонн/лавы). Структурная генерация
     * связна по построению, поэтому без декора хватает первой попытки.
     */
    private const val MAX_ATTEMPTS = 16

    override fun generate(seed: Long): GeneratedLevel =
        generate(seed, DEFAULT_WIDTH, DEFAULT_HEIGHT)

    fun generate(seed: Long, width: Int, height: Int): GeneratedLevel {
        require(width >= GRID_COLS * (MAX_ROOM_SIZE + 2 * CELL_MARGIN)) {
            "width=$width слишком мал для сетки ${GRID_COLS}x$GRID_ROWS"
        }
        require(height >= GRID_ROWS * (MAX_ROOM_SIZE + 2 * CELL_MARGIN)) {
            "height=$height слишком мал для сетки ${GRID_COLS}x$GRID_ROWS"
        }

        // Детерминированный повтор: попытка N использует seed+N. При seed одинаков
        // первый успешный кандидат тоже одинаков, поэтому генерация воспроизводима.
        for (attempt in 0 until MAX_ATTEMPTS) {
            val candidate = buildCandidate(seed + attempt, width, height)
            if (MapConnectivity.allRoomsReachable(candidate.map, candidate.playerSpawn, candidate.rooms)) {
                return candidate
            }
        }
        error("не удалось сгенерировать проходимый лабиринт за $MAX_ATTEMPTS попыток (seed=$seed)")
    }

    private fun buildCandidate(seed: Long, width: Int, height: Int): GeneratedLevel {
        val random = Random(seed)
        // Старт — сплошные стены, затем вырезаем комнаты и коридоры.
        val tiles = Array(width * height) { TileType.WALL }

        val rooms = placeRooms(random, tiles, width, height)
        CorridorCarver.connect(random, tiles, width, rooms, GRID_COLS, GRID_ROWS)
        // Декорируем комнаты бакетами (колонны/лава) поверх tiles+rooms; цикл повтора
        // в generate() отбракует декор, нарушивший проходимость.
        RoomDecorator.decorate(random, tiles, width, rooms)

        // Спавн — центр стартовой комнаты (ячейка 0,0), гарантированно FLOOR.
        val spawn = rooms.first().center
        return GeneratedLevel(TileMap(width, height, tiles), spawn, rooms)
    }

    /**
     * Размещает по одной комнате в каждой ячейке сетки. Дальняя ячейка
     * (правый верхний угол) становится увеличенной комнатой-боссом.
     * Порядок в списке: индекс комнаты = gridY * GRID_COLS + gridX.
     */
    private fun placeRooms(
        random: Random,
        tiles: Array<TileType>,
        width: Int,
        height: Int,
    ): List<Room> {
        val cellW = width / GRID_COLS
        val cellH = height / GRID_ROWS
        val bossGridX = GRID_COLS - 1
        val bossGridY = GRID_ROWS - 1

        val rooms = ArrayList<Room>(GRID_COLS * GRID_ROWS)
        for (gy in 0 until GRID_ROWS) {
            for (gx in 0 until GRID_COLS) {
                val isBoss = gx == bossGridX && gy == bossGridY
                val room = carveRoomInCell(random, tiles, width, gx, gy, cellW, cellH, isBoss)
                rooms.add(room)
            }
        }
        return rooms
    }

    @Suppress("LongParameterList")
    private fun carveRoomInCell(
        random: Random,
        tiles: Array<TileType>,
        width: Int,
        gx: Int,
        gy: Int,
        cellW: Int,
        cellH: Int,
        isBoss: Boolean,
    ): Room {
        // Доступное пространство внутри ячейки с учётом зазоров-стен.
        val maxW = cellW - 2 * CELL_MARGIN
        val maxH = cellH - 2 * CELL_MARGIN
        val roomW = if (isBoss) maxW else random.nextInt(MIN_ROOM_SIZE, minOf(MAX_ROOM_SIZE, maxW) + 1)
        val roomH = if (isBoss) maxH else random.nextInt(MIN_ROOM_SIZE, minOf(MAX_ROOM_SIZE, maxH) + 1)

        val cellX0 = gx * cellW
        val cellY0 = gy * cellH
        // Случайное смещение внутри ячейки, оставляя минимум CELL_MARGIN до границ.
        val slackX = cellW - roomW - 2 * CELL_MARGIN
        val slackY = cellH - roomH - 2 * CELL_MARGIN
        val x = cellX0 + CELL_MARGIN + if (slackX > 0) random.nextInt(slackX + 1) else 0
        val y = cellY0 + CELL_MARGIN + if (slackY > 0) random.nextInt(slackY + 1) else 0

        val room = Room(x, y, roomW, roomH, isBoss)
        carveRect(tiles, width, room)
        return room
    }

    private fun carveRect(tiles: Array<TileType>, width: Int, room: Room) {
        for (ry in room.y until room.y + room.height) {
            for (rx in room.x until room.x + room.width) {
                tiles[ry * width + rx] = TileType.FLOOR
            }
        }
    }
}
