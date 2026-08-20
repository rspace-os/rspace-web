package com.researchspace.model.record;

/**
 * Callback interface to process records and folders within a folder tree. <br/>
 * Implementations shouldn't modify the record/folder hierarchy or modify the
 * record container's collections.
 */
public interface RecordContainerProcessor {

	/**
	 * Processes the passed in record container
	 * 
	 * @param rc A {@link BaseRecord}
	 * @return a boolean to indicate whether to continue processing a
	 *         subtree(true) or to terminate (false).
	 */
	boolean process(BaseRecord rc);
}
