import { useId } from "react";
import { useTranslation } from "react-i18next";
import { Card, CardContent, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { InventoryLocationLink } from "@/modules/common/ui/inventory-item";
import { UserBadge } from "@/modules/common/ui/user-badge";
import { formatBookingEventDateTime } from "../bookings/BookingEventContext";
import type { BookingConfiguration } from "./bookingConfiguration";

export function BookableItemFactsAside({
  configuration,
  displayTimeZone,
}: {
  configuration: BookingConfiguration;
  displayTimeZone: string;
}) {
  const { t, i18n } = useTranslation("booking");
  const headingId = useId();
  const target = configuration.target;
  const facts = [
    {
      id: "location",
      label: t("bookableItemDetails.fields.location"),
      value:
        target?.value.parentContainer && typeof target.value.parentContainer.value === "object" ? (
          <InventoryLocationLink
            name={target.value.parentContainer.value.name}
            globalId={target.value.parentContainer.globalId}
            compact
          />
        ) : (
          t("bookableItemDetails.notAvailable")
        ),
    },
    {
      id: "createdBy",
      label: t("bookableItemDetails.fields.createdBy"),
      value: configuration.createdByName ? (
        <UserBadge name={configuration.createdByName} />
      ) : (
        t("bookableItemDetails.notAvailable")
      ),
    },
    {
      id: "createdAt",
      label: t("bookableItemDetails.fields.createdAt"),
      value: configuration.createdAt ? (
        <time dateTime={configuration.createdAt}>
          {formatBookingEventDateTime(configuration.createdAt, displayTimeZone, i18n.language)}
        </time>
      ) : (
        t("bookableItemDetails.notAvailable")
      ),
    },
  ];

  return (
    <aside data-slot="bookable-item-facts" aria-labelledby={headingId} className="min-w-0">
      <Card size="sm">
        <CardHeader>
          <CardTitle id={headingId}>{t("bookableItemDetails.about")}</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="space-y-3 text-sm">
            {facts.map(({ id, label, value }) => (
              <div key={id}>
                <dt className="text-muted-foreground">{label}</dt>
                <dd className="min-w-0">{value}</dd>
              </div>
            ))}
          </dl>
        </CardContent>
      </Card>
    </aside>
  );
}
