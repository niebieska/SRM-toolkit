import { ref, computed } from 'vue'

function birthDateParts(pesel) {
  if (typeof pesel !== 'string' || pesel.length !== 11 || !/^\d{11}$/.test(pesel)) return null
  const year = parseInt(pesel.substring(0, 2), 10)
  let month = parseInt(pesel.substring(2, 4), 10)
  const day = parseInt(pesel.substring(4, 6), 10)

  let century
  if (month >= 81 && month <= 92) {
    month -= 80
    century = 1800
  } else if (month >= 1 && month <= 12) {
    century = 1900
  } else if (month >= 21 && month <= 32) {
    month -= 20
    century = 2000
  } else if (month >= 41 && month <= 52) {
    month -= 40
    century = 2100
  } else if (month >= 61 && month <= 72) {
    month -= 60
    century = 2200
  } else {
    return null
  }

  const daysInMonth = new Date(century + year, month, 0).getDate()
  if (day < 1 || day > daysInMonth) return null

  return { year: century + year, month, day }
}

function validDateParts({ year, month, day }) {
  if (month < 1 || month > 12 || day < 1) return false
  return day <= new Date(year, month, 0).getDate()
}

function referenceDateParts(referenceDate) {
  if (referenceDate instanceof Date && !Number.isNaN(referenceDate.getTime())) {
    return {
      year: referenceDate.getFullYear(),
      month: referenceDate.getMonth() + 1,
      day: referenceDate.getDate(),
    }
  }
  if (typeof referenceDate === 'string') {
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(referenceDate)
    if (match) {
      const parts = { year: Number(match[1]), month: Number(match[2]), day: Number(match[3]) }
      if (validDateParts(parts)) return parts
    }
  }
  return null
}

export function isAdultOnDate(pesel, referenceDate) {
  const birth = birthDateParts(pesel)
  const reference = referenceDateParts(referenceDate)
  if (!birth || !reference) return false

  let age = reference.year - birth.year
  if (reference.month < birth.month || (reference.month === birth.month && reference.day < birth.day)) age--
  return age >= 18
}

export function usePesel() {
  const pesel = ref('')

  const gender = computed(() => {
    if (pesel.value.length !== 11) return null
    const genderDigit = parseInt(pesel.value[9])
    return genderDigit % 2 === 0 ? 'female' : 'male'
  })

  const isAdult = computed(() => isAdultOnDate(pesel.value, new Date()))

  function detectAge() {
    // Trigger reactivity — isAdult is already computed
    return isAdult.value
  }

  return { pesel, isAdult, gender, detectAge }
}
