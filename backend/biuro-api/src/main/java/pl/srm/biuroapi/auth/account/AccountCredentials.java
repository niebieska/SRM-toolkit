package pl.srm.biuroapi.auth.account;

// Deliberately no generated toString: credentials must not appear in logs.
public final class AccountCredentials {
    private final String id;
    private final String passwordHash;
    private final boolean active;

    public AccountCredentials(String id, String passwordHash, boolean active) {
        this.id = id;
        this.passwordHash = passwordHash;
        this.active = active;
    }

    public String id() { return id; }
    public String passwordHash() { return passwordHash; }
    public boolean active() { return active; }
}
