package pl.srm.biuroapi.registration.model;

import java.util.List;

public record StaffRoleStats(String categoryCode, String label, long accepted, int required,
        List<SubroleCount> subroles) {
    public StaffRoleStats { subroles = List.copyOf(subroles); }
    public record SubroleCount(String code, String label, long accepted) {}
}
