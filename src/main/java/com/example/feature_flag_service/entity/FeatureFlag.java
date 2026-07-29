package com.example.feature_flag_service.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "feature_flags",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "name"}))
@Data
public class FeatureFlag {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean enabled = false;

    @Column(nullable = false)
    private int rolloutPercentage = 0;

    @ElementCollection
    @CollectionTable(name = "flag_targeted_users", joinColumns = @JoinColumn(name = "flag_id"))
    @Column(name = "user_id")
    private Set<String> targetedUsers = new HashSet<>();   // ← add this

    @Version
    private Long version;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    // getters/setters — add getTargetedUsers()/setTargetedUsers() too
}