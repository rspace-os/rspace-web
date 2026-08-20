package com.researchspace.model.audittrail;

/**
 * Example class of how audit trail annotations could work.
 */
@AuditTrailData(auditDomain = AuditDomain.FORM)
public class AuditTrailTestObject {
	final static String string = "Example";
	final static Long id = 1L;
	AuditTrailTestObjectNested nested;

	@AuditTrailProperty(name = "property")
	public String getString() {
		return string;
	}

	@AuditTrailProperty(name = "nested")
	public AuditTrailTestObjectNested getNested() {
		return new AuditTrailTestObjectNested();
	}

	public Long getId() {
		return id;
	}

	@AuditTrailIdentifier
	public static String getOid() {
		return "FD1";
	}

	static class AuditTrailTestObjectNested {
		final static String string = "Example1";
		final static Long id = 2L;

		@AuditTrailProperty(name = "property")
		public String getString() {
			return string;
		}

		public Long getId() {
			return id;
		}

		@AuditTrailIdentifier
		public static String getOid() {
			return "FD2";
		}

	}
}
