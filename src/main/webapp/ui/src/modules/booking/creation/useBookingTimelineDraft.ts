import { useCallback, useState } from "react";
import type { BookingWindowDraft } from "@/modules/booking/domain/bookingTime";
import type { BookingFormState } from "./BookingForm";

type PendingAdjustment = {
  draft: BookingWindowDraft;
  targetGlobalId: string;
};

function sameDraft(left: BookingWindowDraft, right: BookingWindowDraft): boolean {
  return (
    left.startDate === right.startDate &&
    left.startTime === right.startTime &&
    left.startOccurrence === right.startOccurrence &&
    left.endDate === right.endDate &&
    left.endTime === right.endTime &&
    left.endOccurrence === right.endOccurrence
  );
}

export function useBookingTimelineDraft() {
  const [formState, setFormState] = useState<BookingFormState | undefined>(undefined);
  const [pendingAdjustment, setPendingAdjustment] = useState<PendingAdjustment | undefined>(undefined);
  const [interactionActive, setInteractionActive] = useState(false);

  const onStateChange = useCallback((state: BookingFormState) => {
    setFormState(state);
    setPendingAdjustment((pending) => {
      if (!pending) return pending;
      if (state.target?.globalId !== pending.targetGlobalId) return undefined;
      if (!sameDraft(state.draft, pending.draft)) return pending;
      return undefined;
    });
  }, []);

  const onDraftChange = useCallback((draft: BookingWindowDraft, targetGlobalId?: string) => {
    setPendingAdjustment((pending) => {
      if (!pending || pending.targetGlobalId !== targetGlobalId || sameDraft(pending.draft, draft)) return pending;
      return undefined;
    });
  }, []);

  const onTargetChange = useCallback(() => {
    setPendingAdjustment(undefined);
    setInteractionActive(false);
  }, []);

  const onInteractionChange = useCallback((active: boolean) => setInteractionActive(active), []);

  const onTimelineChange = useCallback((draft: BookingWindowDraft, targetGlobalId: string | undefined) => {
    if (!targetGlobalId) return;
    setPendingAdjustment({ draft, targetGlobalId });
  }, []);

  return {
    formState,
    draft: pendingAdjustment?.draft ?? formState?.draft,
    windowAdjustment: pendingAdjustment?.draft,
    windowAdjustmentTarget: pendingAdjustment?.targetGlobalId,
    adjustmentPending: pendingAdjustment !== undefined,
    interactionActive,
    onStateChange,
    onDraftChange,
    onTargetChange,
    onInteractionChange,
    onTimelineChange,
  };
}
