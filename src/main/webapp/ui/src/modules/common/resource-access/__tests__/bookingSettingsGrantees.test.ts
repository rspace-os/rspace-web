import { HttpResponse, http } from "msw";
import { expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { searchBookingSettingsGrantees } from "../resourceAccess";

it("searches settings grantees with q and reads the bounded docs response", async () => {
  server.use(
    http.get("/api/v2/booking-settings/access-grantees", ({ request }) => {
      const params = new URL(request.url).searchParams;
      if (params.get("q") !== "Alice" || params.has("query")) return new HttpResponse(null, { status: 400 });
      return HttpResponse.json({ docs: [] });
    }),
  );
  await expect(searchBookingSettingsGrantees("Alice", "token")).resolves.toEqual([]);
});
