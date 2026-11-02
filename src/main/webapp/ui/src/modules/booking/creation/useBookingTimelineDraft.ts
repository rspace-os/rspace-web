import { useCallback, useRef, useState } from "react";
import { type BookingWindowDraft, sameDraft } from "@/modules/booking/domain/bookingTime";
import type { BookingFormState } from "./BookingForm";

type PendingAdjustment = {
  draft: BookingWindowDraft;
  targetGlobalId: string;
  acknowledged: boolean;
  observed: boolean;
};

export function useBookingTimelineDraft() {
  const [formState, setFormState] = useState<BookingFormState | undefined>(undefined);
  // Keep the latest timeline draft until the same target's form echoes every field, including occurrences.
  // Manual edits or a target change supersede it.
  const [pendingAdjustment, setPendingAdjustment] = useState<PendingAdjustment | undefined>(undefined);
  const [interactionActive, setInteractionActive] = useState(false);
  const pendingRef = useRef<PendingAdjustment | undefined>(undefined);
  const replacePending = useCallback((next: PendingAdjustment | undefined) => {
    pendingRef.current = next;
    setPendingAdjustment(next);
  }, []);

  const onStateChange = useCallback(
    (state: BookingFormState) => {
      setFormState(state);
      const pending = pendingRef.current;
      if (!pending) return;
      if (state.target?.globalId !== pending.targetGlobalId) {
        replacePending(undefined);
        return;
      }
      if (!sameDraft(state.draft, pending.draft)) return;
      const observed = { ...pending, observed: true };
      replacePending(observed.acknowledged ? undefined : observed);
    },
    [replacePending],
  );

  const onWindowAdjustmentApplied = useCallback(
    (draft: BookingWindowDraft, targetGlobalId?: string) => {
      const pending = pendingRef.current;
      if (!pending || pending.targetGlobalId !== targetGlobalId || !sameDraft(pending.draft, draft)) {
        return;
      }
      const acknowledged = { ...pending, acknowledged: true };
      replacePending(acknowledged.observed ? undefined : acknowledged);
    },
    [replacePending],
  );

  const onDraftChange = useCallback(
    (draft: BookingWindowDraft, targetGlobalId?: string) => {
      const pending = pendingRef.current;
      if (!pending || pending.targetGlobalId !== targetGlobalId || sameDraft(pending.draft, draft)) return;
      replacePending(undefined);
    },
    [replacePending],
  );

  const onTargetChange = useCallback(() => {
    replacePending(undefined);
    setInteractionActive(false);
  }, [replacePending]);

  const onInteractionChange = useCallback((active: boolean) => setInteractionActive(active), []);

  const onTimelineChange = useCallback(
    (draft: BookingWindowDraft, targetGlobalId: string | undefined) => {
      if (!targetGlobalId) return;
      replacePending({ draft, targetGlobalId, acknowledged: false, observed: false });
    },
    [replacePending],
  );

  return {
    formState,
    draft: pendingAdjustment?.draft ?? formState?.draft,
    windowAdjustment: pendingAdjustment?.draft,
    windowAdjustmentTarget: pendingAdjustment?.targetGlobalId,
    /** The form has yet to acknowledge the latest timeline change; this is not a network request. */
    adjustmentPending: pendingAdjustment !== undefined,
    interactionActive,
    onStateChange,
    onWindowAdjustmentApplied,
    onDraftChange,
    onTargetChange,
    onInteractionChange,
    onTimelineChange,
  };
}
