import { describe, expect, it } from "vitest";
import { CELSIUS, FAHRENHEIT, isBelowAbsoluteZero, KELVIN, validateTemperature } from "../../Units";

describe("isBelowAbsoluteZero", () => {
  it("allows temperatures just above absolute zero that rounding used to reject", () => {
    expect(isBelowAbsoluteZero(-273.1, CELSIUS)).toBe(false);
    expect(isBelowAbsoluteZero(0.1, KELVIN)).toBe(false);
    expect(isBelowAbsoluteZero(-459.6, FAHRENHEIT)).toBe(false);
  });

  it("rejects temperatures below absolute zero", () => {
    expect(isBelowAbsoluteZero(-273.2, CELSIUS)).toBe(true);
    expect(isBelowAbsoluteZero(-0.1, KELVIN)).toBe(true);
    expect(isBelowAbsoluteZero(-459.7, FAHRENHEIT)).toBe(true);
  });

  it("drives validateTemperature", () => {
    expect(validateTemperature({ numericValue: -273.1, unitId: CELSIUS }).isOk).toBe(true);
    expect(validateTemperature({ numericValue: -273.2, unitId: CELSIUS }).isError).toBe(true);
  });
});
