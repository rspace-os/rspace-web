import { type UnitCategory } from "../stores/UnitStore";
import { AdjustableTableRowOptions } from "./Tables";

/**
 * This is used to mark implementations of the HasQuantity interface so that at
 * runtime we can filter collections of InventoryRecord, operating just on those
 * that have a quantity.
 */
export const HasQuantityMarker = Symbol("HasQuantity");

/**
 * A quantity is a physical quantity of a substance, collection of indivisible
 * items, or other measurable amount of something being used in the lab that is
 * being recorded in the system. It consists of a numeric value and a unit of
 * measurement identified by a unitId.
 *
 * The numeric value can be a number or an empty string. Empty strings are used
 * to represent fields that have not yet been filled in by the user in input fields.
 * The API will always use numeric values for calculations and storage.
 *
 * The unitId references a specific unit of measurement that gives context to the
 * numeric value (e.g., grams, liters, pieces, etc.)
 */
export type Quantity = {
  numericValue: number | "";
  unitId: number;
};

/**
 * There are no fields associated with records that have a quantity that are not
 * editable.
 */
export type HasQuantityUneditableFields = object;

/**
 * Where a record has a quantity, the user may edit it in the UI.
 */
export type HasQuantityEditableFields = {
  quantity: Quantity | null;
};

/**
 * Several different types of Inventory records have a quantity, both those that
 * model physical substances and items in the lab, as well as the logical
 * groupings of those items. All of these records MUST implement this interface,
 * providing a generic abstraction over all such items.
 */
export interface HasQuantity {
  [HasQuantityMarker]: true;

  /**
   * Despite all of the properties below which should be used wherever possible,
   * we still expose this property directly so that TypeScript's type refinement
   * can be used to first check if the value is not null, and then get at the
   * numerical value.
   */
  quantity: HasQuantityEditableFields["quantity"];

  /*
   * The category of unit by which the quantity is measured e.g. mass, volume,
   * etc.
   */
  readonly quantityCategory: UnitCategory;

  /*
   * The specific unit by which the quantity is measured -- e.g. grams,
   * kilograms, etc. It is described as a enum number that the database uses to
   * identify units.
   */
  readonly quantityUnitId: number;

  /*
   * The numerical value of the quantity. Prefer this over accessing the
   * `quantity` property directly.
   */
  readonly quantityValue: number;

  /*
   * The label to be shown in the UI when displaying the quantity.
   */
  readonly quantityLabel: string;

  /*
   * The label to be shown in the UI for just the units of this quantity.
   */
  readonly quantityUnitLabel: string;

  adjustableTableOptions(): AdjustableTableRowOptions<string>;

  readonly fieldValues: HasQuantityEditableFields & HasQuantityUneditableFields;

  readonly noValueLabel: {
    [key in keyof HasQuantityEditableFields]: string | null;
  } & {
    [key in keyof HasQuantityUneditableFields]: string | null;
  };
}
