/*
 */
import { describe, test, expect } from "vitest";
import { stripDiacritics } from "../../StringUtils";

describe("stripDiacritics", () => {
  test("Example", () => {
    expect(stripDiacritics("Zoë")).toEqual("Zoe");
  });
});


