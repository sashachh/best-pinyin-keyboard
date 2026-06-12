package io.github.sashachh.bestpinyin

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.TreeMap
import kotlin.math.ln
import kotlin.math.max

data class Candidate(
    val text: String,
    val consumed: Int,
    val isSentence: Boolean = false,
)

class PinyinEngine(private val context: Context) {

    private class Entry(val text: String, val weight: Long)

    private val zh = TreeMap<String, MutableList<Entry>>()
    private val en = TreeMap<String, MutableList<Entry>>()
    private var maxLen = 1
    private val prefs = context.getSharedPreferences("user_dict", Context.MODE_PRIVATE)

    @Volatile
    var ready = false
        private set

    fun loadAsync(onReady: () -> Unit) {
        Thread {
            loadDict("dict_zh.tsv", zh, trackMaxLen = true)
            loadDict("dict_en.tsv", en, trackMaxLen = false)
            ready = true
            onReady()
        }.start()
    }

    private fun loadDict(asset: String, map: TreeMap<String, MutableList<Entry>>, trackMaxLen: Boolean) {
        try {
            BufferedReader(InputStreamReader(context.assets.open(asset), Charsets.UTF_8)).useLines { lines ->
                for (line in lines) {
                    val parts = line.split('\t')
                    if (parts.size != 3) continue
                    val code = parts[0]
                    val weight = parts[2].toLongOrNull() ?: continue
                    map.getOrPut(code) { mutableListOf() }.add(Entry(parts[1], weight))
                    if (trackMaxLen && code.length > maxLen) maxLen = code.length
                }
            }
            for (list in map.values) list.sortByDescending { it.weight }
        } catch (_: Exception) {
        }
    }

    private fun userBoost(code: String, text: String): Long =
        prefs.getInt("$code\t$text", 0).toLong() * 50_000_000L

    fun recordSelection(code: String, text: String) {
        val key = "$code\t$text"
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    private fun bestFor(code: String): Entry? {
        val list = zh[code] ?: return null
        var best: Entry? = null
        var bestW = Long.MIN_VALUE
        for (e in list) {
            val w = e.weight + userBoost(code, e.text)
            if (w > bestW) {
                bestW = w
                best = e
            }
        }
        return best
    }

    /** Viterbi over the pinyin string; returns converted text + consumed length. */
    fun convert(s: String): Pair<String, Int> {
        if (s.isEmpty() || !ready) return Pair("", 0)
        val n = s.length
        val neg = Double.NEGATIVE_INFINITY
        val dp = DoubleArray(n + 1) { neg }
        dp[0] = 0.0
        val backI = IntArray(n + 1) { -1 }
        val backT = arrayOfNulls<String>(n + 1)
        for (j in 1..n) {
            val start = max(0, j - maxLen)
            for (i in start until j) {
                if (dp[i] == neg) continue
                val seg = s.substring(i, j)
                val e = bestFor(seg) ?: continue
                val w = e.weight + userBoost(seg, e.text)
                val sc = dp[i] + ln(max(w, 1L).toDouble() / 1e9) - 2.0
                if (sc > dp[j]) {
                    dp[j] = sc
                    backI[j] = i
                    backT[j] = e.text
                }
            }
        }
        var end = 0
        for (j in n downTo 1) {
            if (dp[j] != neg) {
                end = j
                break
            }
        }
        if (end == 0) return Pair("", 0)
        val parts = ArrayDeque<String>()
        var j = end
        while (j > 0) {
            parts.addFirst(backT[j] ?: break)
            j = backI[j]
        }
        return Pair(parts.joinToString(""), end)
    }

    fun candidates(s: String): List<Candidate> {
        if (s.isEmpty() || !ready) return emptyList()
        val out = mutableListOf<Candidate>()
        val seen = HashSet<String>()

        val (sentence, consumed) = convert(s)
        if (sentence.isNotEmpty() && sentence.length > 1) {
            out.add(Candidate(sentence, consumed, isSentence = true))
            seen.add(sentence)
        }

        // Exact-code word matches, longest prefix first
        var len = minOf(maxLen, s.length)
        while (len >= 1) {
            val code = s.substring(0, len)
            val list = zh[code]
            if (list != null) {
                val ranked = list.sortedByDescending { it.weight + userBoost(code, it.text) }
                for (e in ranked.take(4)) {
                    if (seen.add(e.text)) out.add(Candidate(e.text, len))
                }
            }
            len--
        }

        // English words (exact + completions)
        if (s.length >= 2) {
            val matches = mutableListOf<Entry>()
            for ((code, list) in en.tailMap(s)) {
                if (!code.startsWith(s)) break
                matches.addAll(list)
                if (matches.size > 30) break
            }
            matches.sortByDescending { it.weight }
            for (e in matches.take(3)) {
                if (seen.add(e.text)) out.add(Candidate(e.text, s.length))
            }
        }

        // Chinese completions (input is a prefix of a longer word's code)
        val comps = mutableListOf<Pair<String, Entry>>()
        for ((code, list) in zh.tailMap(s)) {
            if (!code.startsWith(s)) break
            if (code != s && list.isNotEmpty()) comps.add(Pair(code, list[0]))
            if (comps.size > 40) break
        }
        comps.sortByDescending { it.second.weight }
        for ((_, e) in comps.take(3)) {
            if (seen.add(e.text)) out.add(Candidate(e.text, s.length))
        }

        return out.take(30)
    }
}
