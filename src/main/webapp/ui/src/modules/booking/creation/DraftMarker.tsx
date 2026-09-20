import type { BookingFormState } from "@/modules/booking/creation/BookingForm";
import type { BookingCreationContext } from "@/modules/booking/creation/bookingCreationStore";
import { TimelineWindowEditor } from "@/modules/booking/creation/TimelineWindowEditor";

export function DraftMarker({
  creation,
  draft,
  timeZone,
  snapIncrementMinutes = 5,
  onChange,
}: {
  creation: BookingCreationContext;
  draft: BookingFormState["draft"];
  timeZone: string;
  snapIncrementMinutes?: number;
  onChange?: (draft: BookingFormState["draft"]) => void;
}) {
  const trigger = document.getElementById(creation.triggerId);
  const originDate = creation.initialDate;
  if (!trigger || !originDate) return null;
  return (
    <TimelineWindowEditor
      anchor={trigger}
      date={originDate}
      timezone={timeZone}
      draft={draft}
      snapIncrementMinutes={snapIncrementMinutes}
      onChange={onChange}
      tone={creation.eventKind === "MAINTENANCE" ? "maintenance" : "booking"}
      testId="compact-booking-draft-marker"
      abovePopovers={false}
    />
  );
}
