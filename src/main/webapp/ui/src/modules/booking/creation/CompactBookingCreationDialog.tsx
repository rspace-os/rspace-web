import * as React from "react";
import { ActiveBookingCreationDialog } from "./ActiveBookingCreationDialog";
import { useBookingCreationStore } from "./bookingCreationStore";

export function CompactBookingCreationDialog() {
  const creation = useBookingCreationStore((state) => state.activeCreation);
  const endCreation = useBookingCreationStore((state) => state.endCreation);
  React.useEffect(
    () => () => {
      if (creation) endCreation(creation.ownerId);
    },
    [creation, endCreation],
  );

  return creation ? <ActiveBookingCreationDialog key={creation.ownerId} creation={creation} /> : null;
}
