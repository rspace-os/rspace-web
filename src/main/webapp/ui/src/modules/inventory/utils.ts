import {
  fromCommonUnit,
  massIds,
  toCommonUnit,
  volumeIds,
  unitlessIds,
} from "@/stores/definitions/Units";

const QUANTITY_UNIT_SYMBOLS: Record<number, string> = {
  [unitlessIds.items]: "items",
  [volumeIds.microliters]: "µL",
  [volumeIds.milliliters]: "mL",
  [volumeIds.liters]: "L",
  [volumeIds.picoliters]: "pL",
  [volumeIds.nanoliters]: "nL",
  [volumeIds.millimeterscubed]: "mm³",
  [volumeIds.centimeterscubed]: "cm³",
  [volumeIds.decimeterscubed]: "dm³",
  [volumeIds.meterscubed]: "m³",
  [massIds.micrograms]: "µg",
  [massIds.milligrams]: "mg",
  [massIds.grams]: "g",
  [massIds.picograms]: "pg",
  [massIds.nanograms]: "ng",
  [massIds.kilograms]: "kg",
};

export function getQuantityUnitSymbol(unitId: number): string | null {
  return QUANTITY_UNIT_SYMBOLS[unitId] ?? null;
}

export function isMassUnit(unitId: number): boolean {
  return Object.values(massIds).includes(unitId);
}

export function convertToGrams(
  value: number,
  unitId: number,
): number | null {
  if (!isMassUnit(unitId)) {
    return null;
  }

  return fromCommonUnit(
    toCommonUnit(value, unitId),
    massIds.grams,
  );
}

export function convertFromGrams(
  valueInGrams: number,
  targetUnitId: number,
): number | null {
  if (!isMassUnit(targetUnitId)) {
    return null;
  }

  return fromCommonUnit(
    toCommonUnit(valueInGrams, massIds.grams),
    targetUnitId,
  );
}

