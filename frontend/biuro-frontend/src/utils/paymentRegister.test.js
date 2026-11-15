import test from 'node:test'
import assert from 'node:assert/strict'
import { hasPaymentHistory, isPaymentEligible, refundableTotal } from './demoPayments.js'

test('keeps rejected registrations visible only when they have payment history', () => {
  const rejectedWithPayment = { status: 'REJECTED', fee: 500, payments: [{ id: 1, amount: 100 }], discount: 0 }
  const waitlistedWithDiscount = { status: 'WAITLIST', fee: 500, payments: [], discount: 200 }

  assert.equal(isPaymentEligible(rejectedWithPayment), false)
  assert.equal(hasPaymentHistory(rejectedWithPayment), true)
  assert.equal(hasPaymentHistory(waitlistedWithDiscount), false)
  assert.equal(hasPaymentHistory({ status: 'NEW', fee: 500, payments: [{ id: 2, amount: 100 }], discount: 0 }), false)
})

test('limits accepted refunds to overpayment and allows rejected refunds up to net received', () => {
  const payment = { entryType: 'PAYMENT', amount: 1200 }
  const refund = { entryType: 'REFUND', amount: 100 }

  assert.equal(refundableTotal({ status: 'ACCEPTED', fee: 1000, discount: 100, payments: [payment] }), 300)
  assert.equal(refundableTotal({ status: 'ACCEPTED', fee: 1000, discount: 100, payments: [{ ...payment, amount: 800 }] }), 0)
  assert.equal(refundableTotal({ status: 'REJECTED', fee: 1000, payments: [payment, refund] }), 1100)
  assert.equal(refundableTotal({ status: 'WAITLIST', fee: 1000, payments: [payment] }), 0)
})

test('does not retain rows without a fee or a persisted financial entry', () => {
  assert.equal(hasPaymentHistory({ status: 'REJECTED', fee: 500, payments: [], discount: 0 }), false)
  assert.equal(hasPaymentHistory({ status: 'REJECTED', fee: 0, payments: [{ id: 1, amount: 100 }], discount: 0 }), false)
})
