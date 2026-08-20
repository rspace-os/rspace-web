package com.researchspace.model.views;

import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.audittrail.AuditTrailProperty;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import java.io.InvalidObjectException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Holds objects resulting from a composite operation affecting one or more records in a folder or
 * folder hierarchy.
 */
@AuditTrailData(delegateToCollection = "records")
public class CompositeRecordOperationResult<T extends BaseRecord> {

	// map each T with the possible Error message from the operation requested
	private final Map<T, String> reasonByRecordMap = new TreeMap<>();
	private Folder parentContainer;

	public CompositeRecordOperationResult(){
	}

	public CompositeRecordOperationResult(final T record, final Folder parentContainer) {
		this(record, "", parentContainer);
	}

	/**
	 * 
	 * @param record an optional {@link T} that was operated on.
	 */
	public CompositeRecordOperationResult(final T record, String errorMessage) {
		this(record, errorMessage, record.getParent());
	}

	/**
	 *
	 * @param record an optional {@link T} that was operated on.
	 * @param parentContainer a {@link Folder} that contains the record(s)
	 */
	public CompositeRecordOperationResult(final T record, String errorMessage,
			final Folder parentContainer) {
		this.parentContainer = parentContainer;
		if (record != null) {
			this.reasonByRecordMap.put(record ,errorMessage);
		}
	}

	@AuditTrailProperty(name = "deleted")
	public Set<T> getRecords() {
		return reasonByRecordMap.keySet();
	}

	public Map<T, String> getReasonByRecordMap(){
		return Collections.unmodifiableMap(this.reasonByRecordMap);
	}

	/**
	 * Gets the parent folder containing the records affected by an operation
	 * @return
	 */
	public Folder getParentContainer() {
		return parentContainer;
	}


	/**
	 * Adds a {@link T} to the collection of records affected by an operation.
	 * @param record
	 */
	public void addRecord(T record) {
		this.addRecord(record, "");
	}

	/***
	 * Adds a {@link T} to the collection of records affected by an operation including the
	 * error message
	 * @param record the resord affected by the operation
	 * @param errorMessage the error message returned from the operation
	 */
	public void addRecord(T record, String errorMessage) {
		this.reasonByRecordMap.put(record, errorMessage);
	}

	/**
	 * Adds all these argument records to this result
	 * @param toAdd
	 */
	public void addRecords(Set<T> toAdd) {
		toAdd.forEach(el -> this.addRecord(el));
	}

	/***
	 *
	 * Get the ONLY result from the T collection.
	 *
	 * @return the T result from the operation
	 * @throws InvalidObjectException if the collection is Empty or contains more than one element
	 */
	public T getSingleResult() throws InvalidObjectException {
		if (this.reasonByRecordMap.isEmpty()) {
			throw new InvalidObjectException("There was not any result for this operation");
		} else if (this.reasonByRecordMap.size() > 1) {
			throw new InvalidObjectException("There was more than one result as response of this "
					+ "operation which should only have a single result");
		}
		return this.reasonByRecordMap.keySet().stream().findFirst().get();
	}

}
