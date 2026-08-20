package com.researchspace.core.util.cache;

/**
 * In memory cache for an item with a settable expiry time.
 */
public interface TimeLimitedMemoryCache<T> {

	/**
	 * Gets cached item, or <code>null</code> if cache is unavailable or has
	 * expired.
	 * 
	 * @return A cached item, or null
	 */
	T getCachedItem();

	/**
	 * Sets cache time in milliseconds
	 * 
	 * @param cacheTimeMillis
	 */
	void setCacheTimeMillis(Long cacheTimeMillis);

	/**
	 * Sets an item into the cache. Cache will remain VALID until
	 * cacheTimeMillis millis has elapsed, after which time cache will be STALE.
	 * 
	 * @param license
	 */
	void cache(T license);

	/**
	 * Sets cache to {@link CacheState#EMPTY}
	 */
	void clear();

	/**
	 * Gets the state of this cache
	 * 
	 * @return
	 */
	CacheState getState();

	/**
	 * Cache holds an item whose cache time has not yet expired.
	 * 
	 * @return
	 */
	boolean isValid();

	/**
	 * Cache does not hold an item, i.e., getItem() would return
	 * <code>null</code>.
	 * 
	 * @return
	 */
	boolean isEmpty();

	/**
	 * Cache holds an item whose cache time has expired. The item is still
	 * retrievable though.
	 * 
	 * @return
	 */
	boolean isStale();

}
