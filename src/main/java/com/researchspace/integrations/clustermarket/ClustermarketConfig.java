package com.researchspace.integrations.clustermarket;

import org.springframework.context.annotation.Configuration;

// @EnableRetry - TEMPORARILY DISABLED for Spring 6 migration testing
@Configuration
/*
 * Do not place this under webapp package as component scan from dispatcher-servlet context causes issues:
 * (Transactional advice or @Transactional does not work -  might be double proxying).
 *
 * Note: @EnableTransactionManagement is in BaseConfig and applies application-wide.
 */
public class ClustermarketConfig {}
