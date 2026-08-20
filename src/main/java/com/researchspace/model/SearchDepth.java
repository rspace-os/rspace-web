package com.researchspace.model;

/**
 * Specifies search behaviour when searching hierarchical collections.
 */
public enum SearchDepth {

	/**
	 * Just search the current collection of objects, with no nested search.
	 * 
	 */
	ZERO,

	/**
	 * Nested search through descendant hierarchy of objects
	 */
	INFINITE,

	/**
	 * Search from root element in collection downwards.
	 */
	GLOBAL,
}
