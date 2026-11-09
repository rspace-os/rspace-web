import { describe, expect, it } from "vitest";
import { bookingListDocument } from "@/modules/booking/domain/bookingAgendaFixtures";
import { toTimelineEvent } from "./toTimelineEvent";

describe("toTimelineEvent", () => {
  it.each(["BOOKING", "MAINTENANCE"] as const)("preserves configuration access for %s", (kind) => {
    for (const canViewConfiguration of [true, false, undefined]) {
      const event = bookingListDocument({ kind, privacy: "full", canViewConfiguration });
      const timeline = toTimelineEvent(event, "2026-09-24", "UTC");
      if (timeline.kind === "booking" && timeline.privacy === "busy") throw new Error("Expected full details");
      expect(timeline.item?.canViewConfiguration).toBe(Boolean(canViewConfiguration));
    }
  });
});
