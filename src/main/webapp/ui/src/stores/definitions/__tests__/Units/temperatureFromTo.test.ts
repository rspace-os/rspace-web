/*
 */
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { temperatureFromTo, CELSIUS, KELVIN, FAHRENHEIT } from "../../Units";

describe("conversions", () => {
  describe("Simple examples", () => {
    it("0°C = 32°F", () => {
      expect(temperatureFromTo(CELSIUS, FAHRENHEIT, 0)).toBe(32);
    });
    it("0°C = 273K", () => {
      expect(temperatureFromTo(CELSIUS, KELVIN, 0)).toBe(273);
    });
    it("0K = -273°C", () => {
      expect(temperatureFromTo(KELVIN, CELSIUS, 0)).toBe(-273);
    });
    it("0K = -460°F", () => {
      expect(temperatureFromTo(KELVIN, FAHRENHEIT, 0)).toBe(-460);
    });
    it("0°F = -18°C", () => {
      expect(temperatureFromTo(FAHRENHEIT, CELSIUS, 0)).toBe(-18);
    });
    it("0°F = 255K", () => {
      expect(temperatureFromTo(FAHRENHEIT, KELVIN, 0)).toBe(255);
    });
  });
});


