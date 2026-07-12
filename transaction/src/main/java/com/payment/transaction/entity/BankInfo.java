package com.payment.transaction.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = BankInfo.TABLE)
public class BankInfo {

    public static final String TABLE           = "BankInfo";
    public static final String ORGID           = "bankID";
    public static final String ORGNAME         = "bnkNAME";
    public static final String DESCRIPTION     = "DESCRIPTION";
    public static final String PRIMARYCONTACT  = "PRIMARYCONTACT";
    public static final String COUNTRY_CODE    = "COUNTRY_CODE";
    public static final String ALIAS           = "ALIAS";
    public static final String TIME_ZONE       = "TIME_ZONE";
    public static final int    TIME_ZONE_IDX   = 11;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bankID", nullable = false, unique = true)
    private String orgId;

    @Column(name = "bnkNAME", nullable = false)
    private String orgName;

    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "PRIMARYCONTACT")
    private String primaryContact;

    @Column(name = "COUNTRY_CODE", length = 3)
    private String countryCode;

    @Column(name = "ALIAS")
    private String alias;

    @Column(name = "TIME_ZONE")
    private String timeZone;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public String getOrgId() { return orgId; }
    public void setOrgId(String orgId) { this.orgId = orgId; }

    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPrimaryContact() { return primaryContact; }
    public void setPrimaryContact(String primaryContact) { this.primaryContact = primaryContact; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }

    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
