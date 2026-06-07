package ru.course.roguelike.shared.render

object SceneRenderConfig {
    const val SKY_RGB = 0x2a3548
    const val FLOOR_BASE_RGB = 0x3d4a3a

    /** Яркий однотонный цвет ячейки выхода (без текстуры лифта/двери). */
    const val EXIT_GATE_RGB = 0x33EE66

    /** Число горизонтальных полос в textures/walls.png (256×512 → 2 ряда по 256px). */
    const val WALL_ATLAS_ROWS = 2

    /** Повторов текстуры кирпича на один мировой тайл стены (U — вдоль стены, V — по высоте колонны). */
    const val WALL_HORIZONTAL_REPEATS = 1

    const val WALL_VERTICAL_REPEATS = 1

    /** Доля высоты стены под горизонтальную «крышку» (верхняя грань). */
    const val WALL_CAP_FRACTION = 0.14f

    const val WALL_CAP_DARKEN = 0.72f

    /** Прокрутка неба при повороте (доля оборота на ширину экрана). */
    const val SKY_YAW_SCALE = 0.12f

    const val DISTANCE_SHADE_FACTOR = 0.28f
    const val DISTANCE_SHADE_MIN = 0.25f

    const val Y_WALL_DARKEN = 1f
    const val X_WALL_DARKEN = 0.78f

    const val MAX_FLOOR_DISTANCE = 28f

    /**
     * Внутреннее разрешение raycast-буфера; картинка апскейлится на окно.
     * Меньше пикселей = быстрее, при этом каждый пиксель сэмплируется полноценно (без полос).
     */
    const val VIEW_WIDTH = 480
    const val VIEW_HEIGHT = 270

    const val PITCH_HORIZON_FACTOR = 0.45f

    const val AMBIENT_VOLUME = 10f

    const val HIT_VOLUME = 10f

    fun sideDarken(wallSide: Int): Float = if (wallSide == 0) Y_WALL_DARKEN else X_WALL_DARKEN

    /** Положительный pitch (взгляд вверх) опускает линию горизонта — больше неба, меньше пола. */
    fun horizonY(viewHeight: Int, pitch: Float): Float =
        (
            viewHeight / 2f +
                kotlin.math.sin(pitch) * viewHeight * PITCH_HORIZON_FACTOR
            )
            .coerceIn(0f, viewHeight.toFloat())
}
