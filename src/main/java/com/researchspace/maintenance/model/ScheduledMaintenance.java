package com.researchspace.maintenance.model;

import java.util.Calendar;
import java.util.Date;

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

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;

import com.researchspace.model.User;
import com.researchspace.session.SessionTimeZoneUtils;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Stores information about maintenance periods scheduled by system admin
 *
 */
@Entity
@Cache(usage=CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@EqualsAndHashCode
@ToString
public class ScheduledMaintenance {

	/**
	 * Number of minutes before scheduled maintenance when we stop letting people log in. 
	 */
	private static final int STOP_USER_LOGIN_MINUTES = 10;

	/**
	 * This is a placeholder to populate the cache when there are no scheduled maintenance windows.
	 * <br/> It is transient.
	 */
	public static final ScheduledMaintenance NULL = new ScheduledMaintenance(new Date(0), new Date(1)); 
	
	private Long id;
	private Long startDate;
	private Long endDate;
	private Long stopUserLoginDate;
	private String message;

	protected ScheduledMaintenance() {
	}
	
	/**
	 * Public constructor. startDate and endDate columns have nullable=false property, 
	 * so if you don't set them in constructor, make sure you do it before save. 
	 * 
	 * @param startDate
	 * @param endDate
	 */
	public ScheduledMaintenance(Date startDate,  Date endDate) {
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
	@NotNull(message = "start date {errors.required.field}")
	public Date getStartDate() {
		if (startDate == null) {
			return null;
		}
		return new Date(startDate);
	}
	
	@Transient
	public String getFormattedStartDate() {
		return getFormattedDate(getStartDate());
	}
	
	/**
	 * Sets new maintenance start date, updates stopUserLoginDate.
	 * @param startDate
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
	@NotNull(message = "end date {errors.required.field}")
	public Date getEndDate() {
		if (endDate == null) {
			return null;
		}
		return new Date(endDate);
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

	@Column(nullable = false)
	@Temporal(TemporalType.TIMESTAMP)
	public Date getStopUserLoginDate() {
		if (stopUserLoginDate == null) {
			return null;
		}
		return new Date(stopUserLoginDate);
	}

	@Transient
	public String getFormattedStopUserLoginDate() {
		return getFormattedDate(getStopUserLoginDate());
	}
	
	public void setStopUserLoginDate(Date stopUserLoginDate) {
		if (stopUserLoginDate != null) {
			this.stopUserLoginDate = stopUserLoginDate.getTime();
		}
	}

	@Size(min=0,max = User.DEFAULT_MAXFIELD_LEN, message="{message} {errors.string.max}")
	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
	
	@Transient
	public boolean getCanUserLoginNow() {
		long now = (new Date()).getTime();
		return now < stopUserLoginDate || now > endDate;
	}

	@Transient
	public boolean isActiveNow() {
		long now = (new Date()).getTime();
		return (now > startDate) && (now < endDate);
	}
	
	private void setDefaultStopUserLoginDate() {
		if (startDate != null) {
			Calendar cal = Calendar.getInstance();
			cal.setTimeInMillis(startDate);
			cal.add(Calendar.MINUTE, -STOP_USER_LOGIN_MINUTES);
			setStopUserLoginDate(cal.getTime());
		}
	}
	
	private String getFormattedDate(Date date) {
		return new SessionTimeZoneUtils().formatDateTimeForClient(date);
	}

}
