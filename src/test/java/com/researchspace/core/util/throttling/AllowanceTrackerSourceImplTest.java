package com.researchspace.core.util.throttling;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import com.researchspace.core.util.TimeSource;

public class AllowanceTrackerSourceImplTest {
	public @Rule MockitoRule rule = MockitoJUnit.rule();
	AllowanceTrackerSourceImpl allowanceTrackerSourceImpl ;
	@Mock TimeSource timesource;

	@BeforeEach
	public void setUp() throws Exception {
		MockitoAnnotations.initMocks(this);
		allowanceTrackerSourceImpl = new AllowanceTrackerSourceImpl(timesource, new ThrottleDefinitionSet());
	}

	@Test
	public void getAllowanceRequiresNonEmptyId() {
		assertThrows(IllegalArgumentException.class, ()->allowanceTrackerSourceImpl.getAllowance(""));
	}

}
