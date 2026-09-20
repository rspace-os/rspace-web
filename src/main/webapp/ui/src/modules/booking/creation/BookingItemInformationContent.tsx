import { useTranslation } from "react-i18next";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { CardContent } from "@/modules/common/ui/card";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { Separator } from "@/modules/common/ui/separator";

export function BookingItemInformationContent({
  item,
  displayTimezone,
}: {
  item: BookableItemOption;
  displayTimezone: string;
}) {
  const { t } = useTranslation("booking");
  const facts: Array<[string, string]> = [
    [t("bookableItemDetails.fields.openingHours"), `${item.openingStart}–${item.openingEnd}`],
    [
      t("bookableItemDetails.fields.granularity"),
      t("bookableItemDetails.minutes", { count: item.slotGranularityMinutes }),
    ],
    [
      t("bookableItemDetails.fields.maximumDuration"),
      item.maxBookingDurationMinutes === 0
        ? t("bookableItemDetails.unlimited")
        : t("bookableItemDetails.minutes", { count: item.maxBookingDurationMinutes }),
    ],
    [
      t("bookings.itemInformation.doubleBookingAllowed"),
      item.allowDoubleBooking ? t("bookableItemDetails.yes") : t("bookableItemDetails.no"),
    ],
  ];
  if (item.timezone !== displayTimezone) {
    facts.splice(1, 0, [t("bookableItemDetails.fields.timezone"), item.timezone]);
  }
  const buffer: string[] = [];
  if (item.bufferBeforeMinutes > 0) {
    buffer.push(t("bookings.itemInformation.bufferBefore", { count: item.bufferBeforeMinutes }));
  }
  if (item.bufferAfterMinutes > 0) {
    buffer.push(t("bookings.itemInformation.bufferAfter", { count: item.bufferAfterMinutes }));
  }
  if (buffer.length > 0) facts.push([t("bookings.itemInformation.buffer"), buffer.join(", ")]);

  return (
    <>
      <CardContent className="py-2">
        <InventoryItem
          name={item.name}
          globalId={item.globalId}
          href={`/globalId/${item.globalId}`}
          idLinkLabel={t("bookings.form.openItem", { globalId: item.globalId })}
          size="sm"
          className="border-0 p-0"
        />
      </CardContent>
      <Separator className="h-px" />
      <CardContent className="py-4">
        <dl className="grid grid-cols-2 items-baseline gap-x-4 gap-y-2 text-sm">
          {facts.map(([label, value]) => (
            <div className="contents" key={label}>
              <dt className="text-muted-foreground">{label}</dt>
              <dd className="min-w-0 break-words text-right font-medium">{value}</dd>
            </div>
          ))}
        </dl>
      </CardContent>
    </>
  );
}
