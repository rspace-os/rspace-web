package com.researchspace.integrations.clustermarket;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableTransactionManagement
@EnableRetry
@Configuration
/*
 * Do not place this under webapp package as component scan from dispatcher-servlet context causes issues:
 * (Transactional advice or @Transactional does not work -  might be double proxying).
 */
public class ClustermarketConfig {}
