import { shallowRef } from 'vue'

/** Keep edits intact until the caller's navigation/reload actually succeeds. */
export function createDiscardConfirmation() {
  const prompt = shallowRef(null)
  let resolvePending
  function decide(confirmed) {
    const resolve = resolvePending
    resolvePending = undefined
    prompt.value = null
    resolve?.(confirmed)
  }
  function confirm(kind = 'leave') {
    // A newer navigation supersedes an earlier pending decision.
    decide(false)
    prompt.value = { kind }
    return new Promise(resolve => { resolvePending = resolve })
  }
  return { prompt, confirm, decide }
}
