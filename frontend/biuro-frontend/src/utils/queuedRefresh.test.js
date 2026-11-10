import test from 'node:test'
import assert from 'node:assert/strict'
import { createQueuedRefresh } from './queuedRefresh.js'

function deferred() {
  let resolve
  const promise = new Promise((done) => { resolve = done })
  return { promise, resolve }
}

test('coalesces overlapping refreshes and runs once with the latest state', async () => {
  const firstRequest = deferred()
  const latestRequest = deferred()
  let filter = 'initial'
  let activeTasks = 0
  let maxActiveTasks = 0
  const observedFilters = []
  const currentChecks = []
  let taskCount = 0

  const refresh = createQueuedRefresh(async (isCurrent) => {
    taskCount += 1
    const taskNumber = taskCount
    activeTasks += 1
    maxActiveTasks = Math.max(maxActiveTasks, activeTasks)
    observedFilters.push(filter)
    if (taskNumber === 1) {
      await firstRequest.promise
      currentChecks.push(isCurrent())
    } else {
      await latestRequest.promise
      currentChecks.push(isCurrent())
    }
    activeTasks -= 1
  })

  const initialRefresh = refresh()
  filter = 'latest'
  const secondRefresh = refresh()
  const thirdRefresh = refresh()

  assert.equal(taskCount, 1)
  firstRequest.resolve()
  await new Promise((resolve) => setImmediate(resolve))

  assert.equal(taskCount, 2)
  assert.deepEqual(observedFilters, ['initial', 'latest'])
  assert.deepEqual(currentChecks, [false])
  latestRequest.resolve()
  await Promise.all([initialRefresh, secondRefresh, thirdRefresh])

  assert.deepEqual(currentChecks, [false, true])
  assert.equal(maxActiveTasks, 1)
})

test('a successful queued refresh supersedes an earlier task failure', async () => {
  const followUp = deferred()
  let taskCount = 0
  const refresh = createQueuedRefresh(async () => {
    taskCount += 1
    if (taskCount === 1) throw new Error('first refresh failed')
    await followUp.promise
  })

  const firstRefresh = refresh()
  const queuedRefresh = refresh()
  assert.equal(queuedRefresh, firstRefresh)
  await new Promise((resolve) => setImmediate(resolve))

  assert.equal(taskCount, 2)
  followUp.resolve()
  await Promise.all([firstRefresh, queuedRefresh])
})
