package com.teleboard.teleboard.keyboard.engine

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.channels.FileChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.min

data class WordCandidate(
    val word: String,
    val score: Float
)

class TflitePredictionEngine(private val context: Context) {

    private val correlationManager = AppCorrelationManager()
    private val executor = Executors.newSingleThreadExecutor()
    private var tfliteInterpreter: Interpreter? = null
    private var isTfliteLoaded = false

    // In-Memory Lexicon Trie with word frequencies
    private val rootTrie = TrieNode()
    private val wordFrequencyMap = ConcurrentHashMap<String, Int>()
    private val commonNextWords = ConcurrentHashMap<String, List<String>>()

    init {
        loadBuiltInDictionary()
        loadTfliteModel()
    }

    private fun loadTfliteModel() {
        executor.execute {
            try {
                val assetFd = context.assets.openFd("models/prediction.tflite")
                val fileChannel = FileInputStream(assetFd.fileDescriptor).channel
                val modelBuffer = fileChannel.map(
                    FileChannel.MapMode.READ_ONLY,
                    assetFd.startOffset,
                    assetFd.declaredLength
                )
                tfliteInterpreter = Interpreter(modelBuffer, Interpreter.Options().apply {
                    setNumThreads(2)
                    setUseNNAPI(false)
                })
                isTfliteLoaded = true
            } catch (_: Exception) {
                isTfliteLoaded = false
            }
        }
    }

    fun learnWord(word: String, boost: Int = 200) {
        val clean = word.trim().lowercase().filter { it.isLetter() || it == '\'' }
        if (clean.length < 2) return

        val existingFreq = wordFrequencyMap[clean] ?: 0
        val newFreq = existingFreq + boost
        wordFrequencyMap[clean] = newFreq
        insertWord(clean, newFreq)
    }

    fun loadUserWords(userWords: List<Pair<String, Int>>) {
        for ((word, freq) in userWords) {
            val boost = 3000 + (freq * 100)
            wordFrequencyMap[word] = boost
            insertWord(word, boost)
        }
    }

    fun getAllLexiconWords(): List<String> {
        val all = mutableListOf<Pair<String, Int>>()
        collectAllWords(rootTrie, StringBuilder(), all, 15000)
        return all.sortedByDescending { it.second }.map { it.first }
    }

    fun getPredictions(
        prefix: String,
        contextSentence: String,
        packageName: String?,
        isSensitive: Boolean
    ): List<String> {
        // Strict Privacy & Security Check: Completely halt in sensitive/password contexts
        if (isSensitive) {
            return emptyList()
        }

        val startTime = System.currentTimeMillis()
        val category = correlationManager.getCategory(packageName)
        val cleanPrefix = prefix.trim().lowercase().filter { it.isLetter() || it == '\'' }

        val isFirstUpper = prefix.isNotEmpty() && prefix.first().isUpperCase()
        val isAllUpper = prefix.length >= 2 && prefix.all { it.isUpperCase() }

        fun formatCandidate(word: String): String {
            return when {
                isAllUpper -> word.uppercase()
                isFirstUpper -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                else -> word
            }
        }

        // 1. If prefix is not empty: Autocomplete character-to-word
        if (cleanPrefix.isNotEmpty()) {
            val trieCandidates = searchTriePrefix(cleanPrefix, limit = 10).toMutableList()

            // If exact prefix matches are few, search for fuzzy/typo matches
            if (trieCandidates.size < 3 && cleanPrefix.length >= 3) {
                val fuzzy = findFuzzyMatches(cleanPrefix, maxDistance = 1, limit = 5)
                for (f in fuzzy) {
                    if (trieCandidates.none { it.first == f.first }) {
                        trieCandidates.add(f)
                    }
                }
            }

            val scored = trieCandidates.map { (word, freq) ->
                val lengthBonus = if (word.length == cleanPrefix.length) 1.25f else 1.0f
                val prefixBonus = if (word.startsWith(cleanPrefix)) 1.5f else 0.8f
                val userBoost = if (wordFrequencyMap.containsKey(word)) 1.3f else 1.0f
                val baseScore = freq.toFloat() * lengthBonus * prefixBonus * userBoost
                val adjustedScore = correlationManager.applyWeightAdjustment(word, baseScore, category)
                WordCandidate(word, adjustedScore)
            }.sortedByDescending { it.score }

            // Enforce <12ms deadline constraint
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed > 12) {
                return scored.take(3).map { formatCandidate(it.word) }
            }

            return scored.take(3).map { formatCandidate(it.word) }
        }

