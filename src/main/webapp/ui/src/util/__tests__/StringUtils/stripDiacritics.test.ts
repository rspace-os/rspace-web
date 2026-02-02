import { describe, expect, it } from "vitest";
import { stripDiacritics } from "../../StringUtils";

describe("stripDiacritics", () => {
  it("Example", () => {
    expect(stripDiacritics("Zoë")).toEqual("Zoe");
  });
});


