package com.researchspace.model.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ServiceOperationResultCollectionTest {

	@Test
	void invariants() {
		ServiceOperationResultCollection<Object,Object> anyResult = new ServiceOperationResultCollection<>();
		assertNotNull(anyResult.getFailures());
		assertNotNull(anyResult.getResults());
		assertNotNull(anyResult.getExceptions());
		assertEquals(1, anyResult.addResult(new Object()).getResultCount());
		assertEquals(1, anyResult.addFailure(new Object()).getFailureCount());
		assertEquals(1, anyResult.addException(new RuntimeException()).getExceptionCount());	
	}
	
	@Test
	void allSucceeded() {
		ServiceOperationResultCollection<Object,Object> anyResult = new ServiceOperationResultCollection<>();
		assertFalse(anyResult.isAllSucceeded());
		anyResult.addResult(new Object());
		assertTrue(anyResult.isAllSucceeded());
		anyResult.addFailure(new Object());
		assertFalse(anyResult.isAllSucceeded());
	}

}
