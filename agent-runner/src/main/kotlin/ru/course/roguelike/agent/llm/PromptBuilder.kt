package ru.course.roguelike.agent.llm

import ru.course.roguelike.agent.llm.MapRenderer.formatFullMap
import ru.course.roguelike.shared.dto.GameSnapshot

object PromptBuilder {
    fun buildSystemPrompt(snapshot: GameSnapshot): String {
        return buildString {
            appendLine(buildHeader())
            appendLine(buildSessionInfo(snapshot))
            appendLine(buildToolsDescription())
            appendLine(buildRules())
            appendLine(buildGameState(snapshot))
            appendLine(buildMapSection(snapshot))
            appendLine(buildKeyCollectedMessage(snapshot))
            appendLine(buildClosingRules())
        }.trimIndent()
    }

    fun buildSystemPromptInLoop(sessionId: String): String {
        return """
        Ты — агент в roguelike игре. Твоя задача — собрать все ключи и встать на выход.
        Текущая сессия: $sessionId
        Доступен ТОЛЬКО один инструмент: game_act.
            * move_north   — шаг наверх (увеличение координаты x)
            * move_south   — шаг вниз (уменьшение координаты x)
            * move_east    — шаг вправо (увеличение координаты y)
            * move_west    — шаг влево (уменьшение координаты y)
            * interact     — подобрать ключ или открыть выход
            * wait         — пропустить ход
        Параметр action может быть: move_north, move_south, move_east, move_west, interact, wait.

        ПРАВИЛА:
        - ЗАПРЕЩЕНО писать любой текст. Не рассуждай, не объясняй, не описывай.
        - Твой ответ должен быть ТОЛЬКО вызовом инструмента game_act.
        - Пример правильного ответа: вызов game_act с {"action": "interact"}.

**Если ты получил сообщение "Внимание: вы повторяете одни и те же действия или стоите на месте. Измените стратегию." – ты НЕМЕДЛЕННО должен выбрать действие, отличное от предыдущих четырёх. Например, если ты 4 раза подряд двигался на восток, выбери move_south, move_north или move_west.**
        Никаких слов. Только вызов инструмента.
        """.trimIndent()
    }

    private fun buildHeader(): String = """
        Ты — агент в roguelike игре. Твоя задача — собрать все ключи и встать на выход и дойти до выхода (На карте выход отмечен как E, а ключ как K).
        Карта игры представляет из себя лабиринт с комнатами.
        Тебе предстоит передвигаться через коридоры между комнатами и достигнуть выхода.
    """.trimIndent()

    private fun buildSessionInfo(snapshot: GameSnapshot): String =
        "Текущая сессия: ${snapshot.sessionId}"

    private fun buildToolsDescription(): String = """
        Доступен ТОЛЬКО один инструмент: game_act.
            * move_north   — шаг наверх (увеличение координаты x на ~0.672)
            * move_south   — шаг вниз (уменьшение координаты x на ~0.672)
            * move_east    — шаг вправо (увеличение координаты y на ~0.672)
            * move_west    — шаг влево (уменьшение координаты y на ~0.672)
            * interact     — подобрать ключ или открыть выход
            * wait         — пропустить ход
            * move_to_cell - переместиться в заданную клетку. Можно переместиться только в соседние клетки. Принимает параметры x и y.
        Параметр action может быть: move_north, move_south, move_east, move_west, interact, wait и move_to_cell.
    """.trimIndent()

    private fun buildRules(): String = """
        ПРАВИЛА:
        - ЗАПРЕЩЕНО писать любой текст. Не объясняй, не описывай.
        - Твой ответ должен быть ТОЛЬКО вызовом инструмента game_act.
        - Пример правильного ответа: вызов game_act с {"action": "interact"}.
        - Если после твоего действия позиция не изменилась (сообщение "Позиция не изменилась"), то в следующем шаге ТЫ НЕ ИМЕЕШЬ ПРАВА выбирать то же действие. Выбери любое другое из доступных.
        - НИКОГДА не вызывай interact, если ты не стоишь на клетке с ключом (K) или выходом (E). Если на карте нет K и E – interact бесполезен.
        
        Перед каждым вызовом game_act ты ОБЯЗАН мысленно выполнить:
        1. Посмотреть на карту (я дам её ниже).
        2. Определить, какие из четырёх направлений (north, south, east, west) ведут на пол (.) или выход (E). Направления, ведущие в стену (#) или лаву (L), НЕДОСТУПНЫ.
        3. Выбрать одно из доступных направлений.
        4. Вызвать game_act с этим направлением.
        
        **Если ты получил сообщение "Внимание: вы повторяете одни и те же действия или стоите на месте. Измените стратегию." – ты НЕМЕДЛЕННО должен выбрать действие, отличное от предыдущих четырёх. Например, если ты 4 раза подряд двигался на восток, выбери move_south, move_north или move_west.**
        Ты управляешь игроком в roguelike игре. У тебя есть MCP-инструменты (game_act и другие). Всегда вызывай game_act с нужным действием.
        Прежде чем выбрать действие, мысленно проложи маршрут до выхода, используя полную карту. Затем вызови game_act.
    """.trimIndent()

    private fun buildGameState(snapshot: GameSnapshot): String {
        val playerX = snapshot.agent?.pose?.x ?: snapshot.player.pose.x
        val playerY = snapshot.agent?.pose?.y ?: snapshot.player.pose.y
        val playerYaw = snapshot.agent?.pose?.yaw ?: snapshot.player.pose.yaw
        val playerHP = snapshot.agent?.hp ?: snapshot.player.hp
        val playerMaxHP = snapshot.agent?.maxHp ?: snapshot.player.maxHp

        val keysList = snapshot.keyPickups.joinToString(prefix = "[", postfix = "]") { key ->
            "(${key.x.toInt()}, ${key.y.toInt()})"
        }

        return """
            Фаза: ${snapshot.phase}
            Здоровье: $playerHP/$playerMaxHP
            Позиция игрока: x=$playerX, y=$playerY
            Взгляд: yaw = $playerYaw
            Ключи: ${snapshot.keysCollected}/${snapshot.keysRequired}
            Список оставшихся ключей: $keysList
            Выход: ${snapshot.exitGate}
        """.trimIndent()
    }

    private fun buildMapSection(snapshot: GameSnapshot): String {
        val localMap = formatFullMap(snapshot)
        return """
            Карта (x - вертикаль, y - горизонталь, @ = ты, E = выход, # = стена, . = пол, L = лава):
            $localMap
        """.trimIndent()
    }

    private fun buildKeyCollectedMessage(snapshot: GameSnapshot): String {
        return if (snapshot.keysCollected == snapshot.keysRequired) {
            "Все ключи собраны, надо идти к выходу."
        } else {
            ""
        }
    }

    private fun buildClosingRules(): String = """
        Правила:
        - Не пытайся пройти сквозь стену (#) или лаву (L).
        - Если упёрся в тупик — выбирай другое направление.
        - Сначала проверяй соседнюю клетку в выбранном направлении: если там стена или лава — не ходи туда.
        - Используй interact только если стоишь на ключе или выходе.
        - ВАЖНО: Всегда вызывай game_act с действием. Никогда не отвечай текстом без вызова инструмента.
    """.trimIndent()
}
