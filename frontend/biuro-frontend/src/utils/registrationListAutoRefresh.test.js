import test from 'node:test'
import assert from 'node:assert/strict'
import {
  REGISTRATION_LIST_REFRESH_INTERVAL_MS,
  setupRegistrationListAutoRefresh,
} from './registrationListAutoRefresh.js'

function createBrowserStub(initialVisibility = 'visible') {
  const listeners = new Map()
  let intervalDuration
  let nextTimerId = 0
  const intervals = new Map()
  const clearedTimers = []

  const documentRef = {
    visibilityState: initialVisibility,
    addEventListener: (name, callback) => listeners.set(name, callback),
    removeEventListener: (name, callback) => {
      if (listeners.get(name) === callback) listeners.delete(name)
    },
    dispatchVisibilityChange: () => listeners.get('visibilitychange')?.(),
  }
  const windowRef = {
    setInterval: (callback, duration) => {
      intervalDuration = duration
      const timer = ++nextTimerId
      intervals.set(timer, callback)
      return timer
    },
    clearInterval: (timer) => {
      clearedTimers.push(timer)
      intervals.delete(timer)
    },
    tick: () => [...intervals.values()].forEach(callback => callback()),
  }

  return {
    documentRef,
    windowRef,
    listeners,
    intervals,
    get intervalDuration() { return intervalDuration },
    clearedTimers,
  }
}

test('refreshes immediately and schedules five-minute polling for a visible tab', () => {
  const browser = createBrowserStub('visible')
  let refreshCount = 0

  const stop = setupRegistrationListAutoRefresh(() => refreshCount++, browser)
  assert.equal(refreshCount, 1)
  assert.equal(browser.intervalDuration, REGISTRATION_LIST_REFRESH_INTERVAL_MS)
  assert.equal(browser.intervals.size, 1)

  browser.windowRef.tick()
  assert.equal(refreshCount, 2)
  stop()
  assert.equal(browser.intervals.size, 0)
})

test('skips polling while hidden and refreshes immediately when the tab becomes visible', () => {
  const browser = createBrowserStub('hidden')
  let refreshCount = 0

  const stop = setupRegistrationListAutoRefresh(() => refreshCount++, browser)
  assert.equal(refreshCount, 0)
  assert.equal(browser.intervals.size, 0)

  browser.windowRef.tick()
  assert.equal(refreshCount, 0)

  browser.documentRef.visibilityState = 'visible'
  browser.documentRef.dispatchVisibilityChange()
  assert.equal(refreshCount, 1)
  assert.equal(browser.intervals.size, 1)

  browser.windowRef.tick()
  assert.equal(refreshCount, 2)

  browser.documentRef.visibilityState = 'hidden'
  browser.documentRef.dispatchVisibilityChange()
  assert.equal(browser.intervals.size, 0)
  assert.equal(browser.clearedTimers.length, 1)

  browser.documentRef.visibilityState = 'visible'
  browser.documentRef.dispatchVisibilityChange()
  assert.equal(refreshCount, 3)
  assert.equal(browser.intervals.size, 1)
  assert.equal(browser.intervalDuration, REGISTRATION_LIST_REFRESH_INTERVAL_MS)
  stop()
})

test('removes the visibility listener and timer when polling is stopped', () => {
  const browser = createBrowserStub()
  const stop = setupRegistrationListAutoRefresh(() => {}, browser)

  stop()

  assert.equal(browser.listeners.size, 0)
  assert.equal(browser.intervals.size, 0)
  assert.equal(browser.clearedTimers.length, 1)
})
