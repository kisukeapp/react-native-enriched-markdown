package com.swmansion.enriched.markdown.utils.common

import java.util.concurrent.Executor

/**
 * Serial latest-wins render pump.
 *
 * At most one expensive parse is running and one newer request is retained.
 * Superseded requests are discarded before parsing, and a result may apply
 * only while it is still the newest generation.
 */
internal class LatestRenderCoordinator(
  private val executor: Executor,
  private val postToMain: ((() -> Unit) -> Unit),
) {
  private data class Request(
    val generation: Long,
    val render: () -> Any?,
    val apply: (Any) -> Unit,
  )

  private val lock = Any()
  private var generation = 0L
  private var pending: Request? = null
  private var workerActive = false

  fun <T : Any> schedule(
    render: () -> T?,
    apply: (T) -> Unit,
  ) {
    var startWorker = false
    synchronized(lock) {
      generation += 1
      @Suppress("UNCHECKED_CAST")
      pending = Request(generation, render, apply as (Any) -> Unit)
      if (!workerActive) {
        workerActive = true
        startWorker = true
      }
    }
    if (startWorker) executor.execute(::drain)
  }

  fun invalidate() {
    synchronized(lock) {
      generation += 1
      pending = null
    }
  }

  private fun drain() {
    while (true) {
      val request =
        synchronized(lock) {
          val next = pending
          pending = null
          if (next == null) workerActive = false
          next
        } ?: return

      val result = request.render() ?: continue
      postToMain {
        val current = synchronized(lock) { request.generation == generation }
        if (current) request.apply(result)
      }
    }
  }
}
