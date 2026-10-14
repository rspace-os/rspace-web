import type { BookingFormState } from "@/modules/booking/creation/BookingForm";
import type { BookingCreationContext } from "@/modules/booking/creation/bookingCreationStore";
import { TimelineWindowEditor } from "@/modules/booking/creation/TimelineWindowEditor";
import { type TimelinePolicy, unrestrictedTimelinePolicy } from "@/modules/booking/creation/timelineRange";

export function DraftMarker({
  creation,
  draft,
  timeZone,
  policy = unrestrictedTimelinePolicy(timeZone),
  onChange,
}: {
  creation: BookingCreationContext;
  draft: BookingFormState["draft"];
  timeZone: string;
  /** The chosen item's rules; without an item the draft is unrestricted. */
  policy?: TimelinePolicy;
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
      policy={policy}
      onChange={onChange}
      tone={creation.eventKind === "MAINTENANCE" ? "maintenance" : "booking"}
      testId="compact-booking-draft-marker"
      abovePopovers={false}
    />
  );
}
