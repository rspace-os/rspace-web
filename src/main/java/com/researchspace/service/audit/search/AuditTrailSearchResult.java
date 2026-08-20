package com.researchspace.service.audit.search;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import com.researchspace.model.audittrail.HistoricData;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

/**
 * Represents single hit retrieved from the AuditTrail search.
 */
@Value
@EqualsAndHashCode(of= {"data"})
public class AuditTrailSearchResult {

	private @NonNull HistoricData data;
	
	/**
	 * The timestamp of the logged event
	 */
	@NonNull
	@JsonSerialize(using = ISO8601DateTimeSerialiser.class)
	private  Long timestamp;

	/**
	 * Returns a descriptor object of the data associated with the log event.
	 *
   */
	public HistoricData getEvent() {
		return data;
	}

}
