import { QueryClient } from "@tanstack/react-query";
import { HttpResponse, http } from "msw";
import { expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { getCountBookingConfigurationsQueryOptions } from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import type { ApiV2CountResult } from "@/modules/common/api/v2/generated/models/apiV2CountResult";
import type { TargetDirectoryDocument } from "@/modules/common/api/v2/generated/models/targetDirectoryDocument";
import { eligibleBookingTargetsQuery } from "../bookingConfigurationTargets";

it("shares completed and in-flight administration lookups for the same user", async () => {
  const requests = { count: 0, targets: 0 };
  server.use(
    http.get("/api/v2/booking-configurations/count", () => {
      requests.count += 1;
      return HttpResponse.json<ApiV2CountResult>({ totalDocs: 1 });
    }),
    http.get("/api/v2/booking-configuration-targets", () => {
      requests.targets += 1;
      return HttpResponse.json<TargetDirectoryDocument>({ docs: [] });
    }),
  );
  const client = new QueryClient();
  const load = () =>
    Promise.all([
      client.fetchQuery(
        getCountBookingConfigurationsQueryOptions({ max: 1 }, { query: { staleTime: 10 * 60 * 1000 } }),
      ),
      client.fetchQuery(eligibleBookingTargetsQuery(42)),
    ]);
  await Promise.all([load(), load()]);
  await load();
  expect(requests).toEqual({ count: 1, targets: 1 });
  client.clear();
});
