package com.northcart.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.util.Objects;

/**
 * Common columns for every table: surrogate id + audit timestamps.
 *
 * <p>{@code @MappedSuperclass} means there is no "base_entity" table; the fields are
 * simply copied into each subclass's table.</p>
 *
 * <p>No setter for id or timestamps: the database and Hibernate own those values.</p>
 */
@Getter
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /*
     * equals/hashCode for JPA entities:
     *  - Two entities are equal only if they are the same type AND have the same non-null id.
     *  - A new (unsaved) entity has id == null, so it is only equal to itself.
     *  - hashCode is constant per class so it does NOT change when the id is assigned on save;
     *    otherwise an entity put into a HashSet before persist() could never be found again.
     *  - HibernateProxy handling: a lazy-loaded reference is a generated subclass, so we
     *    compare the real persistent classes, not getClass().
     */
    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (effectiveClass(this) != effectiveClass(o)) return false;
        BaseEntity other = (BaseEntity) o;
        return getId() != null && Objects.equals(getId(), other.getId());
    }

    @Override
    public final int hashCode() {
        return effectiveClass(this).hashCode();
    }

    private static Class<?> effectiveClass(Object o) {
        return o instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : o.getClass();
    }
}
