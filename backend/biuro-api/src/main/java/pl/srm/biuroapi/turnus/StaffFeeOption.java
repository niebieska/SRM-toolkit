package pl.srm.biuroapi.turnus;

/** Display labels for one exact role/subrole that may have a Turnus fee. */
public record StaffFeeOption(String role, String roleLabel, String subrole, String subroleLabel) {}
