package com.researchspace.model.audittrail;

import com.researchspace.model.core.Person;

import lombok.Value;

public class TestFactory {
	
	@Value
	static class AuditTrailTestUser implements Person {
		
		AuditTrailTestUser(String username) {
			super();
			this.uniqueName = username;
			this.fullName = username + " surname";
			this.email= username+"@somewhere.com";
					}

		String uniqueName, email;
		String fullName;	
		Long id = 2L;
	}
	
	static Person createAnyUser(String username) {
		return new AuditTrailTestUser(username);
	}

}
