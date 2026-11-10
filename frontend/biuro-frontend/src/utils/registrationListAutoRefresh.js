export const REGISTRATION_LIST_REFRESH_INTERVAL_MS = 5 * 60 * 1000

export function setupRegistrationListAutoRefresh(refresh, {
  documentRef = document,
  windowRef = window,
  intervalMs = REGISTRATION_LIST_REFRESH_INTERVAL_MS,
} = {}) {
  let timer = null

  const stopPolling = () => {
    if (timer !== null) {
      windowRef.clearInterval(timer)
      timer = null
    }
  }

  const startPolling = () => {
    if (timer === null) timer = windowRef.setInterval(refresh, intervalMs)
  }

  const handleVisibilityChange = () => {
    if (documentRef.visibilityState === 'visible') {
      refresh()
      startPolling()
    } else {
      stopPolling()
    }
  }

  if (documentRef.visibilityState === 'visible') {
    refresh()
    startPolling()
  }
  documentRef.addEventListener('visibilitychange', handleVisibilityChange)

  return () => {
    documentRef.removeEventListener('visibilitychange', handleVisibilityChange)
    stopPolling()
  }
}
