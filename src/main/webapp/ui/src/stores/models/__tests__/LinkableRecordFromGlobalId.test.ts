import { describe, expect, it } from "vitest";
import LinkableRecordFromGlobalId from "../LinkableRecordFromGlobalId";

describe("LinkableRecordFromGlobalId", () => {
  it("maps instrument global ids to the instrument icon instead of throwing", () => {
    const record = new LinkableRecordFromGlobalId("IN7");
    expect(record.iconName).toBe("instrument");
    expect(record.id).toBe(7);
    expect(record.permalinkURL).toBe("/globalId/IN7");
  });

  it("still maps the pre-existing inventory prefixes", () => {
    expect(new LinkableRecordFromGlobalId("IC3").iconName).toBe("container");
    expect(new LinkableRecordFromGlobalId("SA3").iconName).toBe("sample");
    expect(new LinkableRecordFromGlobalId("SS3").iconName).toBe("subsample");
    expect(new LinkableRecordFromGlobalId("IT3").iconName).toBe("template");
  });

  it("maps instrument template global ids to the instrumentTemplate icon", () => {
    expect(new LinkableRecordFromGlobalId("NT7").iconName).toBe("instrumentTemplate");
  });
});
