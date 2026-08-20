package com.researchspace.model.preference;

public interface PreferenceValidator {
	/**
	 * 
	 * @param value
	 * @return a string error message on failure, otherwise null on success
	 */
	String getMsgIfInvalid(String value);
	
	public static final PreferenceValidator ALWAYS_TRUE = new PreferenceValidator() {	
		@Override
		public String getMsgIfInvalid(String value) {
			return null;
		}
	};

}
