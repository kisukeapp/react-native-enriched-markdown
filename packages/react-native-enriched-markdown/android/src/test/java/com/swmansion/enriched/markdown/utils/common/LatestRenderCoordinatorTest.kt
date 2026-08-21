package com.swmansion.enriched.markdown.utils.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.ArrayDeque
import java.util.concurrent.Executor

class LatestRenderCoordinatorTest {
  private class ControlledExecutor : Executor {
    val tasks = ArrayDeque<Runnable>()

    override fun execute(command: Runnable) {
      tasks.add(command)
    }

    fun runNext() = tasks.removeFirst().run()
  }

  @Test
  fun `updates queued before the worker starts coalesce to the latest render`() {
    val worker = ControlledExecutor()
    val main = ControlledExecutor()
    val rendered = mutableListOf<String>()
    val applied = mutableListOf<String>()
    val coordinator = LatestRenderCoordinator(worker) { main.execute(it) }

    coordinator.schedule(render = {
      rendered += "one"
      "one"
    }, apply = applied::add)
    coordinator.schedule(render = {
      rendered += "two"
      "two"
    }, apply = applied::add)
    coordinator.schedule(render = {
      rendered += "three"
      "three"
    }, apply = applied::add)
    worker.runNext()
    main.runNext()

    assertEquals(listOf("three"), rendered)
    assertEquals(listOf("three"), applied)
  }

  @Test
  fun `a stale completed render cannot apply after a newer request`() {
    val worker = ControlledExecutor()
    val main = ControlledExecutor()
    val applied = mutableListOf<String>()
    val coordinator = LatestRenderCoordinator(worker) { main.execute(it) }

    coordinator.schedule(render = { "old" }, apply = applied::add)
    worker.runNext()
    coordinator.schedule(render = { "new" }, apply = applied::add)
    main.runNext()
    worker.runNext()
    main.runNext()

    assertEquals(listOf("new"), applied)
  }

  @Test
  fun `updates arriving during an expensive render retain only the latest follow-up`() {
    val worker = ControlledExecutor()
    val main = ControlledExecutor()
    val rendered = mutableListOf<String>()
    val applied = mutableListOf<String>()
    val coordinator = LatestRenderCoordinator(worker) { main.execute(it) }

    coordinator.schedule(
      render = {
        rendered += "one"
        coordinator.schedule(render = {
          rendered += "two"
          "two"
        }, apply = applied::add)
        coordinator.schedule(render = {
          rendered += "three"
          "three"
        }, apply = applied::add)
        "one"
      },
      apply = applied::add,
    )
    worker.runNext()
    while (main.tasks.isNotEmpty()) main.runNext()

    assertEquals(listOf("one", "three"), rendered)
    assertEquals(listOf("three"), applied)
  }

  @Test
  fun `invalidate rejects an in-flight result`() {
    val worker = ControlledExecutor()
    val main = ControlledExecutor()
    val applied = mutableListOf<String>()
    val coordinator = LatestRenderCoordinator(worker) { main.execute(it) }

    coordinator.schedule(render = { "old" }, apply = applied::add)
    worker.runNext()
    coordinator.invalidate()
    main.runNext()

    assertEquals(emptyList<String>(), applied)
  }
}
