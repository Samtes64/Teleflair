package com.teleboard.teleboard.keyboard.context

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.*
import java.util.ArrayDeque

/**
 * Ultra-fast, zero-lag Accessibility Service implementing the Dual-Service Isolation Pattern.
 * Runs asynchronously on a dedicated worker scope to inspect the active window's UI topology,
 * extracting sub-contexts (comment, post_creation, dm, search) without degrading IME 60+ FPS performance.
 */
class ContextScannerService : AccessibilityService() {

    private val scannerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var processingJob: Job? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // Gatekeeping: Process layout states or focus movements exclusively
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

            val targetPackage = event.packageName?.toString() ?: ""

            // Drop any parsing job currently executing to prevent overlapping CPU loops
            processingJob?.cancel()

            processingJob = scannerScope.launch {
                // Debounce threshold (120ms) allows target app animations and fragments to settle
                delay(120)

                val rootNode = try {
                    rootInActiveWindow
                } catch (_: Exception) {
                    null
                } ?: return@launch

                val evaluatedSubContext = executeScreenStructureAnalysis(rootNode)

                // Dispatch payload across local in-process memory bus instantly
                val dispatchIntent = Intent(ACTION_CONTEXT_MUTATION).apply {
                    putExtra(EXTRA_PACKAGE_NAME, targetPackage)
                    putExtra(EXTRA_SUB_CONTEXT, evaluatedSubContext)
                }
                LocalBroadcastManager.getInstance(this@ContextScannerService).sendBroadcast(dispatchIntent)
            }
        }
    }

    /**
     * Non-recursive iterative Breadth-First Search (BFS) for scanning active View hierarchy.
     * Prevents deep call-stack recursion and limits traversal to 150 nodes to avoid CPU cache thrashing.
     */
    private fun executeScreenStructureAnalysis(root: AccessibilityNodeInfo): String {
        val nodeQueue = ArrayDeque<AccessibilityNodeInfo>()
        nodeQueue.add(root)

        var iterationsChecked = 0
        val maximumNodeDeepSearchLimit = 150

        while (nodeQueue.isNotEmpty() && iterationsChecked < maximumNodeDeepSearchLimit) {
            val currentNode = nodeQueue.removeFirst() ?: continue
            iterationsChecked++

            // Target Extraction 1: Check Hint String Properties
            val rawHint = currentNode.hintText?.toString()?.lowercase() ?: ""
            if (rawHint.isNotEmpty()) {
                when {
                    rawHint.contains("comment") || rawHint.contains("reply") || rawHint.contains("say something") -> return "comment"
                    rawHint.contains("message") || rawHint.contains("dm") || rawHint.contains("chat") || rawHint.contains("send a message") -> return "dm"
                    rawHint.contains("search") || rawHint.contains("find") -> return "search"
                    rawHint.contains("post") || rawHint.contains("share something") || rawHint.contains("what's on your mind") || rawHint.contains("whats happening") -> return "post_creation"
                }
            }

            // Target Extraction 2: Explicit Target View IDs (App-specific indicators)
            val resourceId = currentNode.viewIdResourceName?.lowercase() ?: ""
            if (resourceId.isNotEmpty()) {
                when {
                    // Instagram & Threads
                    resourceId.contains("comment_text_view") || resourceId.contains("row_feed_comment") || resourceId.contains("layout_comment_thread") -> return "comment"
                    resourceId.contains("direct_text_input") || resourceId.contains("row_thread_composer") -> return "dm"
                    resourceId.contains("caption_text_view") -> return "post_creation"

                    // LinkedIn
                    resourceId.contains("comment_input_box") || resourceId.contains("comment_box") -> return "comment"
                    resourceId.contains("messaging_input") -> return "dm"
                    resourceId.contains("share_post") || resourceId.contains("feed_composer") -> return "post_creation"

                    // Twitter / X
                    resourceId.contains("tweet_text") || resourceId.contains("composer_write") -> return "post_creation"
                    resourceId.contains("reply_composer") -> return "comment"

                    // TikTok
                    resourceId.contains("comment_edit_text") || resourceId.contains("et_comment") -> return "comment"

                    // YouTube
                    resourceId.contains("comment_edit_text") || resourceId.contains("comment_input") -> return "comment"
                }
            }

            // Safe collection tracking of nested children layout branches
            val childCount = currentNode.childCount
            for (i in 0 until childCount) {
                val child = try {
                    currentNode.getChild(i)
                } catch (_: Exception) {
                    null
                }
                if (child != null) {
                    nodeQueue.add(child)
                }
            }
        }

        return "unknown"
    }

    override fun onInterrupt() {
        processingJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        scannerScope.cancel()
    }

    companion object {
        const val ACTION_CONTEXT_MUTATION = "com.teleboard.teleboard.ACTION_CONTEXT_MUTATION"
        const val EXTRA_PACKAGE_NAME = "EXTRA_PACKAGE_NAME"
        const val EXTRA_SUB_CONTEXT = "EXTRA_SUB_CONTEXT"
    }
}
