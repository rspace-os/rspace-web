import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Badge } from "@/modules/common/ui/badge";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import type { BookingConfiguration } from "./bookingConfiguration";

export function BookableItemSpotlightHeader({
  configuration,
  target,
  action,
}: {
  configuration: BookingConfiguration;
  target: NonNullable<BookingConfiguration["target"]>;
  action?: ReactNode;
}) {
  const { t } = useTranslation("booking");
  return (
    <section className="flex flex-wrap items-center gap-4">
      <InventoryItem
        name={target.value.name}
        nameAs="h1"
        nameClassName="text-2xl font-semibold"
        globalId={target.globalId}
        idPlacement="title"
        className="min-w-full flex-1 p-0 sm:min-w-64"
      />
      <div
        data-slot="bookable-item-header-actions"
        className="flex w-full min-w-0 flex-wrap items-center gap-3 sm:w-auto sm:shrink-0 [&_[data-slot=badge]]:h-[30px] [&_button]:h-[30px] [&_button]:min-h-[30px]"
      >
        {configuration.state !== "ARCHIVED" ? (
          <Badge variant={configuration.enabled ? "default" : "secondary"}>
            {configuration.enabled ? t("bookableItemDetails.enabled") : t("bookableItemDetails.disabled")}
          </Badge>
        ) : null}
        {configuration.state === "ARCHIVED" ? (
          <Badge variant="secondary">{t("bookableItemDetails.archived")}</Badge>
        ) : null}
        {action}
      </div>
    </section>
  );
}
