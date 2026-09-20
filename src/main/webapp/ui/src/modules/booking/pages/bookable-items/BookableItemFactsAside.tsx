import { useId } from "react";
import { useTranslation } from "react-i18next";
import { Card, CardContent, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { InventoryLocationLink } from "@/modules/common/ui/inventory-item";
import { UserBadge } from "@/modules/common/ui/user-badge";
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

  return (
    <aside
      data-slot="bookable-item-facts"
      aria-labelledby={headingId}
      className="min-w-0 @2xl:sticky @2xl:top-4 @2xl:self-start"
    >
      <Card size="sm">
        <CardHeader>
          <CardTitle id={headingId}>{t("bookableItemDetails.about")}</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="space-y-3 text-sm">
            <div>
              <dt className="text-muted-foreground">{t("bookableItemDetails.fields.location")}</dt>
              <dd className="min-w-0">
                {target?.value.parentContainerName && target.value.parentContainerGlobalId ? (
                  <InventoryLocationLink
                    name={target.value.parentContainerName}
                    globalId={target.value.parentContainerGlobalId}
                    compact
                  />
                ) : (
                  t("bookableItemDetails.notAvailable")
                )}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">{t("bookableItemDetails.fields.createdBy")}</dt>
              <dd className="min-w-0">
                {configuration.createdByName ? (
                  <UserBadge name={configuration.createdByName} />
                ) : (
                  t("bookableItemDetails.notAvailable")
                )}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">{t("bookableItemDetails.fields.createdAt")}</dt>
              <dd>
                {configuration.createdAt ? (
                  <time dateTime={configuration.createdAt}>
                    {new Intl.DateTimeFormat(i18n.language, {
                      dateStyle: "medium",
                      timeStyle: "short",
                      timeZone: displayTimeZone,
                    }).format(new Date(configuration.createdAt))}
                  </time>
                ) : (
                  t("bookableItemDetails.notAvailable")
                )}
              </dd>
            </div>
          </dl>
        </CardContent>
      </Card>
    </aside>
  );
}
