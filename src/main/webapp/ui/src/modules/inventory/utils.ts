import { fromCommonUnit, getUnit, massIds, toCommonUnit } from "@/stores/definitions/Units";

export function getQuantityUnitSymbol(unitId: number): string | null {
  return getUnit(unitId)?.label ?? null;
}

export function isMassUnit(unitId: number): boolean {
  return Object.values(massIds).includes(unitId);
}

export function convertToGrams(value: number, unitId: number): number | null {
  if (!isMassUnit(unitId)) {
    return null;
  }

  return fromCommonUnit(toCommonUnit(value, unitId), massIds.grams);
}

export function convertFromGrams(valueInGrams: number, targetUnitId: number): number | null {
  if (!isMassUnit(targetUnitId)) {
    return null;
  }

  return fromCommonUnit(toCommonUnit(valueInGrams, massIds.grams), targetUnitId);
}
