export const FORM_FIELD_LIMITS = Object.freeze({
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

export function applyLengthErrors(errors, values, constraints) {
  Object.entries(constraints).forEach(([field, { max, label }]) => {
    const value = values[field]
    if (typeof value === 'string' && value.length > max) {
      errors[field] = `${label} może mieć maksymalnie ${max} znaków.`
    }
  })
}
