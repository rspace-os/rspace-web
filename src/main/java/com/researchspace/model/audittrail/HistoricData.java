package com.researchspace.model.audittrail;

import java.util.Date;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Simple POJO object holding generic information for audit trail / diagnostic
 * logging
 */

@EqualsAndHashCode(of={"timestamp","action","domain"})
@ToString(exclude={"fullName"})
@Getter
public class HistoricData {
	@Setter(value = AccessLevel.PACKAGE)
	private String subject;
	@Setter(value = AccessLevel.PACKAGE)
	private String fullName;
	private AuditDomain domain;
	private AuditAction action;
	private AuditData data;
	private Date timestamp;
	@Setter
	private String description;

	/**
	 * 
	 * @param domain
	 *            A non-null {@link AuditDomain}
	 * @param action
	 *            A non-null AuditAction
	 * @param fullName user's full Name
	 * @param data
	 *            An optional {@link AuditData}
	 * @param username subject's username
	 * @throws IllegalArgumentException
	 *             if <code>domain</code> or <code>action</code> is
	 *             <code>null</code>.
	 */
	public HistoricData(AuditDomain domain,  AuditAction action, String fullName, AuditData data, String  username) {
		super();
		if (domain == null || action == null || username == null) {
			throw new IllegalArgumentException("action or domain  or subject cannot be null");
		}
		this.domain = domain;
		this.action = action;
		this.subject = username;
		this.fullName = fullName;
		if (data == null) {
			data = new AuditData();
		}
		this.data = data;
	}

	public HistoricData(AuditDomain domain, HistoricalEvent event, AuditData data) {
		this(domain, event.getAuditAction(), event.getSubject().getFullName(), data, event
				.getSubject().getUniqueName());
		this.description = event.getDescription();
	}

	/**
	 * Optional setter used when reading from logfiles.
	 *
	 */
	public void setTimestamp(Date timestamp) {
		this.timestamp = new Date(timestamp.getTime());
	}

	public Date getTimestamp() {
		return timestamp == null ? null : new Date(timestamp.getTime());
	}
}
