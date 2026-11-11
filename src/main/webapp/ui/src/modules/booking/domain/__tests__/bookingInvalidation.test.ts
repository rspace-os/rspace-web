import { QueryClient } from "@tanstack/react-query";
import { expect, it } from "vitest";
import { getSearchCalendarEventsQueryKey } from "@/modules/common/api/v2/generated/booking-calendar/booking-calendar";
import {
  getCountBookingCatalogueAvailabilityQueryKey,
  getSearchBookingCatalogueLocationsQueryKey,
  getSearchBookingCatalogueQueryKey,
  getSearchCalendarCatalogueQueryKey,
} from "@/modules/common/api/v2/generated/booking-catalogue/booking-catalogue";
import {
  getGetBookingConfigurationAccessQueryKey,
  getSearchBookingConfigurationAccessGranteesQueryKey,
} from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import { getGetBookingQueryKey, getListBookingsQueryKey } from "@/modules/common/api/v2/generated/bookings/bookings";
import { invalidateBookingListOperation } from "../bookingInvalidation";

const window = { start: "2026-08-17T00:00:00Z", end: "2026-08-18T00:00:00Z", mine: false, page: 1, limit: 20 };
const keys = {
  catalogue: getSearchBookingCatalogueQueryKey({ mine: false, page: 1, limit: 20 }),
  locations: getSearchBookingCatalogueLocationsQueryKey({ page: 1, limit: 20 }),
  calendarCatalogue: getSearchCalendarCatalogueQueryKey(window),
  availabilityCounts: getCountBookingCatalogueAvailabilityQueryKey(window),
  calendarEvents: getSearchCalendarEventsQueryKey(window),
};

it.each(["catalogue", "locations", "calendarCatalogue", "availabilityCounts", "calendarEvents"] as const)(
  "refreshes every cached parameter variant of %s and leaves other operations alone",
  async (operation) => {
    const client = new QueryClient();
    const base = keys[operation];
    const variants = [base, [base[0], { q: "microscope", page: 2, mine: true }], [...base, "aggregate"]];
    const unrelated = [
      ...Object.entries(keys)
        .filter(([name]) => name !== operation)
        .map(([, key]) => key),
      getGetBookingQueryKey(42),
      getListBookingsQueryKey({ page: 2 }),
    ];
    for (const key of [...variants, ...unrelated]) client.setQueryData(key, {});
    await invalidateBookingListOperation(client, operation);
    for (const key of variants) expect(client.getQueryState(key)?.isInvalidated).toBe(true);
    for (const key of unrelated) expect(client.getQueryState(key)?.isInvalidated).toBe(false);
    client.clear();
  },
);

it("refreshes every grantee search for the selected record and leaves other records and operations alone", async () => {
  const client = new QueryClient();
  const variants = [
    getSearchBookingConfigurationAccessGranteesQueryKey(7, { q: "Ada" }),
    getSearchBookingConfigurationAccessGranteesQueryKey(7, { q: "Grace", limit: 10 }),
  ];
  const unrelated = [
    getSearchBookingConfigurationAccessGranteesQueryKey(8, { q: "Ada" }),
    getGetBookingConfigurationAccessQueryKey(7),
    keys.catalogue,
  ];
  for (const key of [...variants, ...unrelated]) client.setQueryData(key, {});
  await invalidateBookingListOperation(client, { accessGrantees: 7 });
  for (const key of variants) expect(client.getQueryState(key)?.isInvalidated).toBe(true);
  for (const key of unrelated) expect(client.getQueryState(key)?.isInvalidated).toBe(false);
  client.clear();
});
