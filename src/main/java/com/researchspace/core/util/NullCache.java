package com.researchspace.core.util;

import com.researchspace.core.util.cache.CacheState;
import com.researchspace.core.util.cache.TimeLimitedMemoryCache;

/**
 * Null implementation that never caches the License.
 */
public class NullCache<T> implements TimeLimitedMemoryCache<T> {

	@Override
	public T getCachedItem() {
		return null;
	}

	@Override
	public void cache(T license) {
	}

	@Override
	public void setCacheTimeMillis(Long cacheTime) {
	}

	@Override
	public void clear() {
	}

	@Override
	public CacheState getState() {
		return CacheState.EMPTY;
	}

	@Override
	public boolean isValid() {

		return false;
	}

	@Override
	public boolean isEmpty() {
		return true;
	}

	@Override
	public boolean isStale() {
		return false;
	}

}
