import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { searchBookingTargets } from "../bookingConfigurationTargets";

describe("eligible booking targets", () => {
  it("searches with q and reads the docs envelope", async () => {
    const target = { id: 123, globalId: "IN123", name: "Microscope", deleted: false };
    server.use(
      http.get("/api/v2/booking-configuration-targets", ({ request }) => {
        const parameters = new URL(request.url).searchParams;
        if (parameters.get("q") !== "scope" || parameters.has("query")) {
          return new HttpResponse(null, { status: 400 });
        }
        return HttpResponse.json({ docs: [target] });
      }),
    );
    expect(await searchBookingTargets(" scope ", "token")).toEqual([target]);
  });
});
