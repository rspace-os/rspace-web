package com.researchspace.core.util.cache;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * A basic cache that caches an item for a particular time. <br/>
 * Not thread safe!
 *
 */
public class DefaultTimeLimitedMemoryCache<T> implements TimeLimitedMemoryCache<T> {
	private CacheState state;

	public DefaultTimeLimitedMemoryCache() {
		super();
		this.state = CacheState.EMPTY;
	}

	/**
	 * @param cacheTimeMilliSeconds
	 *            the cacheTimeMilliSeconds to set
	 */
	public void setCacheTimeMillis(Long cacheTimeMilliSeconds) {
		this.cacheTimeMillis = cacheTimeMilliSeconds;
		refreshState();
	}

	private Long cacheTimeMillis = DEFAULT_LICENSE_CACHE_TIME_MILLIS;

	private Date lastCacheTime;

	private T cachedItem;

	// 1 hour default cache;
	private static final Long DEFAULT_LICENSE_CACHE_TIME_MILLIS = Long.valueOf(60 * 60 * 1000);

	private void setState(CacheState state) {
		this.state = state;
	}

	@Override
	public T getCachedItem() {
		if (cachedItem == null || lastCacheTime == null) {
			setState(CacheState.EMPTY);
			return null;
		} else {
			if (checkStale()) {
				setState(CacheState.STALE);
			}
			return cachedItem;
		}
	}

	private void refreshState() {
		if (cachedItem == null || lastCacheTime == null) {
			setState(CacheState.EMPTY);
		} else if (checkStale()) {
			setState(CacheState.STALE);
		} else {
			setState(CacheState.VALID);
		}
	}

	private boolean checkStale() {
		Calendar cal = Calendar.getInstance();
		cal.setTime(lastCacheTime);
		if (cacheTimeMillis > 0) {
			cal.add(Calendar.SECOND, (int) (cacheTimeMillis.intValue() / 1000));
		}
		return new Date().after(cal.getTime());
	}

	@Override
	public void cache(T item) {
		if (item == null) {
			setState(CacheState.EMPTY);
			return;
		}
		this.cachedItem = item;
		lastCacheTime = new Date();
	}

	@Override
	public void clear() {
		this.cachedItem = null;
		this.lastCacheTime = null;
		setState(CacheState.EMPTY);
	}

	@Override
	public CacheState getState() {
		refreshState();
		return state;
	}

	@Override
	public boolean isValid() {
		refreshState();
		return CacheState.VALID.equals(state);
	}

	@Override
	public boolean isEmpty() {
		refreshState();
		return CacheState.EMPTY.equals(state);
	}

	@Override
	public boolean isStale() {
		refreshState();
		return CacheState.STALE.equals(state);
	}
	
	public String  toString (){
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
		return String.format("Item last cached at %s for %d millis" , (lastCacheTime!=null)?sdf.format(lastCacheTime):"never cached", cacheTimeMillis);
	}

}
