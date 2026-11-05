import test from 'node:test'
import assert from 'node:assert/strict'
import { applyLengthErrors, FORM_FIELD_LIMITS } from '../../utils/formFieldLimits.js'

test('frontend field limits match the agreed user-facing values', () => {
  assert.deepEqual(FORM_FIELD_LIMITS, {
    firstName: 60,
    lastName: 80,
    parentName: 60,
    birthSurname: 80,
    email: 254,
    parentNames: 200,
    street: 100,
    houseNumber: 20,
    city: 80,
    iceRelationOther: 80,
    healthDetail: 500,
    staffAdditionalInfo: 500,
    certificateDetails: 500,
  })
})

test('field at maximum length passes and one character over gets an error without changing input', () => {
  const exact = 'x'.repeat(FORM_FIELD_LIMITS.firstName)
  const over = exact + 'ą'
  const values = { firstName: exact, lastName: over }
  const errors = {}

  applyLengthErrors(errors, values, {
    firstName: { max: FORM_FIELD_LIMITS.firstName, label: 'Imię' },
    lastName: { max: FORM_FIELD_LIMITS.firstName, label: 'Nazwisko' },
  })

  assert.equal(errors.firstName, undefined)
  assert.equal(errors.lastName, 'Nazwisko może mieć maksymalnie 60 znaków.')
  assert.equal(values.lastName, over)
})

test('optional empty values and values outside constraints are left alone', () => {
  const errors = { existing: 'Błąd' }
  applyLengthErrors(errors, { optional: '', unrelated: 'x'.repeat(10) }, {
    optional: { max: 5, label: 'Opis' },
  })
  assert.deepEqual(errors, { existing: 'Błąd' })
})
