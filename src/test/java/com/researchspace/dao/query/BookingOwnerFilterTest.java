package com.researchspace.dao.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.inventory.model.ApiV2ContainerResource;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.RsqlFilterParser;
import com.researchspace.model.collection.RuntimeFieldContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class BookingOwnerFilterTest {
  @Test
  void ownerPredicateIncludesReadableUndeletedInstrumentScope() {
    User viewer = new User("viewer");
    viewer.setId(12L);
    viewer.setEnabled(true);
    var configurations = ApiV2BookingConfigurationResource.DESCRIPTION;
    var registry =
        new ResourceRegistry(
            List.of(
                configurations,
                ApiV2InstrumentResource.DESCRIPTION,
                ApiV2ContainerResource.DESCRIPTION,
                ApiV2UserResource.DESCRIPTION));
    var filter =
        new RsqlFilterParser(configurations, registry, new RuntimeFieldContext(List.of(), viewer))
            .parse("targetOwner==me");
    var predicate =
        new RsqlCollectionQuery(configurations, "configuration")
            .translate(filter, RelationshipReadAccess.forActor(registry, viewer));
    assertTrue(predicate.expression().contains("EXISTS"));
    var subquery = predicate.subqueries().values().iterator().next();
    assertTrue(subquery.whereExpression().contains(".owner.username"));
    assertTrue(subquery.whereExpression().contains(".deleted"));
    assertTrue(predicate.parameters().containsValue("viewer"));
    assertTrue(predicate.parameters().containsValue(false));
    assertFalse(predicate.expression().contains("configuration.targetOwner"));
  }

  @Test
  void ownerPredicateCannotBypassDeniedInventoryReadAccess() {
    User viewer = new User("viewer");
    viewer.setId(12L);
    viewer.setEnabled(true);
    var configurations = ApiV2BookingConfigurationResource.DESCRIPTION;
    var registry =
        new ResourceRegistry(
            List.of(
                configurations,
                ApiV2InstrumentResource.description(AccessFunction.never()),
                ApiV2ContainerResource.DESCRIPTION,
                ApiV2UserResource.DESCRIPTION));
    var filter =
        new RsqlFilterParser(configurations, registry, new RuntimeFieldContext(List.of(), viewer))
            .parse("targetOwner==me");
    var predicate =
        new RsqlCollectionQuery(configurations, "configuration")
            .translate(filter, RelationshipReadAccess.forActor(registry, viewer));
    assertEquals(RsqlCollectionQuery.FALSE_EXPRESSION, predicate.expression());
  }
}
