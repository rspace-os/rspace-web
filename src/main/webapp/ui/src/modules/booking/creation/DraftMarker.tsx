import { type BookingFormState } from "./BookingForm";
import { type BookingCreationContext } from "./bookingCreationStore";
import { TimelineWindowEditor } from "./TimelineWindowEditor";
import { type TimelineMovePolicy } from "./timelineMove";

export function DraftMarker({
  creation,
  draft,
  timeZone,
  schedulingTimezone = timeZone,
  snapIncrementMinutes = 5,
  movementPolicy,
  onChange,
}: {
  creation: BookingCreationContext;
  draft: BookingFormState["draft"];
  timeZone: string;
  schedulingTimezone?: string;
  snapIncrementMinutes?: number;
  movementPolicy?: TimelineMovePolicy;
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
      schedulingTimezone={schedulingTimezone}
      draft={draft}
      snapIncrementMinutes={snapIncrementMinutes}
      movementPolicy={movementPolicy}
      onChange={onChange}
      tone={creation.eventKind === "MAINTENANCE" ? "maintenance" : "booking"}
      testId="compact-booking-draft-marker"
    />
  );
}
