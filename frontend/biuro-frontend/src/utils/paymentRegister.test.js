import test from 'node:test'
import assert from 'node:assert/strict'
import { hasPaymentHistory, isPaymentEligible } from './demoPayments.js'

test('keeps a financial record visible after its registration leaves accepted status', () => {
  const rejectedWithPayment = { status: 'REJECTED', fee: 500, payments: [{ id: 1, amount: 100 }], discount: 0 }
  const waitlistedWithDiscount = { status: 'WAITLIST', fee: 500, payments: [], discount: 200 }

  assert.equal(isPaymentEligible(rejectedWithPayment), false)
  assert.equal(hasPaymentHistory(rejectedWithPayment), true)
  assert.equal(hasPaymentHistory(waitlistedWithDiscount), true)
})

test('does not retain rows without a fee or a persisted financial entry', () => {
  assert.equal(hasPaymentHistory({ status: 'REJECTED', fee: 500, payments: [], discount: 0 }), false)
  assert.equal(hasPaymentHistory({ status: 'REJECTED', fee: 0, payments: [{ id: 1, amount: 100 }], discount: 0 }), false)
})
