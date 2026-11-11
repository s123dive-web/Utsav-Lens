package com.utsavlens.sd

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * CLIP byte-pair-encoding tokenizer (the one Stable Diffusion 1.x uses), implemented from the
 * published algorithm. Vocabulary and merge table are downloaded with the model, not bundled.
 */
class ClipTokenizer(vocabJson: String, mergesText: String) {
    private val vocab: Map<String, Int> =
        Json.parseToJsonElement(vocabJson).jsonObject.mapValues { it.value.jsonPrimitive.int }
    private val ranks: Map<Pair<String, String>, Int>
    private val cache = HashMap<String, List<String>>()

    val bosId: Int = vocab[BOS] ?: 49406
    val eosId: Int = vocab[EOS] ?: 49407

    init {
        val map = HashMap<Pair<String, String>, Int>()
        mergesText.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#version") }
            .forEachIndexed { i, line ->
                val parts = line.trim().split(' ')
                if (parts.size == 2) map[parts[0] to parts[1]] = i
            }
        ranks = map
    }

    /** Returns exactly [maxLength] ids: BOS, tokens (truncated), EOS, then EOS padding. */
    fun encode(text: String, maxLength: Int = 77): IntArray {
        val ids = ArrayList<Int>(maxLength)
        ids += bosId
        val clean = text.lowercase().replace(WHITESPACE, " ").trim()
        loop@ for (match in TOKEN.findAll(clean)) {
            for (piece in bpe(match.value)) {
                if (ids.size >= maxLength - 1) break@loop
                ids += vocab[piece] ?: eosId
            }
        }
        ids += eosId
        while (ids.size < maxLength) ids += eosId
        return ids.toIntArray()
    }

    private fun bpe(token: String): List<String> = cache.getOrPut(token) {
        if (token.isEmpty()) return@getOrPut emptyList()
        val codePoints = token.codePoints().toArray()
        var word: List<String> = codePoints.mapIndexed { i, cp ->
            val s = String(Character.toChars(cp))
            if (i == codePoints.size - 1) "$s</w>" else s
        }
        while (word.size > 1) {
            var best: Pair<String, String>? = null
            var bestRank = Int.MAX_VALUE
            for (i in 0 until word.size - 1) {
                val pair = word[i] to word[i + 1]
                val rank = ranks[pair] ?: continue
                if (rank < bestRank) { bestRank = rank; best = pair }
            }
            val merge = best ?: break
            val merged = ArrayList<String>(word.size)
            var i = 0
            while (i < word.size) {
                if (i < word.size - 1 && word[i] == merge.first && word[i + 1] == merge.second) {
                    merged += merge.first + merge.second
                    i += 2
                } else {
                    merged += word[i]
                    i++
                }
            }
            word = merged
        }
        word
    }

    companion object {
        const val BOS = "<|startoftext|>"
        const val EOS = "<|endoftext|>"
        private val WHITESPACE = Regex("\\s+")
        private val TOKEN = Regex("<\\|startoftext\\|>|<\\|endoftext\\|>|'s|'t|'re|'ve|'m|'ll|'d|[\\p{L}]+|[\\p{N}]|[^\\s\\p{L}\\p{N}]+")
    }
}
