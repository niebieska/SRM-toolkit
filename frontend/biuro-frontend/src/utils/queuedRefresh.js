export function createQueuedRefresh(task) {
  let activePromise = null
  let queued = false
  let generation = 0

  return function refresh() {
    generation += 1
    queued = true
    if (activePromise) return activePromise

    activePromise = (async () => {
      let failed = false
      let lastError
      try {
        while (queued) {
          queued = false
          const taskGeneration = generation
          try {
            await task(() => taskGeneration === generation)
            failed = false
            lastError = null
          } catch (error) {
            failed = true
            lastError = error
          }
        }
      } finally {
        activePromise = null
      }
      if (failed) throw lastError
    })()

    return activePromise
  }
}
