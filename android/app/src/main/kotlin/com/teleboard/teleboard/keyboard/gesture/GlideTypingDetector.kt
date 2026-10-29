package com.teleboard.teleboard.keyboard.gesture

import android.graphics.PointF
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.PI

data class GesturePoint(
    val x: Float,
    val y: Float,
    val time: Long
)

class GlideTypingDetector(
    private val keyCentroids: Map<Char, PointF>,
    initialTrieWords: List<String> = emptyList()
) {

    private val words = CopyOnWriteArrayList<String>()
    private val rawPoints = mutableListOf<GesturePoint>()
    private var isGliding = false
    private var totalDistanceTraversed = 0f

    init {
        setLexicon(initialTrieWords)
    }

    fun setLexicon(lexicon: List<String>) {
        words.clear()
        val distinct = lexicon.map { it.trim().lowercase() }.filter { it.length >= 2 }.distinct()
        words.addAll(distinct)
    }

    fun addWord(word: String) {
        val clean = word.trim().lowercase()
        if (clean.length >= 2 && !words.contains(clean)) {
            words.add(0, clean) // Insert at top priority
        }
    }

    fun onTouchDown(x: Float, y: Float) {
        rawPoints.clear()
        rawPoints.add(GesturePoint(x, y, System.currentTimeMillis()))
        isGliding = false
        totalDistanceTraversed = 0f
    }

    fun onTouchMove(x: Float, y: Float): Boolean {
        val now = System.currentTimeMillis()
        val last = rawPoints.lastOrNull()
        if (last != null) {
            val dist = hypot(x - last.x, y - last.y)
            if (dist > 5f) { // Precise 5px sampling
                rawPoints.add(GesturePoint(x, y, now))
                totalDistanceTraversed += dist
                if (totalDistanceTraversed > 30f && rawPoints.size >= 3) {
                    isGliding = true
                }
            }
        }
        return isGliding
    }

    fun onTouchUp(): String? {
        val candidates = onTouchUpCandidates()
        return candidates.firstOrNull()
    }

    fun onTouchUpCandidates(): List<String> {
        if (!isGliding || rawPoints.size < 3) {
            rawPoints.clear()
            isGliding = false
            return emptyList()
        }

        val smoothedPath = smoothPathCatmullRom(rawPoints)
        val inflections = detectInflectionPoints(smoothedPath)
        val recognizedWords = matchPathToLexiconCandidates(smoothedPath, inflections)

        rawPoints.clear()
        isGliding = false
        totalDistanceTraversed = 0f
        return recognizedWords
    }

    fun clear() {
        rawPoints.clear()
        isGliding = false
        totalDistanceTraversed = 0f
    }

    // --- Catmull-Rom Spline Smoothing ---

    private fun smoothPathCatmullRom(points: List<GesturePoint>): List<PointF> {
        if (points.size < 4) return points.map { PointF(it.x, it.y) }

        val smoothed = mutableListOf<PointF>()
        smoothed.add(PointF(points.first().x, points.first().y))

        for (i in 0 until points.size - 3) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val p2 = points[i + 2]
            val p3 = points[i + 3]

            for (step in 1..4) {
                val t = step / 5f
                val t2 = t * t
                val t3 = t2 * t

                val x = 0.5f * ((2 * p1.x) +
                        (-p0.x + p2.x) * t +
                        (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 +
                        (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3)

                val y = 0.5f * ((2 * p1.y) +
                        (-p0.y + p2.y) * t +
                        (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 +
                        (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3)

                smoothed.add(PointF(x, y))
            }
        }

        smoothed.add(PointF(points.last().x, points.last().y))
        return smoothed
    }

    // --- Inflection & Slow-down Point Detection ---

    private fun detectInflectionPoints(path: List<PointF>): List<Char> {
        if (path.isEmpty() || keyCentroids.isEmpty()) return emptyList()

        val keysHit = mutableListOf<Char>()
        // 1. First key
        closestKey(path.first())?.let { keysHit.add(it) }

        // 2. Turn / inflection points
        for (i in 2 until path.size - 2) {
            val pPrev = path[i - 2]
            val pCurr = path[i]
            val pNext = path[i + 2]

            val angle1 = atan2(pCurr.y - pPrev.y, pCurr.x - pPrev.x)
            val angle2 = atan2(pNext.y - pCurr.y, pNext.x - pCurr.x)
            var dAngle = abs(angle2 - angle1)
            if (dAngle > PI.toFloat()) dAngle = 2f * PI.toFloat() - dAngle

            // Sharp turn detected (inflection over a key)
            if (dAngle > 0.65f) {
                closestKey(pCurr)?.let { key ->
                    if (keysHit.isEmpty() || keysHit.last() != key) {
                        keysHit.add(key)
                    }
                }
            }
        }

        // 3. Last key
        closestKey(path.last())?.let { key ->
            if (keysHit.isEmpty() || keysHit.last() != key) {
                keysHit.add(key)
            }
        }

        return keysHit
    }

    private fun closestKey(point: PointF): Char? {
        var minDistance = Float.MAX_VALUE
        var bestChar: Char? = null
        for ((char, centroid) in keyCentroids) {
            val d = hypot(point.x - centroid.x, point.y - centroid.y)
            if (d < minDistance) {
                minDistance = d
                bestChar = char
            }
        }
        return if (minDistance < 140f) bestChar else null
    }

    private fun nearbyKeys(point: PointF, radiusPx: Float = 95f): List<Char> {
        val nearby = mutableListOf<Pair<Char, Float>>()
        for ((char, centroid) in keyCentroids) {
            val d = hypot(point.x - centroid.x, point.y - centroid.y)
            if (d <= radiusPx) {
                nearby.add(Pair(char, d))
            }
        }
        if (nearby.isEmpty()) {
            closestKey(point)?.let { return listOf(it) }
        }
        return nearby.sortedBy { it.second }.map { it.first }
    }

    // --- Trajectory Lexicon Matching & Scoring ---

    private fun matchPathToLexiconCandidates(path: List<PointF>, inflectionKeys: List<Char>): List<String> {
        if (path.isEmpty() || words.isEmpty()) return emptyList()

        val startCandidates = nearbyKeys(path.first(), radiusPx = 110f)
        val endCandidates = nearbyKeys(path.last(), radiusPx = 110f)

        if (startCandidates.isEmpty() || endCandidates.isEmpty()) return emptyList()

        // Filter candidate words matching start & end letter neighborhoods
        var candidateWords = words.filter { word ->
            val clean = word.filter { it.isLetter() }
            clean.length >= 2 &&
            startCandidates.any { clean.startsWith(it, ignoreCase = true) } &&
            endCandidates.any { clean.endsWith(it, ignoreCase = true) }
        }

        // Relax end candidate condition if exact lift-off is slightly outside boundary
        if (candidateWords.isEmpty()) {
            val relaxedEndCandidates = nearbyKeys(path.last(), radiusPx = 160f)
            candidateWords = words.filter { word ->
                val clean = word.filter { it.isLetter() }
                clean.length >= 2 &&
                startCandidates.any { clean.startsWith(it, ignoreCase = true) } &&
                relaxedEndCandidates.any { clean.endsWith(it, ignoreCase = true) }
            }
        }

        if (candidateWords.isEmpty()) return emptyList()

        // Resample touch path to 20 normalized equidistant points along curve
        val resampledTouchPath = resamplePath(path, 20)

        data class ScoredWord(val word: String, val score: Float)
        val scoredList = mutableListOf<ScoredWord>()

        for (word in candidateWords) {
            val lettersOnly = word.filter { it.isLetter() }
            val idealCentroids = lettersOnly.mapNotNull { keyCentroids[it.lowercaseChar()] }
            if (idealCentroids.size != lettersOnly.length || idealCentroids.size < 2) continue

            val resampledIdealPath = resamplePath(idealCentroids, 20)
            val dtwDistance = computeAveragePathDistance(resampledTouchPath, resampledIdealPath)

            // Calculate inflection bonus
            var inflectionHits = 0
            var lastIdx = -1
            for (ch in inflectionKeys) {
                val idx = lettersOnly.indexOf(ch, lastIdx + 1)
                if (idx > lastIdx) {
                    inflectionHits++
                    lastIdx = idx
                }
            }

            val inflectionBonus = 1.0f + (inflectionHits * 0.2f)
            val lengthProximity = 1.0f / (1.0f + abs(lettersOnly.length - inflectionKeys.size) * 0.08f)
            val finalScore = (1000f / (1f + dtwDistance)) * inflectionBonus * lengthProximity

            scoredList.add(ScoredWord(word, finalScore))
        }

        return scoredList
            .sortedByDescending { it.score }
            .take(3)
            .map { it.word }
    }

    // Arc-length equidistant polyline resampling
    private fun resamplePath(points: List<PointF>, targetCount: Int): List<PointF> {
        if (points.isEmpty()) return emptyList()
        if (points.size == 1) return List(targetCount) { points[0] }

        val segmentLengths = FloatArray(points.size - 1)
        var totalLength = 0f
        for (i in 0 until points.size - 1) {
            val d = hypot(points[i + 1].x - points[i].x, points[i + 1].y - points[i].y)
            segmentLengths[i] = d
            totalLength += d
        }

        if (totalLength == 0f) {
            return List(targetCount) { points[0] }
        }

        val resampled = ArrayList<PointF>(targetCount)
        resampled.add(points[0])

        val interval = totalLength / (targetCount - 1)
        var currentSegmentIdx = 0
        var segStartDist = 0f

        for (i in 1 until targetCount - 1) {
            val targetDist = i * interval
            while (currentSegmentIdx < segmentLengths.size && segStartDist + segmentLengths[currentSegmentIdx] < targetDist) {
                segStartDist += segmentLengths[currentSegmentIdx]
                currentSegmentIdx++
            }

            if (currentSegmentIdx >= segmentLengths.size) {
                resampled.add(points.last())
            } else {
                val segLen = segmentLengths[currentSegmentIdx]
                val t = if (segLen > 0f) (targetDist - segStartDist) / segLen else 0f
                val p1 = points[currentSegmentIdx]
                val p2 = points[currentSegmentIdx + 1]
                resampled.add(PointF(p1.x + t * (p2.x - p1.x), p1.y + t * (p2.y - p1.y)))
            }
        }

        resampled.add(points.last())
        return resampled
    }

    private fun computeAveragePathDistance(s: List<PointF>, t: List<PointF>): Float {
        val count = min(s.size, t.size)
        if (count == 0) return Float.MAX_VALUE
        var sumDist = 0f
        for (i in 0 until count) {
            sumDist += hypot(s[i].x - t[i].x, s[i].y - t[i].y)
        }
        return sumDist / count
    }
}
