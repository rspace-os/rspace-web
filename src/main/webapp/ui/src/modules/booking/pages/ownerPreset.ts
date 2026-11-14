import type { FieldName } from "@/modules/common/collection/config";
import i18n from "@/modules/common/i18n";
import type { BookingListDocument } from "../domain/booking";
import type { BookingConfiguration } from "./bookable-items/bookingConfiguration";
export const ownerPreset = {
  kind: "comparison",
  field: "targetOwner" as FieldName<BookingConfiguration>,
  operator: "equals",
  value: "me",
} as const;
export const requesterValueField = "requester.value" as FieldName<BookingListDocument>;
export const requesterPreset = {
  kind: "comparison",
  field: requesterValueField,
  operator: "equals",
  value: "me",
} as const;
export const ownerPresetField = {
  name: "targetOwner" as FieldName<BookingConfiguration>,
  type: "select",
  labelKey: "booking:filters.owner",
  options: [
    {
      value: "me",
      get label() {
        return i18n.t("booking:filters.me");
      },
    },
  ],
  capabilities: { filterOperators: ["equals"], sortable: false },
  form: false,
} as const;
