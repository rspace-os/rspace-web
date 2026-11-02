import { describe, expect, it } from "vitest";
import { purposeSearchMatch } from "../CalendarAgenda";

describe("purposeSearchMatch", () => {
  it("marks a case-insensitive match in a short purpose and keeps the whole purpose", () => {
    expect(purposeSearchMatch("Spring Aurora imaging run", "  aurora ")).toEqual({
      before: "Spring ",
      match: "Aurora",
      after: " imaging run",
      truncatedStart: false,
      truncatedEnd: false,
    });
  });

  it("windows a long purpose around the first match", () => {
    const purpose = `${"a".repeat(200)} Aurora ${"b".repeat(200)} Aurora`;
    const match = purposeSearchMatch(purpose, "Aurora");
    expect(match).toMatchObject({ match: "Aurora", truncatedStart: true, truncatedEnd: true });
    expect(match?.before).toHaveLength(30);
    expect(match?.after).toHaveLength(90);
  });

  it("finds nothing without a search, a purpose, or a match in the purpose", () => {
    expect(purposeSearchMatch("Aurora", "   ")).toBeUndefined();
    expect(purposeSearchMatch(null, "Aurora")).toBeUndefined();
    expect(purposeSearchMatch("Calibration", "Aurora")).toBeUndefined();
  });
});
