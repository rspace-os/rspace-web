package com.researchspace.model.test;

import org.hibernate.search.backend.lucene.analysis.LuceneAnalysisConfigurationContext;
import org.hibernate.search.backend.lucene.analysis.LuceneAnalysisConfigurer;

/**
 * Test-only mirror of the production analysis configurer (which lives in rspace-web and is therefore
 * not on the core-model classpath). Hibernate Search 7 bootstraps strictly, so every analyzer /
 * normalizer referenced by an indexed entity must be defined before the SessionFactory can be built.
 * Defines the analyzers referenced by the core-model entity annotations: {@code structureAnalyzer}
 * (used both as an analyzer and, on {@code User}, as a normalizer), {@code axiopeanalyzer} and
 * {@code aclAnalyzer}. Keep in sync with {@code RSpaceLuceneAnalysisConfigurer} in rspace-web.
 */
public class TestLuceneAnalysisConfigurer implements LuceneAnalysisConfigurer {

  @Override
  public void configure(LuceneAnalysisConfigurationContext context) {
    // used for full-text search on structured document / inventory field content
    context
        .analyzer("structureAnalyzer")
        .custom()
        .tokenizer("standard")
        .charFilter("htmlStrip")
        .tokenFilter("lowercase")
        .tokenFilter("stop");

    // structureAnalyzer is also referenced as a normalizer (keyword fields, e.g. User)
    context.normalizer("structureAnalyzer").custom().tokenFilter("lowercase");

    // general text fields
    context
        .analyzer("axiopeanalyzer")
        .custom()
        .tokenizer("standard")
        .tokenFilter("lowercase")
        .tokenFilter("stop");

    // access control list fields
    context.analyzer("aclAnalyzer").custom().tokenizer("standard").tokenFilter("lowercase");
  }
}