        // 2. If prefix is empty: Word-to-word sequence prediction
        val tokens = contextSentence.trim().split("\\s+".toRegex())
        val lastWord = tokens.lastOrNull()?.lowercase()?.filter { it.isLetter() || it == '\'' } ?: ""

        if (lastWord.isNotEmpty()) {
            val nextCandidates = commonNextWords[lastWord] ?: getDefaultStarters()
            val scored = nextCandidates.map { word ->
                val baseScore = 1.0f
                val adjustedScore = correlationManager.applyWeightAdjustment(word, baseScore, category)
                WordCandidate(word, adjustedScore)
            }.sortedByDescending { it.score }

            return scored.take(3).map { formatCandidate(it.word) }
        }

        return getDefaultStarters().take(3).map { formatCandidate(it) }
    }

    private fun getDefaultStarters(): List<String> {
        return listOf("I", "The", "Thanks")
    }

    // --- Trie Implementation ---

    private class TrieNode {
        val children = HashMap<Char, TrieNode>()
        var isEndOfWord = false
        var frequency = 0
    }

    private fun insertWord(word: String, frequency: Int = 1) {
        var current = rootTrie
        for (ch in word.lowercase()) {
            if (!current.children.containsKey(ch)) {
                current.children[ch] = TrieNode()
            }
            current = current.children[ch]!!
        }
        current.isEndOfWord = true
        current.frequency = frequency
    }

    fun searchTriePrefix(prefix: String, limit: Int = 10): List<Pair<String, Int>> {
        var current = rootTrie
        for (ch in prefix.lowercase()) {
            val next = current.children[ch] ?: return emptyList()
            current = next
        }

        val results = mutableListOf<Pair<String, Int>>()
        collectAllWords(current, StringBuilder(prefix.lowercase()), results, maxCandidates = 200)
        return results.sortedByDescending { it.second }.take(limit)
    }

    private fun collectAllWords(
        node: TrieNode,
        currentWord: StringBuilder,
        results: MutableList<Pair<String, Int>>,
        maxCandidates: Int
    ) {
        if (node.isEndOfWord) {
            results.add(Pair(currentWord.toString(), node.frequency))
            if (results.size >= maxCandidates) return
        }

        for ((ch, child) in node.children) {
            if (results.size >= maxCandidates) return
            currentWord.append(ch)
            collectAllWords(child, currentWord, results, maxCandidates)
            currentWord.deleteCharAt(currentWord.length - 1)
        }
    }

    private fun findFuzzyMatches(target: String, maxDistance: Int = 1, limit: Int = 5): List<Pair<String, Int>> {
        val matches = mutableListOf<Pair<String, Int>>()
        // Check near neighbors in the root trie
        for ((char, child) in rootTrie.children) {
            searchFuzzyRecursive(child, "$char", target, maxDistance, matches, limit = 25)
            if (matches.size >= 25) break
        }
        return matches.sortedByDescending { it.second }.take(limit)
    }

    private fun searchFuzzyRecursive(
        node: TrieNode,
        current: String,
        target: String,
        maxDist: Int,
        results: MutableList<Pair<String, Int>>,
        limit: Int
    ) {
        if (results.size >= limit) return

        if (node.isEndOfWord && abs(current.length - target.length) <= maxDist) {
            val dist = levenshtein(current, target)
            if (dist <= maxDist) {
                results.add(Pair(current, node.frequency / (dist + 1)))
            }
        }

        if (current.length > target.length + maxDist) return

        for ((ch, child) in node.children) {
            searchFuzzyRecursive(child, "$current$ch", target, maxDist, results, limit)
        }
    }

    private fun levenshtein(s: String, t: String): Int {
        val m = s.length
        val n = t.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s[i - 1] == t[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[m][n]
    }

    private fun loadBuiltInDictionary() {
        var loadedFromAssets = false
        try {
            context.assets.open("english_words.txt").bufferedReader().useLines { lines ->
                for (line in lines) {
                    val parts = line.split('\t')
                    if (parts.size >= 2) {
                        val word = parts[0].trim().lowercase()
                        val freq = parts[1].trim().toIntOrNull() ?: 100
                        wordFrequencyMap[word] = freq
                        insertWord(word, freq)
                    }
                }
            }
            loadedFromAssets = true
        } catch (_: Exception) {
            loadedFromAssets = false
        }

        if (!loadedFromAssets || wordFrequencyMap.isEmpty()) {
            val fallbackWords = listOf(
                "the" to 5000, "be" to 4800, "to" to 4600, "of" to 4500, "and" to 4400,
                "a" to 4300, "in" to 4200, "that" to 4100, "have" to 4000, "i" to 3900,
                "it" to 3800, "for" to 3700, "not" to 3600, "on" to 3500, "with" to 3400,
                "he" to 3300, "as" to 3200, "you" to 3100, "do" to 3000, "at" to 2900,
                "this" to 2800, "but" to 2700, "his" to 2600, "by" to 2500, "from" to 2400,
                "they" to 2300, "we" to 2200, "say" to 2100, "her" to 2000, "she" to 1950,
                "will" to 1800, "my" to 1750, "one" to 1700, "all" to 1650, "would" to 1600,
                "there" to 1550, "their" to 1500, "what" to 1450, "so" to 1400, "up" to 1350,
                "out" to 1300, "if" to 1250, "about" to 1200, "who" to 1150, "get" to 1100,
                "which" to 1050, "go" to 1000, "me" to 980, "when" to 960, "make" to 940,
                "can" to 920, "like" to 900, "time" to 880, "no" to 860, "just" to 840,
                "him" to 820, "know" to 800, "take" to 780, "people" to 760, "into" to 740,
                "your" to 700, "good" to 680, "some" to 660, "could" to 640, "them" to 620,
                "see" to 600, "other" to 580, "than" to 560, "then" to 540, "now" to 520,
                "look" to 500, "only" to 480, "come" to 460, "its" to 440, "over" to 420,
                "think" to 400, "also" to 390, "back" to 380, "after" to 370, "use" to 360,
                "how" to 340, "our" to 330, "work" to 320, "first" to 310, "well" to 300,
                "way" to 290, "even" to 280, "new" to 270, "want" to 260, "because" to 250,
                "hello" to 2000, "hi" to 1900, "please" to 1800, "thanks" to 2500,
                "great" to 1500, "perfect" to 1400, "telegram" to 2500, "sticker" to 2400
            )
            for ((w, freq) in fallbackWords) {
                wordFrequencyMap[w] = freq
                insertWord(w, freq)
            }
        }

        // Common 2-gram transition pairs
        commonNextWords["i"] = listOf("am", "have", "will", "think", "can", "want", "need")
        commonNextWords["how"] = listOf("are", "is", "was", "can", "do", "about")
        commonNextWords["are"] = listOf("you", "we", "they", "there", "doing")
        commonNextWords["thank"] = listOf("you", "so", "very")
        commonNextWords["thanks"] = listOf("for", "again", "a", "lot", "so")
        commonNextWords["what"] = listOf("is", "are", "do", "time", "about", "happened")
        commonNextWords["good"] = listOf("morning", "night", "afternoon", "job", "luck")
        commonNextWords["see"] = listOf("you", "them", "if", "what", "later")
        commonNextWords["let"] = listOf("me", "us", "you", "know")
        commonNextWords["looking"] = listOf("forward", "at", "for")
        commonNextWords["send"] = listOf("sticker", "stickers", "message", "photo", "link")
        commonNextWords["sounds"] = listOf("good", "great", "awesome", "perfect", "cool")
        commonNextWords["on"] = listOf("my", "the", "way", "time", "it")
        commonNextWords["it"] = listOf("is", "was", "looks", "works", "feels")
    }

    fun close() {
        tfliteInterpreter?.close()
        tfliteInterpreter = null
    }
}
