package pl.srm.biuroapi.common.api;

public record ApiError(int status, String message) {
}
