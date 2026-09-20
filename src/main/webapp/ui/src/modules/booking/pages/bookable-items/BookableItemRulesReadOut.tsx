import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import {
  RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME,
} from "@/modules/common/collection-form/responsiveFieldLayout";
import type { BookingConfiguration } from "./bookingConfiguration";

export function BookableItemRulesReadOut({ configuration }: { configuration: BookingConfiguration }) {
  const { t } = useTranslation("booking");
  const facts: Array<[string, ReactNode]> = [
    [t("bookableItemDetails.fields.timezone"), configuration.timezone],
    [t("bookableItemDetails.fields.openingHours"), `${configuration.openingStart}–${configuration.openingEnd}`],
    [
      t("bookableItemDetails.fields.granularity"),
      t("bookableItemDetails.minutes", { count: configuration.slotGranularityMinutes }),
    ],
    [
      t("bookableItemDetails.fields.maximumDuration"),
      configuration.maxBookingDurationMinutes === 0
        ? t("bookableItemDetails.unlimited")
        : t("bookableItemDetails.minutes", { count: configuration.maxBookingDurationMinutes }),
    ],
    [
      t("bookableItemDetails.fields.bufferBefore"),
      t("bookableItemDetails.minutes", { count: configuration.bufferBeforeMinutes }),
    ],
    [
      t("bookableItemDetails.fields.bufferAfter"),
      t("bookableItemDetails.minutes", { count: configuration.bufferAfterMinutes }),
    ],
    [
      t("bookableItemDetails.fields.doubleBooking"),
      configuration.allowDoubleBooking ? t("bookableItemDetails.yes") : t("bookableItemDetails.no"),
    ],
  ];

  return (
    <div className={RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME}>
      <dl className={`${RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME} gap-y-4`}>
        {facts.map(([label, value]) => (
          <div className={RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME} key={label}>
            <dt className="font-medium">{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
}
