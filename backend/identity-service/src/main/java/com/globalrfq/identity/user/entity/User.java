package com.globalrfq.identity.user.entity;

import com.globalrfq.identity.role.entity.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY )
    private Long id;

    @Column(name = "employee_code", unique = true, length = 30)
    private String employeeCode;

    @Column(name="full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name="official_email", nullable = false, unique = true, length = 190)
    private String officialEmail;

    @Column(name = "official_email_verified", nullable = false)
    private boolean officialEmailVerified;

    @Column(name="official_email_verified_at")
    private Instant officialEmailVerifiedAt;

    @Column(name="personal_email", unique = true, length=190)
    private String personalEmail;

    @Column(name = "personal_email_verified", nullable = false)
    private boolean personalEmailVerified;

    @Column(name="personal_email_verified_at")
    private Instant personalEmailVerifiedAt;

    @Column(name = "phone", length = 25)
    private String phone;

    @Column(name="password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 25)
    private UserStatus status = UserStatus.PENDING_VERIFICATION;

    @Column(name="failed_login_attempts", nullable=false)
    private int failedLoginAttempts;

    @Column(name="locked_until")
    private Instant lockedUntil;

    @Column(name="last_login_at")
    private Instant lastLoginAt;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name="created_by")
    private Long createdBy;

    @Column(name="created_at", insertable = false, updatable = false)
    private Instant updatedAt;
     @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name="user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    public boolean isLockedNow() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
