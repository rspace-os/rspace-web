import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { bookingCatalogueAvailabilityCountsQuery } from "@/modules/booking/domain/bookingCatalogue";

/** Today's server availability counts; the Calendar's Availability layout replaces this panel. */
export function AllBookableItemsAvailabilityPanel({ clock = () => new Date() }: { clock?: () => Date }) {
  const { t } = useTranslation("booking");
  const [now] = useState(clock);
  const start = new Date(now);
  start.setHours(0, 0, 0, 0);
  const end = new Date(start);
  end.setDate(end.getDate() + 1);
  const window = { start: start.toISOString(), end: end.toISOString(), now: now.toISOString() };
  const counts = useQuery(bookingCatalogueAvailabilityCountsQuery(null, {}, window));
  if (!counts.data) return null;
  return (
    <ul aria-label={t("allBookableItems.quickFilters.legend")} className="flex gap-4 px-4 pt-4 text-sm sm:px-8">
      <li>{`${t("allBookableItems.quickFilters.availableNow")}: ${counts.data.availableNow}`}</li>
      <li>{`${t("allBookableItems.quickFilters.freeLaterToday")}: ${counts.data.freeLaterToday}`}</li>
    </ul>
  );
}
