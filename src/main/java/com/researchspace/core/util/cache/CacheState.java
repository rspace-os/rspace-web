package com.researchspace.core.util.cache;

/**
 * Set of contstants exporting the state of the cache.
 */
public enum CacheState {
	/**
	 * Cache is empty
	 */
	EMPTY,
	/**
	 * Cache has a valid item
	 */
	VALID,
	/**
	 * Cache has an expired item.
	 */
	STALE

}
