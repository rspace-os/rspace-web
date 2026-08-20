package com.researchspace.model.views;

import lombok.AllArgsConstructor;
import lombok.Getter;
/**
 * Extends functionality of {@link ServiceOperationResult} with additional message
 *
 * @param <T>
 */
@Getter
@AllArgsConstructor
public class MessagedServiceOperationResult <T> {
	
	public MessagedServiceOperationResult(T entity, boolean succeeded, String message) {
		super();
		this.result = new ServiceOperationResult<>(entity, succeeded);
		this.message = message;
	}

	
	private ServiceOperationResult<T> result;
	
	private String message = "";
	public void setMessage(String message) {
		this.message = message;
	}
	
	public boolean isSucceeded (){
		return result.isSucceeded();
	}
	
	public T getEntity() {
		return result.getEntity();
	}
}
