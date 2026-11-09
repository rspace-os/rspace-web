import { type InvalidateOptions, QueryClient, QueryObserver } from "@tanstack/react-query";
import { HttpResponse, http } from "msw";
import { expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { invalidateBookableItemRecords } from "@/modules/booking/pages/bookable-items/mutateBookableItems";
import { getSearchBookingCatalogueQueryKey } from "@/modules/common/api/v2/generated/booking-catalogue/booking-catalogue";
import { getSearchBookingConfigurationTargetsQueryKey } from "@/modules/common/api/v2/generated/booking-configuration-targets/booking-configuration-targets";
import { getListBookingConfigurationsQueryKey } from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";

const cases: { name: string; options?: InvalidateOptions; requests: number }[] = [
  { name: "joins the existing fetch", options: { cancelRefetch: false }, requests: 1 },
  { name: "restarts the fetch when options are omitted", requests: 2 },
];

it.each(cases)("$name for generated and predicate invalidators", async ({ options, requests }) => {
  for (const queryKey of [
    getListBookingConfigurationsQueryKey(),
    getSearchBookingCatalogueQueryKey({ mine: false, page: 1, limit: 20 }),
    getSearchBookingConfigurationTargetsQueryKey({ q: "IN7" }),
  ]) {
    const client = new QueryClient();
    const started = Promise.withResolvers<void>();
    const response = Promise.withResolvers<void>();
    let count = 0;
    server.use(
      http.get("http://localhost/record-invalidation-test", async () => {
        count += 1;
        started.resolve();
        await response.promise;
        return HttpResponse.text("fresh");
      }),
    );
    client.setQueryData(queryKey, "old");
    const observer = new QueryObserver(client, {
      queryKey,
      staleTime: Infinity,
      queryFn: async ({ signal }) => (await fetch("http://localhost/record-invalidation-test", { signal })).text(),
    });
    const unsubscribe = observer.subscribe(() => {});
    try {
      const generatedRefresh = client.invalidateQueries({ queryKey });
      await started.promise;
      const manualRefresh = invalidateBookableItemRecords(client, "archive", [{ id: 7 }], options);
      response.resolve();
      await Promise.all([generatedRefresh, manualRefresh]);
      expect(count).toBe(requests);
      expect(observer.getCurrentResult().data).toBe("fresh");
    } finally {
      response.resolve();
      unsubscribe();
      client.clear();
    }
  }
});

it("refreshes eligible targets after a plain bulk request even when the receipt is empty", async () => {
  const client = new QueryClient();
  const key = getSearchBookingConfigurationTargetsQueryKey({ q: "microscope" });
  client.setQueryData(key, {});
  try {
    await invalidateBookableItemRecords(client, "archive", []);
    expect(client.getQueryState(key)?.isInvalidated).toBe(true);
  } finally {
    client.clear();
  }
});
