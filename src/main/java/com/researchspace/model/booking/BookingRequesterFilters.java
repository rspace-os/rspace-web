package com.researchspace.model.booking;

import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.Operator;
import java.util.List;

/**
 * Keeps requester filters from revealing who booked an event that the caller sees only as busy.
 *
 * <p>An event shows its requester only in full detail: the caller can currently read the event's
 * Inventory item, or requested the event. A comparison on the requester is therefore paired with
 * that same condition, so it matches only events whose requester the response would show. A busy
 * event never satisfies a requester comparison, positive or negative, and its presence in or
 * absence from a result says nothing about who booked it.
 *
 * <p>The row read policy currently grants no other rows, but this guard does not depend on that:
 * widening event visibility, for example to show busy blocks on items the caller cannot read, keeps
 * requester filtering safe.
 */
public final class BookingRequesterFilters {

  private BookingRequesterFilters() {}

  /** Returns the filter with every requester comparison limited to visible requesters. */
  public static FilterExpression visibleRequestersOnly(FilterExpression filter, Long subjectId) {
    if (filter == null) {
      return null;
    }
    if (filter instanceof FilterExpression.And and) {
      return new FilterExpression.And(
          and.children().stream().map(child -> visibleRequestersOnly(child, subjectId)).toList());
    }
    if (filter instanceof FilterExpression.Or or) {
      return new FilterExpression.Or(
          or.children().stream().map(child -> visibleRequestersOnly(child, subjectId)).toList());
    }
    FilterExpression.Comparison comparison = (FilterExpression.Comparison) filter;
    return namesRequester(comparison.field())
        ? new FilterExpression.And(List.of(comparison, requesterVisible(subjectId)))
        : comparison;
  }

  /**
   * The event-level condition under which its requester is shown: a readable item, or the caller's
   * own request. Both selectors exist on every Booking event description.
   */
  public static FilterExpression requesterVisible(Long subjectId) {
    FilterExpression readableItem =
        new FilterExpression.Comparison("target", Operator.EXISTS, List.of(true), false);
    if (subjectId == null) {
      return readableItem;
    }
    return new FilterExpression.Or(
        List.of(
            readableItem,
            new FilterExpression.Comparison(
                "requesterId", Operator.EQUAL, List.of(subjectId), false)));
  }

  /**
   * Requester identity and name selectors: {@code requesterId} and the Calendar's {@code
   * requesterUsername}, {@code requesterFirstName} and {@code requesterLastName}.
   */
  static boolean namesRequester(String selector) {
    return selector.startsWith("requester");
  }
}
