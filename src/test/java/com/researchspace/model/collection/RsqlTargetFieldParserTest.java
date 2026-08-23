package com.researchspace.model.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.collection.CollectionDescription.Operator;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Parses a caller filter that names a field reached through a relationship. */
class RsqlTargetFieldParserTest {

  private static final ResourceRegistry REGISTRY =
      new ResourceRegistry(
          List.of(
              ApiV2BookingConfigurationResource.DESCRIPTION,
              ApiV2InstrumentResource.DESCRIPTION,
              ApiV2UserResource.DESCRIPTION));

  private final RsqlFilterParser withTargets =
      new RsqlFilterParser(ApiV2BookingConfigurationResource.DESCRIPTION, REGISTRY);
  private final RsqlFilterParser withoutTargets =
      new RsqlFilterParser(ApiV2BookingConfigurationResource.DESCRIPTION);

  @Test
  void acceptsAFieldOfTheRelationshipTarget() {
    assertEquals(
        new FilterExpression.Comparison(
            "target.name", Operator.CONTAINS, List.of("confocal"), false),
        withTargets.parse("target.name=contains=confocal"));
  }

  @Test
  void keepsTheExistingRelationshipSelectorsWorking() {
    FilterExpression parsed = withTargets.parse("target.relationTo==instruments");

    assertEquals("target.relationTo", ((FilterExpression.Comparison) parsed).field());
    assertEquals(Operator.EQUAL, ((FilterExpression.Comparison) parsed).operator());
  }

  @Test
  void refusesARelationshipFieldWithoutARegistry() {
    assertThrows(
        CollectionQueryException.class,
        () -> withoutTargets.parse("target.name=contains=confocal"));
  }

  @Test
  void refusesANegativeOperatorOnARelationshipField() {
    assertThrows(CollectionQueryException.class, () -> withTargets.parse("target.name!=confocal"));
  }

  @Test
  void refusesAFieldTheTargetDoesNotPublish() {
    assertThrows(CollectionQueryException.class, () -> withTargets.parse("target.globalId==IN1"));
    assertThrows(CollectionQueryException.class, () -> withTargets.parse("target.nosuch==x"));
  }

  /** An internal filter must stay unreachable, including through a relationship. */
  @Test
  void refusesAnInternalFilterOfTheTarget() {
    assertThrows(CollectionQueryException.class, () -> withTargets.parse("target.sharingAcl==lab"));
    assertThrows(
        CollectionQueryException.class, () -> withTargets.parse("target.ownerUsername==someone"));
  }
}
