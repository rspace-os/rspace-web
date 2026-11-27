import { expect, it } from "vitest";
import { displayValue } from "../utils/displayValue";

const english = { language: "en-US", yes: "Yes", no: "No" };
const german = { language: "de-DE", yes: "Ja", no: "Nein" };

it("localises API date, time and date-time strings by their field format", () => {
  expect(displayValue("2026-11-23", english, "date")).toBe("Nov 23, 2026");
  expect(displayValue("2026-11-23", german, "date")).toBe("23.11.2026");
  expect(displayValue("13:45", english, "time")).toBe("1:45 PM");
  expect(displayValue("13:45", german, "time")).toBe("13:45");
  const instant = "2026-11-23T09:15:00Z";
  expect(displayValue(instant, german, "dateTime")).toBe(
    new Intl.DateTimeFormat("de-DE", { dateStyle: "medium", timeStyle: "short" }).format(new Date(instant)),
  );
  expect(displayValue(instant, german)).toBe(instant);
  expect(displayValue("not a date", german, "date")).toBe("not a date");
});

it("uses the rendering language's yes and no words", () => {
  expect(displayValue([true, false], german)).toBe("Ja, Nein");
});
