import { QueryClient } from "@tanstack/react-query";
import { HttpResponse, http } from "msw";
import { expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { eligibleBookingTargetsQuery } from "../bookingConfigurationTargets";

it("shares completed and in-flight administration lookups for the same user", async () => {
  const requests = { count: 0, targets: 0 };
  server.use(
    http.get("/api/v2/booking-configurations/count", () => {
      requests.count += 1;
      return HttpResponse.json({ totalDocs: 1 });
    }),
    http.get("/api/v2/booking-configuration-targets", () => {
      requests.targets += 1;
      return HttpResponse.json({ docs: [] });
    }),
  );
  const client = new QueryClient();
  const load = () => Promise.all([client.fetchQuery(eligibleBookingTargetsQuery(42, "token"))]);
  await Promise.all([load(), load()]);
  await load();
  expect(requests).toEqual({ count: 0, targets: 1 });
  client.clear();
});
