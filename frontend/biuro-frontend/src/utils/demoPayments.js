// Demonstration-only payment register data. Never sent to or saved by the API.
export function createDemoPaymentRegistrations(turnusCode) {
  return [
  {
    id: `${turnusCode}-demo-p-001`,
    registrationCode: `REG-P-${turnusCode}-18`,
    firstName: 'Anna',
    lastName: 'Kowalska',
    registrationType: 'PARTICIPANT',
    fee: 1200,
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
    fee: 1200,
    discount: 200,
    payments: [
      { id: `${turnusCode}-demo-pay-002`, date: '2026-10-12', amount: 500, method: 'Przelew', label: 'Zaliczka', note: '' },
    ],
    discountHistory: [{ previousAmount: 0, amount: 200, changedAt: '2026-10-10 12:20', actor: 'operator.demo', description: 'Przykładowy rabat' }],
  },
  {
    id: `${turnusCode}-demo-p-003`,
    registrationCode: `REG-P-${turnusCode}-20`,
    firstName: 'Maria',
    lastName: 'Wiśniewska',
    registrationType: 'PARTICIPANT',
    fee: 1200,
    discount: 0,
    payments: [
      { id: `${turnusCode}-demo-pay-003`, date: '2026-10-13', amount: 1200, method: 'Przelew', label: '', note: 'Wpłata całej kwoty' },
    ],
    discountHistory: [],
  },
  {
    id: `${turnusCode}-demo-s-001`,
    registrationCode: `REG-S-${turnusCode}-7`,
    firstName: 'Jakub',
    lastName: 'Zieliński',
    registrationType: 'STAFF',
    role: 'Animator — kandydat na animatora',
    fee: 500,
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
    role: 'Kucharka',
    fee: 300,
    discount: 0,
    payments: [
      { id: `${turnusCode}-demo-pay-005`, date: '2026-10-14', amount: 300, method: 'Gotówka', label: '', note: '' },
    ],
    discountHistory: [],
  },
  ]
}

export function paymentTotal(registration) {
  return registration.payments.reduce((total, payment) => total + Number(payment.amount || 0), 0)
}

export function paymentBalance(registration) {
  return Math.max(0, Number(registration.fee || 0) - Number(registration.discount || 0) - paymentTotal(registration))
}

export function paymentStatus(registration) {
  if (paymentTotal(registration) === 0) return 'NO_PAYMENT'
  return paymentBalance(registration) > 0 ? 'OWED' : 'PAID'
}
