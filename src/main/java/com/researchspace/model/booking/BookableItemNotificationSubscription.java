package com.researchspace.model.booking;

import com.researchspace.model.User;
import com.researchspace.model.inventory.Instrument;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.util.Date;

/** A user's personal booking-notification choice for one instrument. */
@Entity
@Table(
    uniqueConstraints =
        @UniqueConstraint(
            name = "UK_BookableItemNotificationSubscription_user_instrument",
            columnNames = {"user_id", "instrument_id"}))
public class BookableItemNotificationSubscription {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "instrument_id", nullable = false)
  private Instrument instrument;

  @Column(nullable = false)
  private boolean enabled;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(nullable = false)
  private Date createdAt;

  @Column(nullable = false)
  private Date updatedAt;

  protected BookableItemNotificationSubscription() {}

  public BookableItemNotificationSubscription(
      User user, Instrument instrument, boolean enabled, Date createdAt) {
    this.user = user;
    this.instrument = instrument;
    this.enabled = enabled;
    this.createdAt = copy(createdAt);
    this.updatedAt = copy(createdAt);
  }

  public Long getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public Instrument getInstrument() {
    return instrument;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public long getVersion() {
    return version;
  }

  public Date getCreatedAt() {
    return copy(createdAt);
  }

  public Date getUpdatedAt() {
    return copy(updatedAt);
  }

  public void setUpdatedAt(Date updatedAt) {
    this.updatedAt = copy(updatedAt);
  }

  private static Date copy(Date value) {
    return value == null ? null : new Date(value.getTime());
  }
}
