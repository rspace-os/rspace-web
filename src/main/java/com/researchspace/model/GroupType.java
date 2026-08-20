package com.researchspace.model;

public enum GroupType {
	/**
	 * A regular lab group
	 */
	LAB_GROUP("Lab Group"),

	/**
	 * A user-created and managed collaboration group.
	 */
	COLLABORATION_GROUP("Collaboration Group"),

	/**
	 * A user-created group that has no PI
	 */
	PROJECT_GROUP("Project Group");

	private final String label;

	GroupType(String label){
		this.label = label;
	}

	public String getLabel(){
		return label;
	}

}
