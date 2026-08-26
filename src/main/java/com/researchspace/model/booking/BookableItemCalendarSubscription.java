package com.researchspace.model.booking;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.researchspace.model.User;
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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/** One user's revocable calendar feed credential for one booking configuration. */
@Entity
@Table(
    uniqueConstraints = {
      @UniqueConstraint(
          name = "UK_BookableItemCalendarSubscription_configuration_user",
          columnNames = {"bookingConfiguration_id", "user_id"}),
      @UniqueConstraint(
          name = "UK_BookableItemCalendarSubscription_tokenHash",
          columnNames = "tokenHash")
    })
public class BookableItemCalendarSubscription implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "bookingConfiguration_id", nullable = false)
  @NotNull
  private BookingConfiguration bookingConfiguration;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  @NotNull
  private User user;

  @Column(nullable = false, length = 64)
  @Size(min = 64, max = 64)
  @JsonIgnore
  private String tokenHash;

  @Column(nullable = false)
  @NotNull
  private Date updatedAt;

  protected BookableItemCalendarSubscription() {}

  public BookableItemCalendarSubscription(
      BookingConfiguration bookingConfiguration, User user, String tokenHash, Date updatedAt) {
    this.bookingConfiguration = bookingConfiguration;
    this.user = user;
    this.tokenHash = tokenHash;
    setUpdatedAt(updatedAt);
  }

  public Long getId() {
    return id;
  }

  public BookingConfiguration getBookingConfiguration() {
    return bookingConfiguration;
  }

  public User getUser() {
    return user;
  }

  @JsonIgnore
  public String getTokenHash() {
    return tokenHash;
  }

  public void setTokenHash(String tokenHash) {
    this.tokenHash = tokenHash;
  }

  public Date getUpdatedAt() {
    return updatedAt == null ? null : new Date(updatedAt.getTime());
  }

  public void setUpdatedAt(Date updatedAt) {
    this.updatedAt = updatedAt == null ? null : new Date(updatedAt.getTime());
  }
}
