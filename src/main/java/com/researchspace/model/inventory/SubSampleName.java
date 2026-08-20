package com.researchspace.model.inventory;

/**
 * Choice of subsample name
 */
public enum SubSampleName {
	
	ALIQUOT, SUBSAMPLE, PIECE, PORTION, UNIT, INDIVIDUAL, COMPONENT, SECTION, VOLUME, OTHER;

	public String getDisplayName() {
		return this.name().toLowerCase();
	}

	public String getDisplayNamePlural() {
		return getDisplayName() + "s";
	}
}
