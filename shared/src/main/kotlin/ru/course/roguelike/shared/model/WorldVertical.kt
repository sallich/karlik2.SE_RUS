package ru.course.roguelike.shared.model

/**
 * Вертикальные единицы мира: один ярус = [FLOOR_STEP], высота над текущим полом — [PlayerPose.height].
 *
 * Абсолютная высота глаза: `floorLevel * FLOOR_STEP + pose.height + EYE_HEIGHT`.
 */
object WorldVertical {
    /** Высота глаза над локальным полом в покое. */
    const val EYE_HEIGHT = 0.35f

    /** Расстояние между ярусами (лифт поднимает ровно на один шаг). */
    const val FLOOR_STEP = 2.0f

    const val WALL_HEIGHT = 1.0f
    const val COLUMN_HEIGHT = 0.45f

    /** Половина ширины колонны для коллизии (уже клетки, как в рендере). */
    const val COLUMN_COLLISION_HALF_SIZE = 0.25f

    /** Высота красной печати ([TileType.ROOM_SEAL]) — как у стены лабиринта. */
    const val SEAL_HEIGHT = WALL_HEIGHT

    /** Насколько смещается линия пола на экране на каждую единицу высоты мира. */
    const val ELEVATION_SCREEN_FACTOR = 0.32f

    /** Минимальный прыжок, чтобы перелезть через колонну. */
    val MAX_JUMP_CLEARANCE: Float
        get() = (COLUMN_HEIGHT - EYE_HEIGHT + 0.08f).coerceAtLeast(0.1f)

    fun eyeWorldZ(floorLevel: Int, localHeight: Float): Float =
        floorLevel * FLOOR_STEP + localHeight + EYE_HEIGHT

    fun floorWorldZ(floorLevel: Int): Float = floorLevel * FLOOR_STEP

    fun floorScreenOffset(viewHeight: Int, floorLevel: Int, localHeight: Float): Float =
        (floorLevel * FLOOR_STEP + localHeight) * viewHeight * ELEVATION_SCREEN_FACTOR

    fun tileTopWorldZ(floorLevel: Int, tile: TileType): Float =
        floorWorldZ(floorLevel) + tile.wallHeight()

    fun blocksVisionAt(floorLevel: Int, tile: TileType, eyeWorldZ: Float): Boolean {
        if (!tile.blocksVision) return false
        return eyeWorldZ <= tileTopWorldZ(floorLevel, tile) + 0.02f
    }

    fun blocksMovementAt(floorLevel: Int, tile: TileType, localHeight: Float): Boolean {
        if (tile.walkable) return false
        if (tile == TileType.COLUMN) {
            return localHeight < COLUMN_HEIGHT - 0.03f
        }
        return true
    }

}

fun TileType.wallHeight(): Float = when (this) {
    TileType.WALL,
    TileType.ROOM_DOOR,
    -> WorldVertical.WALL_HEIGHT
    TileType.ROOM_SEAL -> WorldVertical.SEAL_HEIGHT
    TileType.COLUMN -> WorldVertical.COLUMN_HEIGHT
    else -> 0f
}
