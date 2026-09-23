import type { FormStore } from "@formisch/react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { RenderFields } from "@/modules/common/collection-form/RenderFields";

export const BookingNotificationChoiceSchema = v.object({ choice: v.picklist(["ON", "OFF"]) });
export type BookingNotificationChoice = v.InferOutput<typeof BookingNotificationChoiceSchema>;

export function bookingNotificationChoice(enabled: boolean): BookingNotificationChoice {
  return { choice: enabled ? "ON" : "OFF" };
}

export function BookingNotificationChoiceFields({
  form,
  labelKey,
  descriptionKey,
  disabled = false,
}: {
  form: FormStore;
  labelKey: string;
  descriptionKey?: string;
  disabled?: boolean;
}) {
  const { t } = useTranslation("booking");
  const fields = resolveCollectionConfig<BookingNotificationChoice>({
    slug: "booking-notification-choice",
    idField: "choice",
    useAsTitle: "choice",
    labels: { singularKey: `booking:${labelKey}`, pluralKey: `booking:${labelKey}` },
    defaultColumns: ["choice"],
    fields: [
      {
        name: "choice",
        type: "select",
        labelKey: `booking:${labelKey}`,
        form: { widget: "radio", ...(descriptionKey ? { descriptionKey: `booking:${descriptionKey}` } : {}) },
        options: [
          { label: t("notificationSubscriptions.options.on"), value: "ON" },
          { label: t("notificationSubscriptions.options.off"), value: "OFF" },
        ],
      },
    ],
  }).fields;

  return <RenderFields fields={fields} form={form} disabled={disabled} />;
}
