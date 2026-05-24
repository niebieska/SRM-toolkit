package pl.srm.registrationapi.common.dto;


public record ApiError(
        String code,
        String message
) {
}
