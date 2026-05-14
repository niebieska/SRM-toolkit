/**
 * PESEL: 11 digits, correct checksum
 * Weights: [1, 3, 7, 9, 1, 3, 7, 9, 1, 3, 1], sum mod 10 === 0
 */
export function validatePesel(value) {
  if (!/^\d{11}$/.test(value)) return false
  const weights = [1, 3, 7, 9, 1, 3, 7, 9, 1, 3, 1]
  const sum = value.split('').reduce((acc, digit, i) => acc + parseInt(digit) * weights[i], 0)
  return sum % 10 === 0
}

/**
 * Email: standard format check
 */
export function validateEmail(value) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)
}

/**
 * Phone: strip leading +, strip spaces/dashes, must leave exactly 9 digits
 * Value format: +{prefix}{9digits} e.g. +48123456789
 */
export function validatePhone(value) {
  if (!value) return false
  let v = value.trim()
  if (v.startsWith('+')) v = v.slice(1)
  v = v.replace(/[\s\-]/g, '')
  if (!/^\d+$/.test(v)) return false
  if (v.length < 9) return false
  const digits = v.slice(-9)
  return /^\d{9}$/.test(digits)
}

/**
 * Polish postal code: must match /^\d{2}-\d{3}$/
 */
export function validatePostalCode(value) {
  return /^\d{2}-\d{3}$/.test(value)
}
