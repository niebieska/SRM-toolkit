package pl.srm.registrationapi.registration.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "registration_status_history")
public class RegistrationStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "registration_id", nullable = false)
    private Long registrationId;

    @Column(name = "previous_status", nullable = false, length = 30)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 30)
    private String newStatus;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(name = "actor_account_id", nullable = false, length = 36)
    private String actorAccountId;

    @Column(name = "actor_username", nullable = false, length = 64)
    private String actorUsername;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    protected RegistrationStatusHistory() {
    }

    public RegistrationStatusHistory(Long registrationId, String previousStatus, String newStatus,
                                    LocalDateTime changedAt, String actorAccountId, String actorUsername,
                                    String rejectionReason) {
        this.registrationId = registrationId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.changedAt = changedAt;
        this.actorAccountId = actorAccountId;
        this.actorUsername = actorUsername;
        this.rejectionReason = rejectionReason;
    }

    public Long getId() { return id; }
    public Long getRegistrationId() { return registrationId; }
    public String getPreviousStatus() { return previousStatus; }
    public String getNewStatus() { return newStatus; }
    public LocalDateTime getChangedAt() { return changedAt; }
    public String getActorAccountId() { return actorAccountId; }
    public String getActorUsername() { return actorUsername; }
    public String getRejectionReason() { return rejectionReason; }
}
