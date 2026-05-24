import { ref, computed } from 'vue'

export function usePesel() {
  const pesel = ref('')

  const gender = computed(() => {
    if (pesel.value.length !== 11) return null
    const genderDigit = parseInt(pesel.value[9])
    return genderDigit % 2 === 0 ? 'female' : 'male'
  })

  const isAdult = computed(() => {
    if (pesel.value.length !== 11) return false
    const year = parseInt(pesel.value.substring(0, 2))
    let month = parseInt(pesel.value.substring(2, 4))
    const day = parseInt(pesel.value.substring(4, 6))

    let fullYear
    if (month >= 81 && month <= 92) {
      month -= 80
      fullYear = 1800 + year
    } else if (month >= 1 && month <= 12) {
      fullYear = 1900 + year
    } else if (month >= 21 && month <= 32) {
      month -= 20
      fullYear = 2000 + year
    } else if (month >= 41 && month <= 52) {
      month -= 40
      fullYear = 2100 + year
    } else if (month >= 61 && month <= 72) {
      month -= 60
      fullYear = 2200 + year
    } else {
      return false
    }

    const birthDate = new Date(fullYear, month - 1, day)
    const today = new Date()
    let age = today.getFullYear() - birthDate.getFullYear()
    const m = today.getMonth() - birthDate.getMonth()
    if (m < 0 || (m === 0 && today.getDate() < birthDate.getDate())) {
      age--
    }
    return age >= 18
  })

  function detectAge() {
    // Trigger reactivity — isAdult is already computed
    return isAdult.value
  }

  return { pesel, isAdult, gender, detectAge }
}
