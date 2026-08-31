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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/** One user's revocable credential for a calendar containing all of their bookings. */
@Entity
@Table(
    uniqueConstraints = {
      @UniqueConstraint(name = "UK_UserBookingCalendarSubscription_user", columnNames = "user_id"),
      @UniqueConstraint(
          name = "UK_UserBookingCalendarSubscription_tokenHash",
          columnNames = "tokenHash")
    })
public class UserBookingCalendarSubscription implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  @NotNull
  private User user;

  @Column(nullable = false, length = 64)
  @Size(min = 64, max = 64)
  @JsonIgnore
  private String tokenHash;

  @Column(nullable = false, length = 255)
  @Size(max = 255)
  @JsonIgnore
  private String rawToken;

  @Column(nullable = false)
  @NotNull
  private Date updatedAt;

  protected UserBookingCalendarSubscription() {}

  public UserBookingCalendarSubscription(
      User user, String tokenHash, String rawToken, Date updatedAt) {
    this.user = user;
    this.tokenHash = tokenHash;
    this.rawToken = rawToken;
    setUpdatedAt(updatedAt);
  }

  public Long getId() {
    return id;
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

  @JsonIgnore
  public String getRawToken() {
    return rawToken;
  }

  public void setRawToken(String rawToken) {
    this.rawToken = rawToken;
  }

  public Date getUpdatedAt() {
    return updatedAt == null ? null : new Date(updatedAt.getTime());
  }

  public void setUpdatedAt(Date updatedAt) {
    this.updatedAt = updatedAt == null ? null : new Date(updatedAt.getTime());
  }
}
