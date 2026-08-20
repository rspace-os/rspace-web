package com.researchspace.model.core;

import java.util.Date;

/**
 * Core methods supported by all record-like objects in RSpace.
 */
public interface IRSpaceDoc {

	String getName();

	Long getId();
	
	String getGlobalIdentifier();

	boolean isMediaRecord();

	boolean isStructuredDocument();

	Person getOwner();

	Date getCreationDate();

	Date getModificationDateAsDate();

}
