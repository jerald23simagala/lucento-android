package com.lucento.app.domain.capture

import java.util.Locale

/**
 * Rewrites English number words as digits so spoken or typed amounts read like written ones:
 * "two thousand five hundred" → "2500", "a hundred and fifty pesos" → "150 pesos", "twelve fifty" stays two numbers
 * ("12 50") because a speaker's meaning there is genuinely unclear and the review shows both.
 *
 * Only runs of number words are touched; "a" counts only directly before hundred/thousand/million, and "and" only
 * inside a run ("one hundred and five"), so "lunch and coffee" is never changed.
 */
object NumberWords {

    private val units = mapOf(
        "zero" to 0L, "one" to 1L, "two" to 2L, "three" to 3L, "four" to 4L, "five" to 5L, "six" to 6L, "seven" to 7L,
        "eight" to 8L, "nine" to 9L, "ten" to 10L, "eleven" to 11L, "twelve" to 12L, "thirteen" to 13L, "fourteen" to 14L,
        "fifteen" to 15L, "sixteen" to 16L, "seventeen" to 17L, "eighteen" to 18L, "nineteen" to 19L,
    )
    private val tens = mapOf(
        "twenty" to 20L, "thirty" to 30L, "forty" to 40L, "fifty" to 50L, "sixty" to 60L, "seventy" to 70L,
        "eighty" to 80L, "ninety" to 90L,
    )
    private val scales = mapOf("hundred" to 100L, "thousand" to 1_000L, "million" to 1_000_000L)

    private val TOKEN = Regex("[A-Za-z]+(?:-[A-Za-z]+)?|\\S")

    fun normalize(text: String): String {
        val tokens = TOKEN.findAll(text).toList()
        if (tokens.none { isNumberWord(it.value) }) return text
        val out = StringBuilder()
        var cursor = 0
        var i = 0
        while (i < tokens.size) {
            val run = runAt(tokens.map { it.value }, i)
            if (run == null) {
                i++
                continue
            }
            val (end, value) = run
            out.append(text, cursor, tokens[i].range.first)
            out.append(value)
            cursor = tokens[end - 1].range.last + 1
            i = end
        }
        out.append(text, cursor, text.length)
        return out.toString()
    }

    private fun isNumberWord(word: String): Boolean {
        val w = word.lowercase(Locale.ROOT)
        return w in units || w in tens || w in scales || w.split('-').let { p -> p.size == 2 && p[0] in tens && p[1] in units }
    }

    /** A number-word run starting at [start]: (exclusive end index, value), or null. */
    private fun runAt(words: List<String>, start: Int): Pair<Int, Long>? {
        var i = start
        var total = 0L
        var current = 0L
        var consumed = 0
        while (i < words.size) {
            val w = words[i].lowercase(Locale.ROOT)
            when {
                w == "a" && consumed == 0 && i + 1 < words.size && words[i + 1].lowercase(Locale.ROOT) in scales -> {
                    current = 1
                }
                w == "and" && consumed > 0 && (total + current) >= 100 && i + 1 < words.size &&
                    words[i + 1].lowercase(Locale.ROOT).let { it in units || it in tens || it.contains('-') && isNumberWord(it) } -> {
                    i++
                    continue
                }
                w in units -> {
                    val unit = units.getValue(w)
                    val rest = current % 100
                    // "twenty five" joins; "twelve fifty" or "five ten" are two separate numbers, not one.
                    val joins = consumed == 0 || rest == 0L || (unit < 10 && rest >= 20 && rest % 10 == 0L)
                    if (!joins) break
                    current += unit
                }
                w in tens -> {
                    if (consumed > 0 && current % 100 != 0L) break
                    current += tens.getValue(w)
                }
                w.contains('-') && isNumberWord(w) -> {
                    if (consumed > 0 && current % 100 != 0L) break
                    val (t, u) = w.split('-')
                    current += tens.getValue(t) + units.getValue(u)
                }
                w in scales -> {
                    if (consumed == 0 && current == 0L) return null
                    val scale = scales.getValue(w)
                    if (scale == 100L) {
                        current = (if (current == 0L) 1 else current) * 100
                    } else {
                        total += (if (current == 0L) 1 else current) * scale
                        current = 0
                    }
                }
                else -> break
            }
            consumed++
            i++
        }
        if (consumed == 0) return null
        return i to (total + current)
    }
}
