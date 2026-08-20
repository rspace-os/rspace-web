package com.researchspace.model.views;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic class to store results , failures and exceptions when a service is working on a
 * stream of results.
 *
 * @param <T> result object, e.g. an entity
 * @param <U> failure object
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ServiceOperationResultCollection<T, U> {
	private List<T> results = new ArrayList<>();
	private List<U> failures = new ArrayList<>();
	private List<Exception> exceptions = new ArrayList<>();

	public ServiceOperationResultCollection<T, U> addResult(T result) {
		this.results.add(result);
		return this;
	}

	public ServiceOperationResultCollection<T, U> addFailure(U failure) {
		this.failures.add(failure);
		return this;
	}
	
	public ServiceOperationResultCollection<T, U> addException(Exception e) {
		this.exceptions.add(e);
		return this;
	}

	public int getFailureCount() {
		return failures.size();
	}
	
	public int getResultCount() {
		return results.size();
	}
	public int getExceptionCount() {
		return exceptions.size();
	}
	/**
	 * Boolean test for absence of failures and exceptions, and &ge; 1 result
	 * @return
	 */
	public boolean isAllSucceeded() {
		return getExceptionCount() == 0 && getFailureCount() == 0 && getResultCount() > 0;
	}
	
	/**
	 * Merges contents of  <code>other</code> into this resultCollection
	 * @param other
	 * @return this for method chaining
	 */
	public ServiceOperationResultCollection<T, U> merge (ServiceOperationResultCollection<T, U> other) {
		for (Exception e: other.getExceptions()) {
			addException(e);
		}
		for (T result : other.getResults() ) {
			addResult(result);
		}
		for (U failure: other.getFailures()) {
			addFailure(failure);
		}
		return this;
	}
}
