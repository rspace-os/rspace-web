package com.researchspace.model.audittrail.spring;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.researchspace.core.util.TransformerUtils;
import com.researchspace.service.audit.search.LogLineContentProvider;
import com.researchspace.service.audit.search.LogLineContentProviderImpl;

@EnableCaching
@Configuration
public class SpringConfig {

	

	@Bean
	LogLineContentProvider LogLineContentProvider (){
		return new LogLineContentProviderImpl();
	}
	
	@Bean
	CacheManager cacheManager() {
		SimpleCacheManager cacheMgr = new SimpleCacheManager();
		Cache cache = new ConcurrentMapCache(LogLineContentProviderImpl.AUDIT_FILES_CACHE);
		cacheMgr.setCaches(TransformerUtils.toList(cache));
		return cacheMgr;
	}
}
