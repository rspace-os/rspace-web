import { expect, it } from "vitest";
import { DEFAULT_SCHEDULING_SETTINGS } from "@/modules/booking/configuration/schedulingSettingsSchema";
import type { BookingConfigurationsRead } from "@/modules/common/api/v2/generated/models/bookingConfigurationsRead";
import { mapBookingConfiguration } from "../../pages/bookable-items/bookingConfigurationSchema";

it("preserves projected runtime fields on the configuration and its instrument", () => {
  const customFields = { "17": "Screening" };
  const extraFields = { "19": { type: "Text", value: "Floor 2" } };
  const document: BookingConfigurationsRead = {
    id: 7,
    configurationVersion: 0,
    enabled: true,
    state: "ACTIVE",
    timezone: "UTC",
    ...DEFAULT_SCHEDULING_SETTINGS,
    "target.customFields": customFields,
    target: {
      relationTo: "instruments",
      globalId: "IN123",
      value: { id: 123, name: "Confocal", deleted: false, parentContainer: null, customFields, extraFields },
    },
  };
  expect(mapBookingConfiguration(document)).toMatchObject({
    "target.customFields": customFields,
    target: { value: { customFields, extraFields } },
  });
});
