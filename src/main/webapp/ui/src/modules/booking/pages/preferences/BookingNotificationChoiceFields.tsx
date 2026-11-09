import type { FormStore } from "@formisch/react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import { RenderFields } from "@/modules/common/collection-form/RenderFields";

export const BookingNotificationChoiceSchema = v.object({ choice: v.picklist(["ON", "OFF"]) });
export type BookingNotificationChoice = v.InferOutput<typeof BookingNotificationChoiceSchema>;

export function bookingNotificationChoice(enabled: boolean): BookingNotificationChoice {
  return { choice: enabled ? "ON" : "OFF" };
}

/** The On/Off radio choice; `label` and `description` are translated text. */
export function BookingNotificationChoiceFields({
  form,
  label,
  description,
  disabled = false,
}: {
  form: FormStore;
  label: string;
  description?: string;
  disabled?: boolean;
}) {
  const { t } = useTranslation("booking");
  const fields = resolveCollectionConfig<BookingNotificationChoice>({
    slug: "booking-notification-choice",
    idField: "choice",
    useAsTitle: "choice",
    labels: {
      singularKey: "booking:notificationSubscriptions.item.title",
      pluralKey: "booking:notificationSubscriptions.item.title",
    },
    defaultColumns: ["choice"],
    fields: [
      {
        name: "choice",
        type: "select",
        label,
        form: { widget: "radio", ...(description ? { description } : {}) },
        options: [
          { label: t("notificationSubscriptions.options.on"), value: "ON" },
          { label: t("notificationSubscriptions.options.off"), value: "OFF" },
        ],
      },
    ],
  }).fields;

  return <RenderFields fields={fields} form={form} disabled={disabled} />;
}
