export function isRegistrationCapacityFull(capacityInfo) {
  if (!capacityInfo) return false
  if (!Number.isFinite(capacityInfo.accepted) || !Number.isFinite(capacityInfo.capacity)) return false
  return capacityInfo.accepted >= capacityInfo.capacity
}

export function canAcceptRegistration(registration, capacityInfo) {
  return Boolean(registration)
    && registration.status !== 'ACCEPTED'
    && !isRegistrationCapacityFull(capacityInfo)
}
