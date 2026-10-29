package com.lucento.app.domain.backup

/**
 * A small JSON tree for the backup file. Numbers are kept as their original text so money stays an
 * integer (a decimal or an exponent is rejected) and never passes through floating point.
 */
sealed interface JsonValue {
    data class Obj(val fields: Map<String, JsonValue>) : JsonValue
    data class Arr(val items: List<JsonValue>) : JsonValue
    data class Str(val value: String) : JsonValue
    data class Num(val raw: String) : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data object Null : JsonValue
}

object Json {
    fun parse(text: String): JsonValue = JsonParser(text).parse()

    fun stringify(value: JsonValue): String {
        val out = StringBuilder(256)
        write(out, value, 0)
        out.append('\n')
        return out.toString()
    }

    private fun write(out: StringBuilder, value: JsonValue, indent: Int) {
        when (value) {
            is JsonValue.Null -> out.append("null")
            is JsonValue.Bool -> out.append(if (value.value) "true" else "false")
            is JsonValue.Num -> out.append(value.raw)
            is JsonValue.Str -> writeString(out, value.value)
            is JsonValue.Arr -> {
                if (value.items.isEmpty()) {
                    out.append("[]")
                    return
                }
                out.append("[\n")
                value.items.forEachIndexed { index, item ->
                    indent(out, indent + 1)
                    write(out, item, indent + 1)
                    if (index != value.items.lastIndex) out.append(',')
                    out.append('\n')
                }
                indent(out, indent)
                out.append(']')
            }
            is JsonValue.Obj -> {
                if (value.fields.isEmpty()) {
                    out.append("{}")
                    return
                }
                out.append("{\n")
                val entries = value.fields.entries.toList()
                entries.forEachIndexed { index, (key, item) ->
                    indent(out, indent + 1)
                    writeString(out, key)
                    out.append(": ")
                    write(out, item, indent + 1)
                    if (index != entries.lastIndex) out.append(',')
                    out.append('\n')
                }
                indent(out, indent)
                out.append('}')
            }
        }
    }

    private fun indent(out: StringBuilder, level: Int) {
        repeat(level) { out.append("  ") }
    }

    private fun writeString(out: StringBuilder, value: String) {
        out.append('"')
        value.forEach { c ->
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (c < ' ') {
                    out.append("\\u")
                    out.append(c.code.toString(16).padStart(4, '0'))
                } else {
                    out.append(c)
                }
            }
        }
        out.append('"')
    }
}

private class JsonParser(private val s: String) {
    private var i = 0

    fun parse(): JsonValue {
        skip()
        if (i >= s.length) fail()
        val value = value()
        skip()
        if (i != s.length) fail()
        return value
    }

    private fun value(): JsonValue {
        if (i >= s.length) fail()
        return when (val c = s[i]) {
            '{' -> obj()
            '[' -> arr()
            '"' -> JsonValue.Str(str())
            't' -> literal("true", JsonValue.Bool(true))
            'f' -> literal("false", JsonValue.Bool(false))
            'n' -> literal("null", JsonValue.Null)
            '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> num()
            else -> fail()
        }
    }

    private fun obj(): JsonValue.Obj {
        expect('{')
        skip()
        val fields = LinkedHashMap<String, JsonValue>()
        if (peek() == '}') {
            i++
            return JsonValue.Obj(fields)
        }
        while (true) {
            skip()
            if (peek() != '"') fail()
            val key = str()
            skip()
            expect(':')
            skip()
            if (fields.put(key, value()) != null) fail()
            skip()
            when (peek()) {
                ',' -> i++
                '}' -> {
                    i++
                    return JsonValue.Obj(fields)
                }
                else -> fail()
            }
        }
    }

    private fun arr(): JsonValue.Arr {
        expect('[')
        skip()
        val items = ArrayList<JsonValue>()
        if (peek() == ']') {
            i++
            return JsonValue.Arr(items)
        }
        while (true) {
            skip()
            items.add(value())
            skip()
            when (peek()) {
                ',' -> i++
                ']' -> {
                    i++
                    return JsonValue.Arr(items)
                }
                else -> fail()
            }
        }
    }

    private fun str(): String {
        expect('"')
        val out = StringBuilder()
        while (i < s.length) {
            val c = s[i++]
            when {
                c == '"' -> return out.toString()
                c == '\\' -> out.append(escape())
                c < ' ' -> fail()
                else -> out.append(c)
            }
        }
        fail()
    }

    private fun escape(): Char {
        if (i >= s.length) fail()
        return when (val c = s[i++]) {
            '"', '\\', '/' -> c
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                if (i + 4 > s.length) fail()
                val hex = s.substring(i, i + 4)
                i += 4
                val code = hex.toIntOrNull(16) ?: fail()
                code.toChar()
            }
            else -> fail()
        }
    }

    private fun literal(text: String, value: JsonValue): JsonValue {
        if (!s.regionMatches(i, text, 0, text.length)) fail()
        i += text.length
        return value
    }

    private fun num(): JsonValue.Num {
        val start = i
        if (peek() == '-') i++
        if (i >= s.length || !s[i].isDigit()) fail()
        if (s[i] == '0') {
            i++
        } else {
            while (i < s.length && s[i].isDigit()) i++
        }
        if (i < s.length && s[i].isDigit()) fail()
        if (i < s.length && (s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '+')) fail()
        return JsonValue.Num(s.substring(start, i))
    }

    private fun skip() {
        while (i < s.length && s[i].isWhitespace()) i++
    }

    private fun peek(): Char = if (i < s.length) s[i] else '\u0000'

    private fun expect(c: Char) {
        if (i >= s.length || s[i] != c) fail()
        i++
    }

    private fun fail(): Nothing = throw BackupFormatException(
        BackupFailure.MALFORMED,
        "This file isn’t valid JSON. Nothing was changed.",
    )
}

internal fun JsonValue.Num.toLongExact(): Long {
    if (raw.length > 19 || (raw.startsWith("-") && raw.length > 20)) {
        throw BackupFormatException(BackupFailure.INVALID, "This file isn’t a valid Lucento backup. Nothing was changed.")
    }
    return try {
        raw.toLong()
    } catch (_: NumberFormatException) {
        throw BackupFormatException(BackupFailure.INVALID, "This file isn’t a valid Lucento backup. Nothing was changed.")
    }
}

internal fun JsonValue.obj(): JsonValue.Obj = this as? JsonValue.Obj
    ?: throw BackupFormatException(BackupFailure.INVALID, "This file isn’t a valid Lucento backup. Nothing was changed.")
