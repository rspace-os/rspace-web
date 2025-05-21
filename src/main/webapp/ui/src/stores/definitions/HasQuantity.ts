import { type UnitCategory } from "../stores/UnitStore";
import { AdjustableTableRowOptions } from "./Tables";

export const HasQuantityMarker = Symbol("HasQuantity");

export type Quantity = {
  // API always deals with numbers, but input fields can read in empty string
  numericValue: number | "";
  unitId: number;
};

export type RecordWithQuantityUneditableFields = object;

export type RecordWithQuantityEditableFields = {
  quantity: Quantity | null;
};

export interface HasQuantity {
  [HasQuantityMarker]: true;

  quantity: RecordWithQuantityEditableFields["quantity"];

  readonly quantityCategory: UnitCategory;

  readonly quantityUnitId: number;

  readonly quantityValue: number;

  readonly quantityLabel: string;

  readonly quantityUnitLabel: string;

  adjustableTableOptions(): AdjustableTableRowOptions<string>;

  readonly fieldValues: RecordWithQuantityEditableFields;

  readonly noValueLabel: {
    [key in keyof RecordWithQuantityEditableFields]: string | null;
  } & {
    [key in keyof RecordWithQuantityUneditableFields]: string | null;
  };
}
