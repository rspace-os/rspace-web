import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { parseDate } from "../../parsers";
import fc from "fast-check";

describe("parseDate", () => {
  it("Pareses ISO timestamp", () => {
    fc.assert(
      fc.property(fc.date({ noInvalidDate: true }), (d) => {
        expect(
          parseDate(d.toISOString()).orElseGet(([e]) => {
            throw e;
          })
        ).toEqual(d);
      })
    );
  });

  it("Parses YYYY-MM-DD", () => {
    const input = "2021-02-02";
    const d = parseDate(input).orElseGet(([e]) => {
      throw e;
    });
    expect(d.getFullYear()).toEqual(2021);
    expect(d.getMonth()).toEqual(1);
    expect(d.getDay()).toEqual(2);
  });

  it("Parsers UNIX timestamp", () => {
    const input = new Date();
    const d = parseDate(Math.floor(input.getTime())).elseThrow();
    expect(d.getFullYear()).toEqual(input.getFullYear());
    expect(d.getMonth()).toEqual(input.getMonth());
    expect(d.getDay()).toEqual(input.getDay());
  });

  it("Fails on invalid dates", () => {
    const input = "2021-13-02T00:00:00.000Z";
    const d = parseDate(input);
    d.mapError(([e]) => {
      expect(e.message).toMatch(/not a valid date/);
      return e;
    }).do(() => {
      throw new Error("Should not have parsed");
    });
  });
});


