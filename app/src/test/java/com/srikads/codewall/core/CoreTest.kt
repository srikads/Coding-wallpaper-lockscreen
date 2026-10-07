package com.srikads.codewall.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonPrinterTest {
    @Test
    fun printsNestedObjectWithHighlighting() {
        val doc = obj("time" to JString("14:32:07"), "weather" to obj("temp" to JNumber(21.4)), "ok" to JBool(true))
        val lines = JsonPrinter().print(doc, "// today.json")
        assertEquals(
            listOf(
                "// today.json",
                "{",
                "  \"time\": \"14:32:07\",",
                "  \"weather\": {",
                "    \"temp\": 21.4",
                "  },",
                "  \"ok\": true",
                "}",
            ),
            lines.map { it.text },
        )
        val timeLine = lines[2]
        assertEquals(Tok.KEY, timeLine.spans[1].tok)
        assertEquals(Tok.STRING, timeLine.spans[3].tok)
        assertEquals("/time", timeLine.id)
    }

    @Test
    fun inlineObjectsInArrays() {
        val doc = obj("tasks" to JArray(listOf(obj("done" to JBool(false), "title" to JString("ship"), inline = true))))
        val text = JsonPrinter().print(doc).map { it.text }
        assertEquals("    { \"done\": false, \"title\": \"ship\" }", text[2])
    }

    @Test
    fun emptyContainers() {
        val text = JsonPrinter().print(obj("tasks" to JArray(emptyList()))).map { it.text }
        assertEquals(listOf("{", "  \"tasks\": []", "}"), text)
    }

    @Test
    fun escapesStrings() {
        assertEquals("\"a\\\"b\\n\"", JString("a\"b\n").toJsonText())
    }

    @Test
    fun decimals() {
        assertEquals("21.4", JNumber(21.43).raw)
        assertEquals("21", JNumber(21.0).raw)
        assertEquals("-3", JNumber(-3.04).raw)
    }
}

class TemplateTest {
    private val doc = obj("time" to JString("09:00:01"), "weather" to obj("temp" to JNumber(18)), "tasks" to JArray(listOf(JString("a"))))

    @Test
    fun fillsPlaceholders() {
        assertEquals(
            "t=\"09:00:01\" w=18 x=null list=[\"a\"] first=a",
            TemplateEngine.fill("t=\"{{time}}\" w={{ weather.temp }} x={{nope}} list={{tasks}} first={{tasks.0}}", doc),
        )
    }

    @Test
    fun highlighterTokens() {
        val spans = Highlighter.tokenize("const x = { time: \"1\", n: 2 }; // hi")
        val toks = spans.filter { it.text.isNotBlank() }.associate { it.text.trim() to it.tok }
        assertEquals(Tok.KEYWORD, toks["const"])
        assertEquals(Tok.KEY, toks["time"])
        assertEquals(Tok.STRING, toks["\"1\""])
        assertEquals(Tok.NUMBER, toks["2"])
        assertEquals(Tok.COMMENT, toks["// hi"])
    }
}

class ConfigTest {
    @Test
    fun roundTrip() {
        val c = WallConfig(themeId = "matrix", fontSizeSp = 18f, manualLat = 12.3, manualLon = 45.6, useTemplate = true)
            .let { it.copy(fields = it.fields.map { f -> if (f.id == FieldId.TIME) f.copy(key = "now") else f }) }
        assertEquals(c, WallConfig.fromJson(c.toJson().toString()))
    }

    @Test
    fun garbageFallsBackToDefaults() {
        assertEquals(WallConfig(), WallConfig.fromJson("not json"))
    }

    @Test
    fun documentHonorsLockVisibility() {
        val values = mapOf(FieldId.TIME to JString("t"), FieldId.TASKS to JArray(emptyList()))
        val cfg = WallConfig()
        val unlocked = DocumentBuilder.build(cfg, values, locked = false)
        val locked = DocumentBuilder.build(cfg, values, locked = true)
        assertTrue(unlocked.entries.any { it.id == FieldId.TASKS.name })
        assertFalse(locked.entries.any { it.id == FieldId.TASKS.name })
    }
}

class TasksTest {
    @Test
    fun todayFiltering() {
        val today = "2026-10-07"
        val tasks = listOf(
            TaskItem(1, "open"),
            TaskItem(2, "done yesterday", doneOn = "2026-10-06"),
            TaskItem(3, "done today", doneOn = today),
            TaskItem(4, "future", date = "2026-10-09"),
            TaskItem(5, "overdue", date = "2026-10-01"),
            TaskItem(6, "daily", daily = true, doneOn = "2026-10-06"),
        )
        val visible = tasks.filter { it.isForToday(today) }.map { it.id }
        assertEquals(listOf(1L, 3L, 5L, 6L), visible)
        assertFalse(tasks[5].isDone(today))
        assertEquals(tasks, TaskItem.listFromJson(TaskItem.listToJson(tasks)))
    }

    @Test
    fun misc() {
        assertEquals("2h 05m", humanDuration(125 * 60_000L))
        assertEquals("1d 2h", humanDuration(26 * 3_600_000L))
        assertEquals(12.3, roundCoordinate(12.3456), 1e-9)
        assertEquals("clear", WeatherCodes.describe(0))
        assertTrue(Quotes.pick(QuoteKind.MIXED, 0L, 30).isNotEmpty())
    }
}
