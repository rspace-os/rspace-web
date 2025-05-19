import { Container, Location } from "../definitions/Container";
import { Factory } from "../definitions/Factory";
import { Person } from "../definitions/Person";
import * as Parsers from "../../util/parsers";
import { AdjustableTableRowOptions } from "../definitions/Tables";
import { type InventoryRecord } from "../definitions/InventoryRecord";
import React from "react";
import { Optional } from "../../util/optional";
import { HasLocation } from "../definitions/HasLocation";
import { GlobalId } from "../definitions/BaseRecord";
import Result from "./Result";

/**
 * Inventory records that model items that physically exist and thus have a
 * real-worth location MUST implement the HasLocation interface. This mixin
 * class provides the state and methods common to all of these Inventory
 * records.
 */
export function HasLocationMixin<TBase extends new (...args: any[]) => Result>(
  Base: TBase
) {
  return class extends Base implements HasLocation {
    /*
     * The timestamp of when this item was last moved. If it has never been moved
     * from the location it was created in (usually the owner's workbench) then is
     * null;
     */
    private lastMoveDate: Date | null;

    /*
     * This is the last container that the Inventory record was last in. In most
     * circumstances this is probably the storage location for the item from where
     * the user retrieved the item prior to beginning an experiment and to where
     * they will return it after they are done with using it.
     */
    private lastNonWorkbenchParent: Container | null;

    protected parentLocation: Location | null;

    /*
     * An Inventory record that has a location will either be inside of another
     * container, in which case this property will reference that container, or it
     * will be a root level container. Only containers may reside at the root
     * level; subsamples must always be inside of another container.
     */
    public immediateParentContainer: Container | null;

    constructor(...args: any[]) {
      super(...args);
      const [factory, params] = args as [factory: Factory, params: object];
      const parentContainers = Parsers.getValueWithKey("parentContainers")(
        params
      )
        .flatMap(Parsers.isArray)
        .elseThrow();
      this.immediateParentContainer = factory.newRecord(
        parentContainers[0] as Record<string, unknown> & { globalId: GlobalId }
      ) as Container;
      this.parentLocation = Parsers.getValueWithKey("parentLocation")(
        params
      ).elseThrow() as Location | null;
      this.lastMoveDate = Parsers.getValueWithKey("lastMoveDate")(params)
        .flatMap(Parsers.isString)
        .flatMap(Parsers.parseDate)
        .orElse(null);
      this.lastNonWorkbenchParent = Parsers.getValueWithKey(
        "lastNonWorkbenchParent"
      )(params).elseThrow() as Container | null;
    }

    get fieldValues(): typeof Result.prototype.fieldValues & {
      location: InventoryRecord;
    } {
      return {
        ...super.fieldValues,
        location: this,
      };
    }

    get noValueLabel(): typeof Result.prototype.noValueLabel {
      return super.noValueLabel;
    }

    isMovable(): boolean {
      return true;
    }

    get rootParentContainer(): Container | null {
      if (this.immediateParentContainer === null) return null;
      return this.immediateParentContainer.rootParentContainer;
    }

    get isInWorkbench(): boolean {
      if (this.rootParentContainer === null) return false;
      return this.rootParentContainer.isWorkbench;
    }

    get isOnWorkbench(): boolean {
      return (
        this.isInWorkbench &&
        this.immediateParentContainer === this.rootParentContainer
      );
    }

    isInWorkbenchOfUser(user: Person): boolean {
      return (
        this.isInWorkbench && this.rootParentContainer?.id === user.workbenchId
      );
    }

    isOnWorkbenchOfUser(user: Person): boolean {
      return (
        this.isOnWorkbench && this.rootParentContainer?.id === user.workbenchId
      );
    }

    adjustableTableOptions(): AdjustableTableRowOptions<string> {
      const options: AdjustableTableRowOptions<string> = new Map([
        ...super.adjustableTableOptions(),
      ]);

      if (!this.immediateParentContainer)
        throw new Error("Parent container must be known.");
      if (!this.parentLocation)
        throw new Error("Parent location must be known.");
      const parentLocation = this.parentLocation;
      const gridCoordinatesLabel = Optional.fromNullable(
        this.immediateParentContainer.gridLayout
      )
        .map(
          ({ rowsNumber, columnsNumber }) =>
            `Row ${parentLocation.coordY} of ${rowsNumber}, Column ${parentLocation.coordX} of ${columnsNumber}`
        )
        .orElse("");

      if (this.readAccessLevel !== "public") {
        options.set("Previous Location", () =>
          this.lastNonWorkbenchParent
            ? { renderOption: "name", data: this.lastNonWorkbenchParent }
            : { renderOption: "node", data: null }
        );
        options.set("Current Location", () => ({
          renderOption: "location",
          data: this,
        }));
        if (this.immediateParentContainer?.cType === "GRID") {
          options.set("Grid Coordinates", () => ({
            renderOption: "node",
            data: gridCoordinatesLabel,
          }));
        }
        options.set("Last Moved", () => ({
          renderOption: "node",
          data: this.lastMoveDate ? (
            this.lastMoveDate.toLocaleString()
          ) : (
            <>&mdash;</>
          ),
        }));
      } else {
        options.set("Previous Location", () => ({
          renderOption: "node",
          data: null,
        }));
        options.set("Current Location", () => ({
          renderOption: "node",
          data: null,
        }));
        if (this.immediateParentContainer?.cType === "GRID") {
          options.set("Grid Coordinates", () => ({
            renderOption: "node",
            data: null,
          }));
        }
        options.set("Last Moved", () => ({ renderOption: "node", data: null }));
      }
      return options;
    }
  };
}
