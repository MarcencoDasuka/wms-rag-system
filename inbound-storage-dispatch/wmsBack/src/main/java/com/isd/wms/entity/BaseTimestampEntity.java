package com.isd.wms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Abstract base class for all entities that require creation and update timestamps.
 * <p>
 * Provides {@code createdAt} and {@code updatedAt} fields that are automatically
 * populated by Hibernate via {@code @CreationTimestamp} and {@code @UpdateTimestamp}.
 * All concrete entity classes that need auditing timestamps should extend this class.
 * </p>
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseTimestampEntity {

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
