import { type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { openingHoursFacts } from "@/modules/booking/configuration/openingHoursFacts";
import {
  RESPONSIVE_INLINE_FIELD_CONTAINER_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_GRID_CLASS_NAME,
  RESPONSIVE_INLINE_FIELD_ROW_CLASS_NAME,
} from "@/modules/common/collection-form/responsiveFieldLayout";
import { type BookingConfiguration } from "./bookingConfiguration";

export function BookableItemRulesReadOut({
  configuration,
  displayTimeZone,
}: {
  configuration: BookingConfiguration;
  displayTimeZone: string;
}) {
  const { t, i18n } = useTranslation("booking");
  const updatedAt =
    configuration.updatedAt === null || configuration.updatedAt === undefined ? (
      t("bookableItemDetails.notAvailable")
    ) : (
      <time dateTime={configuration.updatedAt}>
        {new Intl.DateTimeFormat(i18n.language, {
          dateStyle: "medium",
          timeStyle: "short",
          timeZone: displayTimeZone,
        }).format(new Date(configuration.updatedAt))}
      </time>
    );
  const facts: Array<[string, ReactNode]> = [
    [t("bookableItemDetails.fields.timezone"), configuration.timezone],
    [t("bookableItemDetails.fields.updatedAt"), updatedAt],
    ...openingHoursFacts(configuration, t),
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
