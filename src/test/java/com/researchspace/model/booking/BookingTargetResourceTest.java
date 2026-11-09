package com.researchspace.model.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.inventory.model.ApiV2InstrumentResource;
import java.util.List;
import org.junit.jupiter.api.Test;

class BookingTargetResourceTest {
  @Test
  void bothBookingTargetsNameTheInventoryResourceAndRetainTheirStoredKindAndPrefix() {
    for (var description :
        List.of(
            ApiV2BookingConfigurationResource.DESCRIPTION,
            ApiV2TimeSlotBookingResource.DESCRIPTION)) {
      var target = description.requireRelationship("target").targets().get(0);
      assertEquals(ApiV2InstrumentResource.RESOURCE_NAME, target.resourceName());
      assertEquals(BookableTargetType.INSTRUMENT, target.storedKind());
      assertEquals("IN", target.globalIdPrefix());
    }
  }
}
