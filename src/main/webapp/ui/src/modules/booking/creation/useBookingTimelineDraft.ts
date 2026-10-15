import { useCallback, useState } from "react";
import { type BookingWindowDraft, sameDraft } from "@/modules/booking/domain/bookingTime";
import type { BookingFormState } from "./BookingForm";

type PendingAdjustment = {
  draft: BookingWindowDraft;
  targetGlobalId: string;
};

export function useBookingTimelineDraft() {
  const [formState, setFormState] = useState<BookingFormState | undefined>(undefined);
  // Keep the latest timeline draft until the same target's form echoes every field, including occurrences.
  // Manual edits or a target change supersede it.
  const [pendingAdjustment, setPendingAdjustment] = useState<PendingAdjustment | undefined>(undefined);
  const [interactionActive, setInteractionActive] = useState(false);

  const onStateChange = useCallback((state: BookingFormState) => {
    setFormState(state);
    setPendingAdjustment((pending) =>
      pending && state.target?.globalId === pending.targetGlobalId && !sameDraft(state.draft, pending.draft)
        ? pending
        : undefined,
    );
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
    /** The form has yet to acknowledge the latest timeline change; this is not a network request. */
    adjustmentPending: pendingAdjustment !== undefined,
    interactionActive,
    onStateChange,
    onDraftChange,
    onTargetChange,
    onInteractionChange,
    onTimelineChange,
  };
}
