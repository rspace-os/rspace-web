package com.researchspace.search.impl;

import com.researchspace.model.record.StructuredDocument;
import org.hibernate.search.mapper.orm.mapping.HibernateOrmMappingConfigurationContext;
import org.hibernate.search.mapper.orm.mapping.HibernateOrmSearchMappingConfigurer;

/**
 * Programmatic additions to the Hibernate Search mapping, merged with the annotation-based mapping
 * on the entities. Used for indexing rules that belong to this application rather than to the
 * entity classes in rspace-core-model.
 *
 * <p>Registered via the {@code hibernate.search.mapping.configurer} property in {@code
 * hibernate.cfg.xml}.
 */
public class RSpaceSearchMappingConfigurer implements HibernateOrmSearchMappingConfigurer {

  @Override
  public void configure(HibernateOrmMappingConfigurationContext context) {
    context
        .programmaticMapping()
        .type(StructuredDocument.class)
        .indexed()
        .routingBinder(new TemporaryDocRoutingBinder());
  }
}
