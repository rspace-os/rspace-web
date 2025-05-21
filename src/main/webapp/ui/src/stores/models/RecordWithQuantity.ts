import { computed, makeObservable, observable, override } from "mobx";
import getRootStore from "../stores/RootStore";
import Result, {
  type ResultEditableFields,
  type ResultUneditableFields,
} from "./Result";
import { type Factory } from "../definitions/Factory";
import { type UnitCategory } from "../stores/UnitStore";
import { type AdjustableTableRowOptions } from "../definitions/Tables";
import {
  type HasEditableFields,
  type HasUneditableFields,
} from "../definitions/Editable";

export type Quantity = {
  // API always deals with numbers, but input fields can read in empty string
  numericValue: number | "";
  unitId: number;
};

export type HasQuantityEditableFields = ResultEditableFields & {
  quantity: Quantity | null;
};

export type HasQuantityUneditableFields = ResultUneditableFields;

/*
 * Some samples/subsamples don't have a quantity; these functions just provide
 * fallbacks.
 */
export const getUnitId = (q: Quantity | null): number => q?.unitId ?? 3;
export const getValue = (q: Quantity | null): number => q?.numericValue || 0;
export const getQuantityUnitLabel = (q: Quantity | null): string =>
  q ? getRootStore().unitStore.getUnit(q.unitId)?.label ?? "..." : "";
export const getLabel = (q: Quantity | null): string =>
  q ? `${getValue(q)} ${getQuantityUnitLabel(q)}` : "";

export default class HasQuantity
  extends Result
  implements
    HasEditableFields<HasQuantityEditableFields>,
    HasUneditableFields<HasQuantityUneditableFields>
{
  // @ts-expect-error quantity is initialised by populateFromJson
  quantity: HasQuantityEditableFields["quantity"];

  constructor(factory: Factory, params: object) {
    super(factory, params);
    makeObservable(this, {
      quantity: observable,
      quantityCategory: computed,
      quantityUnitId: computed,
      quantityValue: computed,
      quantityLabel: computed,
      quantityUnitLabel: computed,
      fieldValues: override,
    });
  }

  populateFromJson(
    factory: Factory,
    passedParams: object,
    defaultParams: object = {}
  ) {
    super.populateFromJson(factory, passedParams, defaultParams);
    const params = { ...defaultParams, ...passedParams } as {
      quantity: Quantity;
    };
    this.quantity = params.quantity;
  }

  get quantityCategory(): UnitCategory {
    const unitStore = getRootStore().unitStore;
    const unit = unitStore.getUnit(this.quantityUnitId);
    if (!unit) throw new Error("Could not get unit category");
    return unit.category;
  }

  get quantityUnitId(): number {
    return getUnitId(this.quantity);
  }

  get quantityValue(): number {
    return getValue(this.quantity);
  }

  get quantityLabel(): string {
    return getLabel(this.quantity);
  }

  get quantityUnitLabel(): string {
    return getQuantityUnitLabel(this.quantity);
  }

  adjustableTableOptions(): AdjustableTableRowOptions<string> {
    const options = super.adjustableTableOptions();
    options.set("Quantity", () => ({
      renderOption: "node",
      data: this.quantityLabel,
    }));
    return options;
  }

  /*
   * The current value of the editable fields, as required by the interface
   * `HasEditableFields` and `HasUneditableFields`.
   */
  get fieldValues(): HasQuantityEditableFields &
    HasQuantityUneditableFields {
    return {
      ...super.fieldValues,
      quantity: this.quantity,
    };
  }

  //eslint-disable-next-line no-unused-vars
  get noValueLabel(): {
    [key in keyof HasQuantityEditableFields]: string | null;
  } & { [key in keyof HasQuantityUneditableFields]: string | null } {
    return {
      ...super.noValueLabel,
      quantity: null,
    };
  }
}
