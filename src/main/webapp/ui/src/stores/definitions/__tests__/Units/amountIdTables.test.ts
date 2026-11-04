import { describe, expect, it } from "vitest";
import { categoryOfUnit, type QuantityUnitId, quantityIds, toCommonUnit, unitsOfCategory } from "../../Units";
import units from "../../units.json";

const AMOUNT_CATEGORIES = ["dimensionless", "volume", "mass"];

/**
 * Units.ts keeps its own id tables for amount units (volumeIds, massIds,
 * unitlessIds and their conversion magnitudes). units.json is pinned to the
 * backend, so pin these tables to units.json.
 */
describe("amount unit id tables", () => {
  it("know every amount unit in units.json", () => {
    for (const unit of unitsOfCategory(AMOUNT_CATEGORIES)) {
      const id = unit.id as QuantityUnitId;
      expect(categoryOfUnit(id), `unit ${unit.id}`).toBe(unit.category);
      expect(Number.isFinite(toCommonUnit(1, id)), `unit ${unit.id}`).toBe(true);
    }
  });

  it("only contain units from units.json", () => {
    for (const id of Object.values(quantityIds)) {
      expect(units.find((u) => u.id === id)?.category, `unit ${id}`).toBe(categoryOfUnit(id));
    }
  });
});
