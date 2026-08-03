package com.researchspace.maintenance.model;

import com.researchspace.model.User;
import com.researchspace.session.SessionTimeZoneUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Calendar;
import java.util.Date;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;

/** Stores information about maintenance periods scheduled by a system administrator. */
@Entity
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@EqualsAndHashCode
@ToString
public class ScheduledMaintenance {

  /** Number of minutes before scheduled maintenance when users can no longer log in. */
  private static final int STOP_USER_LOGIN_MINUTES = 10;

  /** Transient placeholder returned when there is no scheduled maintenance window. */
  public static final ScheduledMaintenance NULL =
      new ScheduledMaintenance(new Date(0), new Date(1));

  private Long id;
  private Long startDate;
  private Long endDate;
  private Long stopUserLoginDate;
  private String message;

  protected ScheduledMaintenance() {}

  /**
   * Creates a scheduled maintenance window.
   *
   * @param startDate start of the maintenance window
   * @param endDate end of the maintenance window
   */
  public ScheduledMaintenance(Date startDate, Date endDate) {
    setStartDate(startDate);
    setEndDate(endDate);
    setDefaultStopUserLoginDate();
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Column(nullable = false)
  @Temporal(TemporalType.TIMESTAMP)
  @DateTimeFormat(iso = ISO.DATE_TIME)
  @NotNull(message = "{errors.api.v2.maintenance.startDate.required}")
  public Date getStartDate() {
    return startDate == null ? null : new Date(startDate);
  }

  @Transient
  public String getFormattedStartDate() {
    return getFormattedDate(getStartDate());
  }

  /**
   * Sets the maintenance start date and derives the default login cutoff.
   *
   * @param startDate start of the maintenance window
   */
  public void setStartDate(Date startDate) {
    if (startDate != null) {
      this.startDate = startDate.getTime();
      setDefaultStopUserLoginDate();
    }
  }

  @Column(nullable = false)
  @Temporal(TemporalType.TIMESTAMP)
  @DateTimeFormat(iso = ISO.DATE_TIME)
  @NotNull(message = "{errors.api.v2.maintenance.endDate.required}")
  public Date getEndDate() {
    return endDate == null ? null : new Date(endDate);
  }

  @Transient
  public String getFormattedEndDate() {
    return getFormattedDate(getEndDate());
  }

  public void setEndDate(Date endDate) {
    if (endDate != null) {
      this.endDate = endDate.getTime();
    }
  }

  @Column(nullable = true)
  @Temporal(TemporalType.TIMESTAMP)
  public Date getStopUserLoginDate() {
    return stopUserLoginDate == null ? null : new Date(stopUserLoginDate);
  }

  @Transient
  public String getFormattedStopUserLoginDate() {
    return getFormattedDate(getStopUserLoginDate());
  }

  public void setStopUserLoginDate(Date stopUserLoginDate) {
    this.stopUserLoginDate = stopUserLoginDate == null ? null : stopUserLoginDate.getTime();
  }

  @Size(min = 0, max = User.DEFAULT_MAXFIELD_LEN, message = "{message} {errors.string.max}")
  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  @Transient
  public boolean getCanUserLoginNow() {
    long now = System.currentTimeMillis();
    long cutoff = stopUserLoginDate == null ? startDate : stopUserLoginDate;
    return now < cutoff || now > endDate;
  }

  @Transient
  public boolean isActiveNow() {
    long now = System.currentTimeMillis();
    return now > startDate && now < endDate;
  }

  /** Whether this maintenance ends strictly after it starts. */
  @Transient
  public boolean hasValidWindow() {
    return startDate != null && endDate != null && endDate > startDate;
  }

  private void setDefaultStopUserLoginDate() {
    if (startDate != null) {
      Calendar calendar = Calendar.getInstance();
      calendar.setTimeInMillis(startDate);
      calendar.add(Calendar.MINUTE, -STOP_USER_LOGIN_MINUTES);
      setStopUserLoginDate(calendar.getTime());
    }
  }

  private String getFormattedDate(Date date) {
    return new SessionTimeZoneUtils().formatDateTimeForClient(date);
  }
}
