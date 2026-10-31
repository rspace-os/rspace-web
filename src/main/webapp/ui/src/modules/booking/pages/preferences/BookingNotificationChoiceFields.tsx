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
  const autoSubscribe = labelKey === "notificationSubscriptions.preferences.autoSubscribe.label";
  const key = autoSubscribe
    ? "booking:notificationSubscriptions.preferences.autoSubscribe.label"
    : "booking:notificationSubscriptions.item.label";
  const label = autoSubscribe
    ? t("notificationSubscriptions.preferences.autoSubscribe.label")
    : labelKey === "notificationSubscriptions.item.label"
      ? t("notificationSubscriptions.item.label")
      : labelKey;
  const fieldDescription =
    descriptionKey === "notificationSubscriptions.preferences.autoSubscribe.description"
      ? t("notificationSubscriptions.preferences.autoSubscribe.description")
      : descriptionKey;
  const fields = resolveCollectionConfig<BookingNotificationChoice>({
    slug: "booking-notification-choice",
    idField: "choice",
    useAsTitle: "choice",
    labels: { singularKey: key, pluralKey: key },
    defaultColumns: ["choice"],
    fields: [
      {
        name: "choice",
        type: "select",
        labelKey: key,
        label,
        form: { widget: "radio", ...(fieldDescription ? { description: fieldDescription } : {}) },
        options: [
          { label: t("notificationSubscriptions.options.on"), value: "ON" },
          { label: t("notificationSubscriptions.options.off"), value: "OFF" },
        ],
      },
    ],
  }).fields;

  return <RenderFields fields={fields} form={form} disabled={disabled} />;
}
