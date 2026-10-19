package com.researchspace.booking.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.dao.query.RsqlCollectionQuery.Predicate;
import com.researchspace.model.booking.ApiV2BookingInstrumentResource;
import com.researchspace.model.booking.ApiV2TimeSlotBookingResource;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.inventory.Instrument;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class BookingCalendarQueryTest {

  @Test
  void searchEscapesLiteralLikeCharactersForItemsAndEventsWithoutChangingCase() {
    var instruments = ApiV2BookingInstrumentResource.description(AccessFunction.anyone());
    ResourceRegistry resources =
        new ResourceRegistry(
            List.of(
                instruments,
                new CollectionDescription<Instrument>(
                    "instruments",
                    Instrument.class,
                    instruments.fields(),
                    instruments.idField(),
                    instruments.defaultSort(),
                    AccessPolicy.readOnly(AccessFunction.anyone()))));
    @SuppressWarnings("unchecked")
    ObjectProvider<ResourceRegistry> registry = mock(ObjectProvider.class);
    when(registry.getObject()).thenReturn(resources);
    BookingCalendarQuery query =
        new BookingCalendarQuery(
            ApiV2TimeSlotBookingResource.description(AccessFunction.anyone()), registry);

    Predicate search = query.eventSearch("  MiXeD%_!\\  ", null);

    String expected = "%MiXeD!%!_!!\\%";
    assertEquals(expected, search.parameters().get("calendarItemTextPattern"));
    assertEquals(expected, search.parameters().get("calendarEventTextPattern"));
    assertThat(search.expression())
        .contains(
            "LOWER(booking.purpose) LIKE LOWER(:calendarEventTextPattern) ESCAPE '!'", "CONCAT(");
    assertThat(search.subqueries().get("calendarItemText").whereExpression())
        .contains(
            "LOWER(calendarInstrument.editInfo.name) LIKE LOWER(:calendarItemTextPattern) ESCAPE"
                + " '!'",
            "LOWER(calendarInstrument.editInfo.description) LIKE LOWER(:calendarItemTextPattern)"
                + " ESCAPE '!'");
  }
}
