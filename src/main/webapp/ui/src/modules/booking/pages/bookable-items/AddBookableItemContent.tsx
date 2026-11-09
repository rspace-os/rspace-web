import { useSuspenseQuery } from "@tanstack/react-query";
import { CatchBoundary, useSearch } from "@tanstack/react-router";
import { bookingSettingsQuery } from "@/modules/booking/configuration/schedulingSettingsApi";
import type { BookingSettings } from "@/modules/booking/configuration/schedulingSettingsSchema";
import { AddBookableItemForm } from "./AddBookableItemForm";
import { bookingRouteTargetSuspenseQuery } from "./bookingConfigurationApi";

function AddBookableItemTargetForm({ target, defaults }: { target: string; defaults: BookingSettings }) {
  const initialTarget = useSuspenseQuery(bookingRouteTargetSuspenseQuery(target)).data.find(
    (option) => option.globalId === target,
  );
  return <AddBookableItemForm key={target} defaults={defaults} initialTargetId={initialTarget?.id} />;
}

export function AddBookableItemContent() {
  const defaults = useSuspenseQuery(bookingSettingsQuery()).data;
  const search = useSearch({ from: "/booking/bookable-items/add/" });
  const target = search.target;
  if (target === undefined) return <AddBookableItemForm key="" defaults={defaults} />;
  return (
    // An unreadable route target only loses the preselection; the form still opens.
    <CatchBoundary
      getResetKey={() => target}
      errorComponent={() => <AddBookableItemForm key={target} defaults={defaults} />}
    >
      <AddBookableItemTargetForm target={target} defaults={defaults} />
    </CatchBoundary>
  );
}
