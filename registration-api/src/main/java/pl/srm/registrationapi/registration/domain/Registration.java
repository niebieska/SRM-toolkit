package pl.srm.registrationapi.registration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "registration")
public class Registration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "registration_code", nullable = false, unique = true)
    private String registrationCode;

    @Column(name = "registration_type", nullable = false)
    private String registrationType;

    @Column(name = "turnus_code", nullable = false)
    private String turnusCode;

    @Column(name = "pesel_hash", nullable = false)
    private String peselHash;

    @Column(name = "is_minor", nullable = false)
    private boolean minor;

    @Column(nullable = false)
    private String status;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Registration() {
    }

    public Registration(String registrationCode,
                        String registrationType,
                        String turnusCode,
                        String peselHash,
                        boolean minor,
                        String status,
                        String rejectionReason,
                        String payload,
                        LocalDateTime createdAt,
                        LocalDateTime updatedAt) {
        this.registrationCode = registrationCode;
        this.registrationType = registrationType;
        this.turnusCode = turnusCode;
        this.peselHash = peselHash;
        this.minor = minor;
        this.status = status;
        this.rejectionReason = rejectionReason;
        this.payload = payload;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRegistrationCode() {
        return registrationCode;
    }

    public void setRegistrationCode(String registrationCode) {
        this.registrationCode = registrationCode;
    }

    public String getRegistrationType() {
        return registrationType;
    }

    public void setRegistrationType(String registrationType) {
        this.registrationType = registrationType;
    }

    public String getTurnusCode() {
        return turnusCode;
    }

    public void setTurnusCode(String turnusCode) {
        this.turnusCode = turnusCode;
    }

    public String getPeselHash() {
        return peselHash;
    }

    public void setPeselHash(String peselHash) {
        this.peselHash = peselHash;
    }

    public boolean isMinor() {
        return minor;
    }

    public void setMinor(boolean minor) {
        this.minor = minor;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
