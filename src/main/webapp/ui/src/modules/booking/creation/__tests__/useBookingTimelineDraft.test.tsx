import { act, renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { BookingWindowDraft } from "@/modules/booking/domain/bookingTime";
import type { BookingFormState } from "../BookingForm";
import { useBookingTimelineDraft } from "../useBookingTimelineDraft";
import { confocalTargetWithHours } from "./creationTestFixtures";

const target = confocalTargetWithHours("06:00", "22:00");

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
  it("keeps the latest range pending until the matching form state is reported", () => {
    const { result } = renderHook(() => useBookingTimelineDraft());

    act(() => {
      result.current.onTimelineChange(firstDraft, target.globalId);
      result.current.onTimelineChange(secondDraft, target.globalId);
      result.current.onStateChange(state(firstDraft));
    });

    expect(result.current.adjustmentPending).toBe(true);
    expect(result.current.draft).toEqual(secondDraft);

    act(() => result.current.onStateChange(state(secondDraft)));
    expect(result.current.adjustmentPending).toBe(false);
    expect(result.current.draft).toEqual(secondDraft);
  });

  it("requires matching repeated-hour occurrence fields before accepting the form state", () => {
    const { result } = renderHook(() => useBookingTimelineDraft());
    const occurrenceMismatch = { ...firstDraft, startOccurrence: "later" as const };

    act(() => {
      result.current.onTimelineChange(firstDraft, target.globalId);
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
    });

    expect(result.current.adjustmentPending).toBe(false);
    expect(result.current.draft).toEqual(secondDraft);
  });
});
