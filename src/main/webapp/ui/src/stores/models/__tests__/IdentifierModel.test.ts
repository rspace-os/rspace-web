import { runInAction } from "mobx";
import { describe, expect, test } from "vitest";
import { mockIGSNAttrs } from "../../../Inventory/components/Fields/Identifiers/__tests__/mocking";
import IdentifierModel from "../IdentifierModel";

describe("IdentifierModel.toJson() — Dates serialization", () => {
  test("date values are plain yyyy-MM-dd strings, not Date objects or ISO timestamps", () => {
    const model = new IdentifierModel({ ...mockIGSNAttrs(), dates: [{ value: "2027-05-25", type: "CREATED" }] }, "SA1");
    const json = model.toJson() as { dates: Array<{ value: unknown }> };
    expect(typeof json.dates[0].value).toBe("string");
    expect(json.dates[0].value).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  test("serialized date matches local calendar date, not the UTC-shifted equivalent", () => {
    /*
     * new Date(year, monthIndex, day) creates midnight in LOCAL time.
     * format() from date-fns also uses local time, so the round-trip must
     * preserve the calendar date regardless of the host timezone offset.
     * This guards against the prior bug where JSON.stringify serialised Date
     * objects as UTC ISO strings, shifting the date back by the UTC offset.
     */
    const localMidnight = new Date(2027, 4, 25); // May 25 local midnight
    const model = new IdentifierModel({ ...mockIGSNAttrs(), dates: [] }, "SA1");
    runInAction(() => {
      model.dates = [{ value: localMidnight, type: "CREATED" }];
    });
    const json = model.toJson() as { dates: Array<{ value: string }> };
    const expectedLocalDateString = [
      localMidnight.getFullYear(),
      String(localMidnight.getMonth() + 1).padStart(2, "0"),
      String(localMidnight.getDate()).padStart(2, "0"),
    ].join("-");
    expect(json.dates[0].value).toBe(expectedLocalDateString);
  });
});
