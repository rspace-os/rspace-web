import {
  HasQuantity,
  HasQuantityMarker,
  HasQuantityEditableFields,
  HasQuantityUneditableFields,
  type Quantity,
} from "../definitions/HasQuantity";
import * as Parsers from "../../util/parsers";
import { Factory } from "../definitions/Factory";
import { type UnitCategory } from "../stores/UnitStore";
import InventoryBaseRecord, { InventoryBaseRecordEditableFields, InventoryBaseRecordUneditableFields } from "./InventoryBaseRecord";
import { AdjustableTableRowOptions } from "../definitions/Tables";
import getRootStore from "../stores/RootStore";
import { Optional } from "../../util/optional";
import { computed, makeObservable, observable, override } from "mobx";

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

export function HasQuantityMixin<TBase extends new (...args: any[]) => InventoryBaseRecord>(
  Base: TBase
) {
  return class extends Base implements HasQuantity {
    [HasQuantityMarker] = true as const;

    quantity: Quantity | null;

    constructor(...args: any[]) {
      super(...args);
      makeObservable(this, {
        quantity: observable,
        quantityCategory: computed,
        quantityUnitId: computed,
        quantityValue: computed,
        quantityLabel: computed,
        quantityUnitLabel: computed,
        fieldValues: override,
      });
      const [, params] = args as [factory: Factory, params: object];
      this.quantity = Parsers.getValueWithKey("quantity")(
        params
      ).elseThrow() as Quantity | null;
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

    get fieldValues(): InventoryBaseRecordEditableFields &
      InventoryBaseRecordUneditableFields &
      HasQuantityEditableFields &
      HasQuantityUneditableFields {
      return {
        ...super.fieldValues,
        quantity: this.quantity,
      };
    }

    get noValueLabel(): {
      [key in keyof HasQuantityEditableFields]: string | null;
    } & {
      [key in keyof HasQuantityUneditableFields]: string | null;
    } & {
      [key in keyof InventoryBaseRecordEditableFields]: string | null;
    } & {
      [key in keyof InventoryBaseRecordUneditableFields]: string | null;
    } {
      return {
        ...super.noValueLabel,
        quantity: null,
      };
    }
  };
}

/**
 * Checks if a given object has a quantity.
 */
export function hasQuantity(input: object): Optional<HasQuantity> {
  return HasQuantityMarker in input
    ? Optional.present(input as HasQuantity)
    : Optional.empty();
}

/**
 * Filters an iterable collection for those with quantities.
 */
export function* filterForThoseWithQuantities<T>(
  input: Iterable<T>
): Iterable<HasQuantity & T> {
  for (const val of input) {
    if (HasQuantityMarker in (val as object)) yield val as HasQuantity & T;
  }
}
