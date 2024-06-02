/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import { parseDate } from "../../parsers";
import fc from "fast-check";

describe("parseDate", () => {
  test("Pareses ISO timestamp", () => {
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

  test("Parses YYYY-MM-DD", () => {
    const input = "2021-02-02";
    const d = parseDate(input).orElseGet(([e]) => {
      throw e;
    });
    expect(d.getFullYear()).toEqual(2021);
    expect(d.getMonth()).toEqual(1);
    expect(d.getDay()).toEqual(2);
  });

  test("Fails on invalid dates", () => {
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
