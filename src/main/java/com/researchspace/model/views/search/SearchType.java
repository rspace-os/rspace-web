package com.researchspace.model.views.search;

/**
 * Type for a search query.
 */
public enum SearchType {

	/**
	 * What text to search for
	 */
	TEXT,

	TAG,
	/**
	 * What file name to search for
	 */
	NAME,

	FORM,

	TEMPLATE,

	CREATION_DATE,

	LAST_MODIFIED,

	USER,

	ATTACHMENT,

	/**
	 * Search in these selected folders
	 */
	FOLDERS,

	/**
	 * Search in these selected documents.
	 */
	DOCUMENTS,

	/**
	 * Filter files and only return templates, or starred files, or shared with me
	 */
	FILTER
}

