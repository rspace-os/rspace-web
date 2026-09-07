import { describe, expect, it } from "vitest";
import { bookingCreationDraftFromHistoryState } from "../bookingCreationDraft";

const draft = {
  targetGlobalId: "IN123",
  window: {
    startDate: "2026-10-25",
    startTime: "02:30",
    startOccurrence: "later",
    endDate: "2026-10-25",
    endTime: "04:00",
  },
  purpose: "Confocal imaging",
};

describe("bookingCreationDraftFromHistoryState", () => {
  it("copies a matching draft, including an ambiguous-time occurrence", () => {
    expect(bookingCreationDraftFromHistoryState({ bookingCreationDraft: draft }, "IN123")).toEqual(draft);
  });

  it("does not apply a draft to another target", () => {
    expect(bookingCreationDraftFromHistoryState({ bookingCreationDraft: draft }, "IN124")).toBeUndefined();
  });

  it.each([
    { ...draft, purpose: "x".repeat(1001) },
    { ...draft, targetGlobalId: "SA123" },
    { ...draft, window: { ...draft.window, startDate: "2026-02-30" } },
    { ...draft, window: { ...draft.window, startTime: "25:00" } },
    { ...draft, window: { ...draft.window, startOccurrence: "middle" } },
  ])("rejects malformed browser history state", (malformed) => {
    expect(
      bookingCreationDraftFromHistoryState({ bookingCreationDraft: malformed }, malformed.targetGlobalId),
    ).toBeUndefined();
  });
});
