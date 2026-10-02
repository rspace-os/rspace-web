import { act, renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { BookingWindowDraft } from "@/modules/booking/domain/bookingTime";
import type { BookingFormState } from "../BookingForm";
import type { BookableItemOption } from "../bookableItemOption";
import { useBookingTimelineDraft } from "../useBookingTimelineDraft";

const target: BookableItemOption = {
  configurationId: 7,
  targetId: 123,
  globalId: "IN123",
  name: "Confocal microscope",
  timezone: "Europe/Berlin",
  slotGranularityMinutes: 5,
  openingStart: "06:00",
  openingEnd: "22:00",
  openDays: [1, 2, 3, 4, 5, 6, 7],
  openingExceptions: [],
  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
  maxBookingDurationMinutes: 0,
  allowDoubleBooking: false,
};

const firstDraft: BookingWindowDraft = {
  startDate: "2026-10-19",
  startTime: "09:00",
  startOccurrence: "earlier",
  endDate: "2026-10-19",
  endTime: "10:00",
  endOccurrence: "later",
};

const secondDraft: BookingWindowDraft = {
  ...firstDraft,
  startTime: "09:05",
  endTime: "10:05",
};

function state(draft: BookingWindowDraft, targetGlobalId = target.globalId): BookingFormState {
  return {
    target: { ...target, globalId: targetGlobalId },
    draft,
    window: { start: "2026-10-19T07:00:00Z", end: "2026-10-19T08:00:00Z" },
    enteredWindow: { start: "2026-10-19T07:00:00Z", end: "2026-10-19T08:00:00Z" },
    purpose: "Keep this purpose",
    eventKind: "BOOKING",
    dirty: true,
  };
}

describe("useBookingTimelineDraft", () => {
  it("keeps the latest range pending through an older acknowledgement and waits for the matching form state", () => {
    const { result } = renderHook(() => useBookingTimelineDraft());

    act(() => {
      result.current.onTimelineChange(firstDraft, target.globalId);
      result.current.onTimelineChange(secondDraft, target.globalId);
      result.current.onWindowAdjustmentApplied(firstDraft, target.globalId);
      result.current.onStateChange(state(firstDraft));
    });

    expect(result.current.adjustmentPending).toBe(true);
    expect(result.current.draft).toEqual(secondDraft);

    act(() => result.current.onWindowAdjustmentApplied(secondDraft, target.globalId));
    act(() => result.current.onStateChange(state(secondDraft)));
    expect(result.current.adjustmentPending).toBe(false);
    expect(result.current.draft).toEqual(secondDraft);
  });

  it("requires matching repeated-hour occurrence fields before acknowledging", () => {
    const { result } = renderHook(() => useBookingTimelineDraft());
    const occurrenceMismatch = { ...firstDraft, startOccurrence: "later" as const };

    act(() => {
      result.current.onTimelineChange(firstDraft, target.globalId);
      result.current.onWindowAdjustmentApplied(occurrenceMismatch, target.globalId);
      result.current.onStateChange(state(occurrenceMismatch));
    });

    expect(result.current.adjustmentPending).toBe(true);
    expect(result.current.draft).toEqual(firstDraft);
  });

  it("lets a later manual field edit replace the pending timeline range", () => {
    const { result } = renderHook(() => useBookingTimelineDraft());

    act(() => {
      result.current.onTimelineChange(firstDraft, target.globalId);
      result.current.onDraftChange(secondDraft, target.globalId);
      result.current.onStateChange(state(secondDraft));
      result.current.onWindowAdjustmentApplied(firstDraft, target.globalId);
    });

    expect(result.current.adjustmentPending).toBe(false);
    expect(result.current.draft).toEqual(secondDraft);
  });

  it("clears a pending range when the target changes and ignores an acknowledgement from the old target", () => {
    const { result } = renderHook(() => useBookingTimelineDraft());
    const nextDraft = { ...secondDraft, startTime: "10:00", endTime: "11:00" };

    act(() => {
      result.current.onTimelineChange(firstDraft, target.globalId);
      result.current.onTargetChange();
      result.current.onTimelineChange(nextDraft, "IN456");
      result.current.onWindowAdjustmentApplied(firstDraft, target.globalId);
      result.current.onWindowAdjustmentApplied(nextDraft, target.globalId);
    });

    expect(result.current.adjustmentPending).toBe(true);
    expect(result.current.draft).toEqual(nextDraft);

    act(() => {
      result.current.onStateChange(state(nextDraft, "IN456"));
      result.current.onWindowAdjustmentApplied(nextDraft, "IN456");
    });
    expect(result.current.adjustmentPending).toBe(false);
  });
});
