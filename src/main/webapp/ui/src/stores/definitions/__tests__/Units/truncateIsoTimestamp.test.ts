/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";
import { truncateIsoTimestamp } from "../../Units";

describe("truncateIsoTimestamp", () => {
  test("Simple examples with string", () => {
    const date = "1970-01-01T00:00:00.000";

    expect(
      truncateIsoTimestamp(date, "year").orElseGet(([e]) => {
        throw e;
      })
    ).toEqual("1970");
    expect(
      truncateIsoTimestamp(date, "month").orElseGet(([e]) => {
        throw e;
      })
    ).toEqual("1970-01");
    expect(
      truncateIsoTimestamp(date, "date").orElseGet(([e]) => {
        throw e;
      })
    ).toEqual("1970-01-01");
    expect(
      truncateIsoTimestamp(date, "hour").orElseGet(([e]) => {
        throw e;
      })
    ).toEqual("1970-01-01T00");
    expect(
      truncateIsoTimestamp(date, "minute").orElseGet(([e]) => {
        throw e;
      })
    ).toEqual("1970-01-01T00:00");
    expect(
      truncateIsoTimestamp(date, "second").orElseGet(([e]) => {
        throw e;
      })
    ).toEqual("1970-01-01T00:00:00");
    expect(
      truncateIsoTimestamp(date, "millisecond").orElseGet(([e]) => {
        throw e;
      })
    ).toEqual("1970-01-01T00:00:00.000");
  });

  test("Simple examples with date", () => {
    const date = new Date("1970-01-01T00:00:00.000");

    expect(
      truncateIsoTimestamp(date, "year").orElseGet(([e]) => {
        throw e;
      })
    ).toEqual("1970");
    expect(
      truncateIsoTimestamp(date, "month").orElseGet(([e]) => {
        throw e;
      })
    ).toEqual("1970-01");
    expect(
      truncateIsoTimestamp(date, "date").orElseGet(([e]) => {
        throw e;
      })
    ).toEqual("1970-01-01");
    expect(
      truncateIsoTimestamp(date, "hour").orElseGet(([e]) => {
        throw e;
      })
    ).toEqual("1970-01-01T00");
    expect(
      truncateIsoTimestamp(date, "minute").orElseGet(([e]) => {
        throw e;
      })
    ).toEqual("1970-01-01T00:00");
    expect(
      truncateIsoTimestamp(date, "second").orElseGet(([e]) => {
        throw e;
      })
    ).toEqual("1970-01-01T00:00:00");
    expect(
      truncateIsoTimestamp(date, "millisecond").orElseGet(([e]) => {
        throw e;
      })
    ).toEqual("1970-01-01T00:00:00.000");
  });

  test("Invalid date", () => {
    expect(
      truncateIsoTimestamp(new Date("2001-22-29T00:00:00.000"), "date").isError
    ).toEqual(true);
  });
});
