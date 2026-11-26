package com.globalrfq.identity.token.entity;

import com.globalrfq.identity.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch= FetchType.LAZY, optional = false)
    @JoinColumn(name="user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked", nullable= false)
    private boolean revoked;

    @Column(name = "replaced_by", length = 128)
    private String replacedBy;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public boolean isExpired(){
        return expiresAt.isBefore(Instant.now());
    }

    public boolean isUsable(){
        return !revoked && !isExpired();
    }


}
