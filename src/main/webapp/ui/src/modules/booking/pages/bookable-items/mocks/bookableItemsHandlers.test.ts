import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { listBookingConfigurations } from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import { bookableItemsHandlers } from "./bookableItemsHandlers";

describe("bookableItemsHandlers runtime projections", () => {
  it.each(["target.customFields.SF152", "target.customFields.SF160"])(
    "attaches the selected namespace to every document for %s",
    async (selector) => {
      server.use(...bookableItemsHandlers(() => {}));
      const page = await listBookingConfigurations({ fields: { "booking-configurations": `id,${selector}` } });
      expect(page.docs.map((doc) => doc["target.customFields"])).toEqual([
        { SF152: "BSL-2", SF160: "yes" },
        {},
        {},
        {},
        {},
      ]);
    },
  );

  it("leaves unselected namespaces absent on documents without values", async () => {
    server.use(...bookableItemsHandlers(() => {}));
    const page = await listBookingConfigurations({ fields: { "booking-configurations": "id" } });
    expect(page.docs[1]).not.toHaveProperty(["target.customFields"]);
  });
});
