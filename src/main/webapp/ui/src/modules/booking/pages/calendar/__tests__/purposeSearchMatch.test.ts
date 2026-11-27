import { describe, expect, it } from "vitest";
import { purposeSearchMatch } from "../CalendarAgenda";

describe("purposeSearchMatch", () => {
  it("marks a case-insensitive match in a short purpose and keeps the whole purpose", () => {
    expect(purposeSearchMatch("Spring Aurora imaging run", "  aurora ", "en-US")).toEqual({
      before: "Spring ",
      match: "Aurora",
      after: " imaging run",
      truncatedStart: false,
      truncatedEnd: false,
    });
  });

  it("windows a long purpose around the first match", () => {
    const purpose = `${"a".repeat(200)} Aurora ${"b".repeat(200)} Aurora`;
    const match = purposeSearchMatch(purpose, "Aurora", "en-US");
    expect(match).toMatchObject({ match: "Aurora", truncatedStart: true, truncatedEnd: true });
    expect(match?.before).toHaveLength(30);
    expect(match?.after).toHaveLength(90);
  });

  it("finds nothing without a search, a purpose, or a match in the purpose", () => {
    expect(purposeSearchMatch("Aurora", "   ", "en-US")).toBeUndefined();
    expect(purposeSearchMatch(null, "Aurora", "en-US")).toBeUndefined();
    expect(purposeSearchMatch("Calibration", "Aurora", "en-US")).toBeUndefined();
  });
});
