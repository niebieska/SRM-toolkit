// Demonstration-only payment register data. Never sent to or saved by the API.
export function createDemoPaymentRegistrations(turnusCode, pricing = {}) {
  const participantFee = Number(pricing.participantFee ?? 0)
  const partialDiscount = Math.min(200, participantFee)
  const fullDiscount = participantFee
  const staffFees = pricing.staffFees || []
  const staffFee = (role, subrole = null) => {
    const exactFee = staffFees.find(item => item.role === role && (item.subrole ?? null) === subrole)
    const roleFee = subrole ? staffFees.find(item => item.role === role && (item.subrole ?? null) === null) : null
    return Number((exactFee || roleFee)?.amount ?? 0)
  }
  return [
  {
    id: `${turnusCode}-demo-p-001`,
    registrationCode: `REG-P-${turnusCode}-18`,
    firstName: 'Anna',
    lastName: 'Kowalska',
    registrationType: 'PARTICIPANT',
    registrationStatus: 'ACCEPTED',
    fee: participantFee,
    discount: 0,
    payments: [],
    discountHistory: [],
  },
  {
    id: `${turnusCode}-demo-p-002`,
    registrationCode: `REG-P-${turnusCode}-19`,
    firstName: 'Jan',
    lastName: 'Nowak',
    registrationType: 'PARTICIPANT',
    registrationStatus: 'ACCEPTED',
    fee: participantFee,
    discount: partialDiscount,
    payments: [
      { id: `${turnusCode}-demo-pay-002`, date: '2026-10-12', amount: 500, method: 'Przelew', label: 'Zaliczka', note: '' },
    ],
    discountHistory: [{ previousAmount: 0, amount: partialDiscount, changedAt: '2026-10-10 12:20', actor: 'operator.demo', description: 'Przykładowy rabat' }],
  },
  {
    id: `${turnusCode}-demo-p-003`,
    registrationCode: `REG-P-${turnusCode}-20`,
    firstName: 'Maria',
    lastName: 'Wiśniewska',
    registrationType: 'PARTICIPANT',
    registrationStatus: 'ACCEPTED',
    fee: participantFee,
    discount: 0,
    payments: [
      {
        id: `${turnusCode}-demo-pay-003`, date: '2026-10-13', amount: 1200, method: 'Przelew', label: '', note: 'Wpłata całej kwoty',
        history: [{
          previous: { date: '2026-10-13', amount: 1000, method: 'Przelew', label: 'Zaliczka', note: '' },
          current: { date: '2026-10-13', amount: 1200, method: 'Przelew', label: '', note: 'Wpłata całej kwoty' },
          changedAt: '2026-10-14 10:15', actor: 'operator.demo', reason: 'Poprawiono kwotę zgodnie z potwierdzeniem przelewu',
        }],
      },
    ],
    discountHistory: [],
  },
  {
    id: `${turnusCode}-demo-p-004`,
    registrationCode: `REG-P-${turnusCode}-21`,
    firstName: 'Zofia',
    lastName: 'Kaczmarek',
    registrationType: 'PARTICIPANT',
    registrationStatus: 'ACCEPTED',
    fee: participantFee,
    discount: 0,
    payments: [
      { id: `${turnusCode}-demo-pay-006`, date: '2026-10-15', amount: 1300, method: 'Przelew', label: 'Wpłata całej kwoty', note: '' },
    ],
    discountHistory: [],
  },
  {
    id: `${turnusCode}-demo-p-005`,
    registrationCode: `REG-P-${turnusCode}-22`,
    firstName: 'Piotr',
    lastName: 'Kamiński',
    registrationType: 'PARTICIPANT',
    registrationStatus: 'ACCEPTED',
    fee: participantFee,
    discount: fullDiscount,
    payments: [],
    discountHistory: [{ previousAmount: 0, amount: fullDiscount, changedAt: '2026-10-16 09:30', actor: 'operator.demo', description: 'Pełne zwolnienie z opłaty' }],
  },
  {
    id: `${turnusCode}-demo-s-001`,
    registrationCode: `REG-S-${turnusCode}-7`,
    firstName: 'Jakub',
    lastName: 'Zieliński',
    registrationType: 'STAFF',
    registrationStatus: 'ACCEPTED',
    role: 'Animator — kandydat na animatora',
    roleCode: 'animator',
    subroleCode: 'kandydat_na_animatora',
    fee: staffFee('animator', 'kandydat_na_animatora'),
    discount: 0,
    payments: [],
    discountHistory: [],
  },
  {
    id: `${turnusCode}-demo-s-002`,
    registrationCode: `REG-S-${turnusCode}-8`,
    firstName: 'Agata',
    lastName: 'Wójcik',
    registrationType: 'STAFF',
    registrationStatus: 'ACCEPTED',
    role: 'Kucharka',
    roleCode: 'kucharka',
    fee: staffFee('kucharka'),
    discount: 0,
    payments: [
      { id: `${turnusCode}-demo-pay-005`, date: '2026-10-14', amount: 300, method: 'Gotówka', label: '', note: '' },
    ],
    discountHistory: [],
  },
  {
    id: `${turnusCode}-demo-p-006`,
    registrationCode: `REG-P-${turnusCode}-23`,
    firstName: 'Tomasz',
    lastName: 'Lewandowski',
    registrationType: 'PARTICIPANT',
    registrationStatus: 'NEW',
    fee: participantFee,
    discount: 0,
    payments: [],
    discountHistory: [],
  },
  {
    id: `${turnusCode}-demo-s-003`,
    registrationCode: `REG-S-${turnusCode}-9`,
    firstName: 'Ewa',
    lastName: 'Kowalczyk',
    registrationType: 'STAFF',
    registrationStatus: 'ACCEPTED',
    role: 'Kucharka',
    roleCode: 'kucharka',
    fee: 0,
    discount: 0,
    payments: [],
    discountHistory: [],
  },
  ]
}

export function isPaymentEligible(registration) {
  return (registration.status || registration.registrationStatus) === 'ACCEPTED' && Number(registration.fee) > 0
}

export function hasPaymentHistory(registration) {
  return Number(registration.fee) > 0 && (registration.payments?.length > 0 || Number(registration.discount) > 0)
}

export function paymentTotal(registration) {
  const totalCents = registration.payments.reduce((total, payment) => total + Math.round(Number(payment.amount || 0) * 100), 0)
  return totalCents / 100
}

export function paymentBalance(registration) {
  const feeCents = Math.round(Number(registration.fee || 0) * 100)
  const discountCents = Math.round(Number(registration.discount || 0) * 100)
  const paidCents = Math.round(paymentTotal(registration) * 100)
  return (feeCents - discountCents - paidCents) / 100
}

export function paymentStatus(registration) {
  const balance = paymentBalance(registration)
  if (balance < 0) return 'OVERPAID'
  if (balance === 0) {
    if (paymentTotal(registration) > 0) return 'PAID'
    if (Number(registration.discount || 0) >= Number(registration.fee || 0) && Number(registration.discount || 0) > 0) return 'DISCOUNT_SETTLED'
  }
  if (paymentTotal(registration) === 0) return 'NO_PAYMENT'
  return 'OWED'
}
