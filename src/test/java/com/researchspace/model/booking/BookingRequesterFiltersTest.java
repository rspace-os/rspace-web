package com.researchspace.model.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.ResourceReference;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Booking requester filter guard")
class BookingRequesterFiltersTest {

  private static final FilterExpression SHOWN =
      new FilterExpression.Or(
          List.of(
              new FilterExpression.Comparison("target", Operator.EXISTS, List.of(true), false),
              new FilterExpression.Comparison("requesterId", Operator.EQUAL, List.of(7L), false)));

  @Test
  void pairsEveryRequesterComparisonWithTheRuleThatShowsTheRequester() {
    FilterExpression.Comparison byId = comparison("requesterId", Operator.EQUAL, 42L);
    FilterExpression.Comparison notById = comparison("requesterId", Operator.NOT_EQUAL, 42L);
    FilterExpression.Comparison byUsername =
        comparison("requesterUsername", Operator.CONTAINS, "ada");
    FilterExpression.Comparison calendarName =
        comparison("requesterFirstName", Operator.CONTAINS, "Ada");

    for (FilterExpression.Comparison requester : List.of(byId, notById, byUsername, calendarName)) {
      assertEquals(
          new FilterExpression.And(List.of(requester, SHOWN)),
          BookingRequesterFilters.visibleRequestersOnly(requester, 7L),
          requester.field());
    }
  }

  @Test
  void keepsOtherComparisonsAndTheBooleanStructure() {
    FilterExpression.Comparison purpose = comparison("purpose", Operator.CONTAINS, "plate");
    FilterExpression.Comparison target =
        comparison("target", Operator.EQUAL, new ResourceReference<>("INSTRUMENT", 3L));
    FilterExpression.Comparison requester = comparison("requesterId", Operator.IN, 42L);
    FilterExpression filter =
        new FilterExpression.And(
            List.of(target, new FilterExpression.Or(List.of(purpose, requester))));

    FilterExpression guarded = BookingRequesterFilters.visibleRequestersOnly(filter, 7L);

    assertEquals(
        new FilterExpression.And(
            List.of(
                target,
                new FilterExpression.Or(
                    List.of(purpose, new FilterExpression.And(List.of(requester, SHOWN)))))),
        guarded);
    assertSame(purpose, BookingRequesterFilters.visibleRequestersOnly(purpose, 7L));
    assertNull(BookingRequesterFilters.visibleRequestersOnly(null, 7L));
  }

  @Test
  void anAnonymousRuleRequiresAReadableItem() {
    assertEquals(
        new FilterExpression.Comparison("target", Operator.EXISTS, List.of(true), false),
        BookingRequesterFilters.requesterVisible(null));
    assertTrue(BookingRequesterFilters.namesRequester("requesterLastName"));
  }

  private static FilterExpression.Comparison comparison(
      String field, Operator operator, Object value) {
    return new FilterExpression.Comparison(field, operator, List.of(value), false);
  }
}
