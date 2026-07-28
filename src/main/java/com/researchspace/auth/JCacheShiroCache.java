package com.researchspace.auth;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.cache.Cache;
import org.apache.shiro.cache.CacheException;

/** Adapts a {@link javax.cache.Cache} to Shiro's {@link org.apache.shiro.cache.Cache} interface. */
public class JCacheShiroCache<K, V> implements org.apache.shiro.cache.Cache<K, V> {

  private final javax.cache.Cache<K, V> jCache;

  public JCacheShiroCache(Cache<K, V> jCache) {
    this.jCache = jCache;
  }

  @Override
  public V get(K key) throws CacheException {
    return jCache.get(key);
  }

  @Override
  public V put(K key, V value) throws CacheException {
    V previous = jCache.getAndPut(key, value);
    return previous;
  }

  @Override
  public V remove(K key) throws CacheException {
    V previous = jCache.getAndRemove(key);
    return previous;
  }

  @Override
  public void clear() throws CacheException {
    jCache.removeAll();
  }

  @Override
  public int size() {
    int count = 0;
    for (Cache.Entry<K, V> entry : jCache) {
      count++;
    }
    return count;
  }

  @Override
  public Set<K> keys() {
    Set<K> keys = new HashSet<>();
    for (Cache.Entry<K, V> entry : jCache) {
      keys.add(entry.getKey());
    }
    return keys;
  }

  @Override
  public Collection<V> values() {
    Collection<V> values = new java.util.ArrayList<>();
    for (Cache.Entry<K, V> entry : jCache) {
      values.add(entry.getValue());
    }
    return values;
  }
}
