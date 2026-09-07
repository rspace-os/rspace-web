package com.researchspace.dao.query;

import com.blazebit.persistence.Criteria;
import com.blazebit.persistence.spi.CriteriaBuilderConfiguration;
import com.blazebit.persistence.spi.JpqlFunctionGroup;

/**
 * The default Blaze configuration plus the functions RSpace queries need.
 *
 * <p>A factory rather than {@code Criteria.getDefault()} straight from XML, because a registered
 * function has to exist before the {@code CriteriaBuilderFactory} is built; registering it later
 * has no effect on an already-created factory.
 */
public final class BlazeCriteriaConfiguration {

  private BlazeCriteriaConfiguration() {}

  public static CriteriaBuilderConfiguration create() {
    CriteriaBuilderConfiguration configuration = Criteria.getDefault();
    configuration.registerFunction(
        new JpqlFunctionGroup(NumericTextFunction.NAME, new NumericTextFunction()));
    return configuration;
  }
}
