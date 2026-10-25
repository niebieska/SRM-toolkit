package pl.srm.registrationapi.registration.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "registration_edit_history")
public class RegistrationEditHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "registration_id", nullable = false)
    private Long registrationId;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(name = "actor_account_id", nullable = false, length = 36)
    private String actorAccountId;

    @Column(name = "actor_username", nullable = false, length = 64)
    private String actorUsername;

    @Column(name = "changed_fields", nullable = false, columnDefinition = "TEXT")
    private String changedFields;

    protected RegistrationEditHistory() {}

    public RegistrationEditHistory(Long registrationId, LocalDateTime changedAt,
                                   String actorAccountId, String actorUsername, String changedFields) {
        this.registrationId = registrationId;
        this.changedAt = changedAt;
        this.actorAccountId = actorAccountId;
        this.actorUsername = actorUsername;
        this.changedFields = changedFields;
    }

    public Long getId() { return id; }
    public Long getRegistrationId() { return registrationId; }
    public LocalDateTime getChangedAt() { return changedAt; }
    public String getActorAccountId() { return actorAccountId; }
    public String getActorUsername() { return actorUsername; }
    public String getChangedFields() { return changedFields; }
}
