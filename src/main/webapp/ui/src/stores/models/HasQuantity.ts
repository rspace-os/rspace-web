import {
  HasQuantity,
  HasQuantityMarker,
  type Quantity,
} from "../definitions/HasQuantity";
import * as Parsers from "../../util/parsers";
import { Factory } from "../definitions/Factory";
import { type UnitCategory } from "../stores/UnitStore";
import Result from "./Result";
import { AdjustableTableRowOptions } from "../definitions/Tables";
import getRootStore from "../stores/RootStore";

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

export function HasQuantityMixin<TBase extends new (...args: any[]) => Result>(
  Base: TBase
) {
  return class extends Base implements HasQuantity {
    [HasQuantityMarker] = true as const;

    quantity: Quantity | null;

    constructor(...args: any[]) {
      super(...args);
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

    get fieldValues(): typeof Result.prototype.fieldValues & {
      quantity: Quantity | null;
    } {
      return {
        ...super.fieldValues,
        quantity: this.quantity,
      };
    }

    get noValueLabel(): typeof Result.prototype.noValueLabel & {
      quantity: string | null;
    } {
      return {
        ...super.noValueLabel,
        quantity: null,
      };
    }
  };
}
