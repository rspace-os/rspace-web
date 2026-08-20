package com.researchspace.model.views;


import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ServiceOperationResultTest {

	@ParameterizedTest(name=" {index} msg = [{0}] is invalid")
	@NullAndEmptySource
	@ValueSource(strings = {"  "})
	@DisplayName("Message required if succeeded == false")
	void invariantsFalse(String msg) {
		assertThrows(IllegalArgumentException.class,
				()->new ServiceOperationResult<>(null, false, msg));
	}	
	
	@ParameterizedTest(name="{index} msg [{0}] is accepted")
	@NullAndEmptySource
	@ValueSource(strings = {"  "})
	@DisplayName("Message not required if succeeded == true")
	void invariantsTrue(String msg) {
		assertNotNull(new ServiceOperationResult<>(null, true, msg));
	}	

}
