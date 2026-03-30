package com.researchspace.search.impl;

import org.hibernate.search.backend.lucene.analysis.LuceneAnalysisConfigurationContext;
import org.hibernate.search.backend.lucene.analysis.LuceneAnalysisConfigurer;

/**
 * Configures Lucene analyzers for Hibernate Search 6. Defines all custom analyzers used throughout
 * the entity model.
 */
public class RSpaceLuceneAnalysisConfigurer implements LuceneAnalysisConfigurer {

  @Override
  public void configure(LuceneAnalysisConfigurationContext context) {
    // Define the structureAnalyzer - used for full-text search on structured document content
    context
        .analyzer("structureAnalyzer")
        .custom()
        .tokenizer("standard")
        .tokenFilter("lowercase")
        .tokenFilter("stop");

    // Define the structureAnalyzer as a normalizer (for keyword fields)
    // Normalizers are like analyzers but don't tokenize
    context.normalizer("structureAnalyzer").custom().tokenFilter("lowercase");

    // Define axiopeanalyzer - standard analyzer for general text fields
    context
        .analyzer("axiopeanalyzer")
        .custom()
        .tokenizer("standard")
        .tokenFilter("lowercase")
        .tokenFilter("stop");

    // Define aclAnalyzer - analyzer for access control list fields
    context.analyzer("aclAnalyzer").custom().tokenizer("standard").tokenFilter("lowercase");
  }
}
