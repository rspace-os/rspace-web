import { describe, expect, it } from "vitest";
import { formatRaidConnectionLabel } from "../formatRaidConnectionLabel";

describe("formatRaidConnectionLabel", () => {
  it("formats a title and identifier without changing the existing display shape", () => {
    expect(
      formatRaidConnectionLabel({
        raidIdentifier: "raid-123",
        raidTitle: "Test RAiD",
      }),
    ).toBe("Test RAiD (raid-123)");
  });

  it("does not render a leading space when the title is missing", () => {
    expect(
      formatRaidConnectionLabel({
        raidIdentifier: "raid-123",
        raidTitle: "",
      }),
    ).toBe("(raid-123)");
  });

  it("trims title whitespace before formatting", () => {
    expect(
      formatRaidConnectionLabel({
        raidIdentifier: "raid-123",
        raidTitle: "  Test RAiD  ",
      }),
    ).toBe("Test RAiD (raid-123)");
  });
});
