package com.researchspace.model.inventory;
/**
 * Simple discriminator for whether sample is lab-created, obtained from Vendor or other (e.g. a gift)
 */
public enum SampleSource {
	
	/**
	 * Sample was created in the lab
	 */
	LAB_CREATED ("Lab-created"), 
	
	/**
	 * Sample is acquired from a vendor
	 */
	VENDOR_SUPPLIED("Vendor-supplied"), 
	
	/**
	 * Catch-all category e.g. for gift, requested sample etc
	 */
	OTHER("Other");
	
	public String getDisplayName() {
		return displayName;
	}

	private String displayName;

	private SampleSource(String displayName) {
		this.displayName = displayName;
	}

}
