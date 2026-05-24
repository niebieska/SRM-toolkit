package pl.srm.registrationapi.registration.api;

public record StatusUpdateRequest(String status, String rejectionReason) {
}
