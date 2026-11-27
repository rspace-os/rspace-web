package com.researchspace.model.booking;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/** Stable, typed identity of a bookable entity. */
@Embeddable
@Access(AccessType.FIELD)
public final class BookableTargetReference implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @Enumerated(EnumType.STRING)
  @Column(name = "targetType", nullable = false, length = 32)
  private BookableTargetType type;

  @Column(name = "targetId", nullable = false)
  private Long id;

  protected BookableTargetReference() {}

  public BookableTargetReference(BookableTargetType type, Long id) {
    this.type = Objects.requireNonNull(type, "Target type");
    this.id = Objects.requireNonNull(id, "Target id");
  }

  public BookableTargetType type() {
    return type;
  }

  public Long id() {
    return id;
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || other instanceof BookableTargetReference reference
            && type == reference.type
            && id.equals(reference.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, id);
  }

  @Override
  public String toString() {
    return type + ":" + id;
  }
}
