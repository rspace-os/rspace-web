package com.researchspace.model.audittrail;


/**
 * Interface for persisting audit trail or diagnostic data.
 */
public interface HistoryDAO {

	/**
	 * Saves a {@link HistoricData} object
	 * 
	 * @return the persisted <code>data</code>object
	 */
	HistoricData save(HistoricData data);
	
	/**
	 * Saves a collection of {@link HistoricData} objects, for when there are several auditable events
	 *   from a single service method (e.g., deletion of a folder may result in many records being deleted, each
	 *    of which should be recorded.
   */
	void save(Iterable<HistoricData> data);

}
