package com.researchspace.model.permissions;

class PropertyConstraintTSS extends PropertyConstraint {
	 /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public PropertyConstraintTSS(String name, String value) {
		super(name, value);
		}
	String uname;

	// override to decouple from application security. Always return a single 
	String getPrincipalFromSecurityCtxt() {
		 return uname;
	 }
}