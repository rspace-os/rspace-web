import { useEffect } from "react";
import { ActiveBookingCreationDialog } from "@/modules/booking/creation/ActiveBookingCreationDialog";
import { useBookingCreationStore } from "@/modules/booking/creation/bookingCreationStore";

export { DraftMarker } from "@/modules/booking/creation/DraftMarker";

export function CompactBookingCreationDialog() {
  const creation = useBookingCreationStore((state) => state.activeCreation);
  const endCreation = useBookingCreationStore((state) => state.endCreation);
  useEffect(
    () => () => {
      if (creation) endCreation(creation.ownerId);
    },
    [creation, endCreation],
  );

  return creation ? <ActiveBookingCreationDialog key={creation.ownerId} creation={creation} /> : null;
}
