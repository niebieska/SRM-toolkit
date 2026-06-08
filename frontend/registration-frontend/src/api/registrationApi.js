const API_BASE = 'http://localhost:8080'
const STAFF_HEALTH_QUESTIONS = {
    q1: 'Czy posiadasz choroby przewlekłe, o których organizator powinien wiedzieć?',
    q2: 'Czy występują u Ciebie alergie lub nietolerancje pokarmowe, które powinny zostać uwzględnione przy przygotowywaniu posiłków?',
    additionalInfo: 'Dodatkowe informacje istotne pod kątem organizacji turnusu',
}

const PARTICIPANT_HEALTH_QUESTIONS = {
    q1: 'Czy uczestnik/uczestniczka posiada choroby przewlekłe, o których organizator powinien wiedzieć?',
    q2: 'Czy u uczestnika/uczestniczki występują alergie lub nietolerancje pokarmowe, które powinny zostać uwzględnione przy przygotowywaniu posiłków?',
    q3: 'Czy uczestnik/uczestniczka ma specjalne potrzeby edukacyjne lub inne ważne informacje, o których organizator powinien wiedzieć?',
}


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

function withQuestion(item, question) {
    return {
        question,
        answer: item?.answer || '',
        detail: item?.detail || '',
    }
}

function buildParticipantHealthBlock(f) {
    return {
        q1: withQuestion(
            f.health.q1,
            PARTICIPANT_HEALTH_QUESTIONS.q1),
        q2: withQuestion(
            f.health.q2, PARTICIPANT_HEALTH_QUESTIONS.q2),
        q3: withQuestion(
            f.health.q3, PARTICIPANT_HEALTH_QUESTIONS.q3),
    }
}

function buildStaffHealthBlock(f) {
    return {
        q1: withQuestion(
            f.health.q1,
            STAFF_HEALTH_QUESTIONS.q1
        ),

        q2: withQuestion(
            f.health.q2,
            STAFF_HEALTH_QUESTIONS.q2
        ),

        additionalInfo: {
            question: STAFF_HEALTH_QUESTIONS.additionalInfo,
            value: f.health.additionalInfo || '',
        },
    }
}

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
        health: buildParticipantHealthBlock(f),
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
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(payload),
    })
    if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`)
    return response.json()
}

export async function submitStaffRegistration(formData) {
    const payload = buildStaffPayload(formData)
    const response = await fetch(`${API_BASE}/api/registrations/staff`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(payload),
    })
    if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`)
    return response.json()
}
