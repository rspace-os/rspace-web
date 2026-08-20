package com.researchspace.service.audit.search;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.joda.time.DateTime;

import com.researchspace.core.util.DateRange;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.AuditTrailData;

@AuditTrailData(auditDomain=AuditDomain.AUDIT)
public class AuditTrailSearchElement {
	
	private static final int HOURS = 23;
	private static final int MINUTES = 59;
	private static final int SECONDS = 59;

	@Override
	public String toString() {
		return "AuditTrailSearchElement [usernames=" + usernames + ", dateRange=" + dateRange
				+ ", domains=" + domains + ", actions=" + actions + "]";
	}

	/**
	 * Creates a search config element with default values
	 */
	public AuditTrailSearchElement() {
		super();
		setDefaults();
	}

	public AuditTrailSearchElement(IAuditTrailSearchConfig inputSearchConfig) {
		this();
		if (!CollectionUtils.isEmpty(inputSearchConfig.getDomains())) {
			setDomains(EnumSet.copyOf(inputSearchConfig.getDomains()));
		}
		if (!CollectionUtils.isEmpty(inputSearchConfig.getActions())) {
			setActions(EnumSet.copyOf(inputSearchConfig.getActions()));
		}

		setOid(inputSearchConfig.getOid());
		// incoming to date is set to midnight; we want to include this day
		if (inputSearchConfig.getDateTo() != null) {
			Date toDate = new DateTime(inputSearchConfig.getDateTo()).plusHours(HOURS).plusMinutes(MINUTES)
					.plusSeconds(SECONDS).toDate();
			inputSearchConfig.setDateTo(toDate);
		}
		DateRange dateRange = new DateRange(inputSearchConfig.getDateFrom(), inputSearchConfig.getDateTo());
		setDateRange(dateRange);
		setUsernames(inputSearchConfig.getUsernames());
	}

	public void setUsernames(Set<String> usernames) {
		this.usernames = usernames;
	}
	/**
	 * Adds usernames to the collection of usernames to query.
   */
	public void addUsernames (Collection<String> usernames) {
		this.usernames.addAll(usernames);
	}

	private void setDefaults() {
		this.usernames = new HashSet<>();
		domains = EnumSet.allOf(AuditDomain.class);
		actions = EnumSet.allOf(AuditAction.class);
		this.dateRange = new DateRange(0L, null);
	}

	/**
	 * Resets configuration to defaults
	 */
	public void clear() {
		setDefaults();
	}

	private Set<String> usernames = new HashSet<>();

	/**
	 * If empty, assume is not set, and use all
	 *
   */
	public Collection<String> getUsernames() {
		return usernames==null?Collections.emptySet():usernames;
	}

	public void addUsernameTerm(String username) {
		this.usernames.add(username);
	}

	public void setDateRange(DateRange dateRange) {
		this.dateRange = dateRange;
	}

	public DateRange getDateRange() {
		return dateRange;
	}

	public EnumSet<AuditDomain> getDomains() {
		return domains;
	}

	public EnumSet<AuditAction> getActions() {
		return actions;
	}

	private DateRange dateRange;

	private EnumSet<AuditDomain> domains = EnumSet.allOf(AuditDomain.class);

	private EnumSet<AuditAction> actions = EnumSet.allOf(AuditAction.class);
	
	private String oid = null;

	public String getOid() {
		return oid;
	}

	public void setOid(String oid) {
		this.oid = oid;
	}

	public void setDomains(EnumSet<AuditDomain> domains) {
		this.domains = domains;
	}

	public void setActions(EnumSet<AuditAction> actions) {
		this.actions = actions;
	}

}
