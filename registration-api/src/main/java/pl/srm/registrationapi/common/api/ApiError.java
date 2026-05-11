package pl.srm.registrationapi.common.api;


public record ApiError(
        String code,
        String message
) {
}
