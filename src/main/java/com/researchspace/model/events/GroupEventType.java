package com.researchspace.model.events;

public enum GroupEventType {
	
    /**
     * User joined the group
     */
	JOIN,
	
	/**
	 * User was removed from group
	 */
	REMOVE,
	
	/**
	 * User enabled autosharing
	 */
	ENABLED_AUTOSHARING,
	
	/**
	 * User disabled autosharing
	 */
	DISABLED_AUTOSHARING,

	/**
	 * User enabled group-wide autosharing
	 */
	ENABLED_GROUP_AUTOSHARING,

	/**
	 * User disabled group-wide autosharing
	 */
	DISABLED_GROUP_AUTOSHARING
}
