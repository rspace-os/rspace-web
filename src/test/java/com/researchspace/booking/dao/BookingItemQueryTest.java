package com.researchspace.booking.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.dao.query.RsqlCollectionQuery;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.inventory.Instrument;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BookingItemQueryTest {

  @Test
  void trustedRestrictionCompositionRejectsDistinctBindingCollisions() {
    RsqlCollectionQuery.Predicate left =
        new RsqlCollectionQuery.Predicate("left", Map.of("shared", "left"));
    RsqlCollectionQuery.Predicate right =
        new RsqlCollectionQuery.Predicate("right", Map.of("shared", "right"));
    assertThrows(IllegalArgumentException.class, () -> BookingItemQuery.and(left, right));

    RsqlCollectionQuery.Subquery leftSubquery =
        new RsqlCollectionQuery.Subquery(Instrument.class, "instrument", "instrument.id = 1");
    RsqlCollectionQuery.Subquery rightSubquery =
        new RsqlCollectionQuery.Subquery(Instrument.class, "instrument", "instrument.id = 2");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            BookingItemQuery.and(
                new RsqlCollectionQuery.Predicate("left", Map.of(), Map.of("shared", leftSubquery)),
                new RsqlCollectionQuery.Predicate(
                    "right", Map.of(), Map.of("shared", rightSubquery))));

    RsqlCollectionQuery.Predicate sameBinding =
        new RsqlCollectionQuery.Predicate("same", Map.of("shared", BookableTargetType.INSTRUMENT));
    assertEquals(
        BookableTargetType.INSTRUMENT,
        BookingItemQuery.and(sameBinding, sameBinding).parameters().get("shared"));
  }
}
