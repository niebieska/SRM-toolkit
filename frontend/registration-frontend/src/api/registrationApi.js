const API_BASE = 'http://localhost:8080'

function buildPersonBlock(f) {
  return {
    firstName: f.firstName,
    lastName: f.lastName,
    pesel: f.pesel,
    gender: f.gender,
    isAdult: f.isAdult,
    contact: {
      email: f.email,
      phone: f.phone,
    },
  }
}

function buildGuardianBlock(f) {
  return {
    firstName: f.guardianFirstName,
    lastName: f.guardianLastName,
    relation: f.guardianRelation,
    names: f.parentNames,
    contact: {
      email: f.guardianEmail,
      phone: f.guardianPhone,
    },
  }
}

function buildIceBlock(f) {
  return {
    firstName: f.iceFirstName,
    lastName: f.iceLastName,
    relation: f.iceRelation,
    phone: f.icePhone,
  }
}

function buildHealthBlock(f) {
  return {
    q1: f.health.q1,
    q2: f.health.q2,
    q3: f.health.q3,
    q4: f.health.q4,
    q5: f.health.q5 ?? { answer: '', detail: '' },
    q6: f.health.q6 ?? { answer: '', detail: '' },
    declaration: f.healthDeclaration,
  }
}

function buildStaffHealthBlock(f) {
  return {
    q1: f.health.q1,
    q2: f.health.q2,
    additionalInfo: f.health.additionalInfo || '',
  }
}

/*function buildConsentsBlock(f) {
  return {
    dataProcessing: f.consent1,
    imageUsage: f.consent2,
    regulations: f.consent3,
  }
}*/

function buildConsentsBlock(f) {
  return {
    dataProcessing: !!f.consent1,
    imageUsage: !!f.consent2,
    regulations: !!f.consent3,
    truthDeclaration: !!f.truthDeclaration,
  }
}

export function buildParticipantPayload(f) {
  return {
    turnusCode: f.turnusCode,
    person: buildPersonBlock(f),
    address: {
      street: f.street,
      houseNumber: f.houseNumber,
      postalCode: f.postalCode,
      city: f.city,
      sameAddress: f.sameAddress,
      guardianAddress: f.sameAddress ? null : {
        street: f.parentStreet,
        houseNumber: f.parentHouseNumber,
        postalCode: f.parentPostalCode,
        city: f.parentCity,
      },
    },
    guardian: f.isAdult ? null : buildGuardianBlock(f),
    ice: f.isAdult ? buildIceBlock(f) : null,
    health: buildHealthBlock(f),
    consents: buildConsentsBlock(f),
  }
}

export function buildStaffPayload(f) {
  return {
    turnusCode: f.turnusCode,
    person: buildPersonBlock(f),
    address: {
      street: f.street,
      houseNumber: f.houseNumber,
      postalCode: f.postalCode,
      city: f.city,
    },
    guardian: f.isAdult ? null : buildGuardianBlock(f),
    ice: f.isAdult ? buildIceBlock(f) : null,
    role: f.role,
    subrole: f.subrole,
    certificates: f.certificates,
    certificateDetails: f.certificateDetails,
    health: buildStaffHealthBlock(f),
    consents: buildConsentsBlock(f),
  }
}

export async function submitParticipantRegistration(formData) {
  const payload = buildParticipantPayload(formData)
  const response = await fetch(`${API_BASE}/api/registrations/participant`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`)
  return response.json()
}

export async function submitStaffRegistration(formData) {
  const payload = buildStaffPayload(formData)
  const response = await fetch(`${API_BASE}/api/registrations/staff`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`)
  return response.json()
}
