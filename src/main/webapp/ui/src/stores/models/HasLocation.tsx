import { Container, GridLayout, Location } from "../definitions/Container";
import { Factory } from "../definitions/Factory";
import { Person } from "../definitions/Person";
import * as Parsers from "../../util/parsers";
import { AdjustableTableRowOptions } from "../definitions/Tables";
import { type InventoryRecord } from "../definitions/InventoryRecord";
import React from "react";
import { Optional, lift2 } from "../../util/optional";
import { HasLocation, HasLocationMarker } from "../definitions/HasLocation";
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
    [HasLocationMarker] = true as const;

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

    public immediateParentContainer: Container | null;

    constructor(...args: any[]) {
      super(...args);
      const [factory, params] = args as [factory: Factory, params: object];
      const parentContainers = Parsers.getValueWithKey("parentContainers")(
        params
      )
        .flatMap(Parsers.isArray)
        .elseThrow();
      if (parentContainers !== null && parentContainers.length > 0) {
        this.immediateParentContainer = factory.newRecord(
          parentContainers[0] as Record<string, unknown> & {
            globalId: GlobalId;
          }
        ) as Container;
      } else {
        this.immediateParentContainer = null;
      }
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

    get rootParentContainer(): Container | null {
      if (this.immediateParentContainer === null) return null;
      return this.immediateParentContainer.rootParentContainer;
    }

    get allParentContainers(): ReadonlyArray<Container> {
      if (this.immediateParentContainer === null) return [];
      return [
        this.immediateParentContainer,
        ...this.immediateParentContainer.allParentContainers,
      ];
    }

    get isOnWorkbench(): boolean {
      if (this.rootParentContainer === null) return false;
      return this.rootParentContainer.isWorkbench;
    }

    get isDirectlyOnWorkbench(): boolean {
      return (
        this.isOnWorkbench &&
        this.immediateParentContainer === this.rootParentContainer
      );
    }

    isOnWorkbenchOfUser(user: Person): boolean {
      return (
        this.isOnWorkbench && this.rootParentContainer?.id === user.workbenchId
      );
    }

    isDirectlyOnWorkbenchOfUser(user: Person): boolean {
      return (
        this.isDirectlyOnWorkbench &&
        this.rootParentContainer?.id === user.workbenchId
      );
    }

    adjustableTableOptions(): AdjustableTableRowOptions<string> {
      const options: AdjustableTableRowOptions<string> = new Map([
        ...super.adjustableTableOptions(),
      ]);

      const gridCoordinatesLabel = lift2<GridLayout, Location, string>(
        ({ rowsNumber, columnsNumber }, parentLocation) =>
          `Row ${parentLocation.coordY} of ${rowsNumber}, Column ${parentLocation.coordX} of ${columnsNumber}`,
        Optional.fromNullable(this.immediateParentContainer?.gridLayout),
        Optional.fromNullable(this.parentLocation)
      ).orElse("");

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

/**
 * Checks if a given object has a location.
 */
export function hasLocation(input: object): Optional<HasLocation> {
  return HasLocationMarker in input
    ? Optional.present(input as HasLocation)
    : Optional.empty();
}

/**
 * Filters an iterable collection for those with locations
 */
export function* filterForThoseWithLocations<T>(
  input: Iterable<T>
): Iterable<HasLocation & T> {
  for (const val of input) {
    if (HasLocationMarker in (val as object)) yield val as HasLocation & T;
  }
}
