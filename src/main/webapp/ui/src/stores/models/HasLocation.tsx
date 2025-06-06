import { Container, GridLayout, Location } from "../definitions/Container";
import { Factory } from "../definitions/Factory";
import { Person } from "../definitions/Person";
import * as Parsers from "../../util/parsers";
import * as ArrayUtils from "../../util/ArrayUtils";
import { AdjustableTableRowOptions } from "../definitions/Tables";
import { type InventoryRecord } from "../definitions/InventoryRecord";
import React from "react";
import { Optional, lift2 } from "../../util/optional";
import {
  HasLocation,
  HasLocationEditableFields,
  HasLocationMarker,
  HasLocationUneditableFields,
} from "../definitions/HasLocation";
import { GlobalId } from "../definitions/BaseRecord";
import InventoryBaseRecord, {
  InventoryBaseRecordEditableFields,
  InventoryBaseRecordUneditableFields,
} from "./InventoryBaseRecord";

/**
 * Inventory records that model items that physically exist and thus have a
 * real-worth location MUST implement the HasLocation interface. This mixin
 * class provides the state and methods common to all of these Inventory
 * records.
 */
export function HasLocationMixin<
  TBase extends new (...args: any[]) => InventoryBaseRecord
>(Base: TBase) {
  return class extends Base implements HasLocation {
    [HasLocationMarker] = true as const;

    /*
     * The timestamp of when this item was last moved. If it has never been moved
     * from the location it was created in (usually the owner's workbench) then is
     * null. It will also be null if the user does not have permission to view its
     * location.
     */
    private lastMoveDate: Date | null;

    /*
     * This is the container that the Inventory record was last in, that is not
     * a workbench. In most circumstances this is probably the storage location
     * for the item from where the user retrieved the item prior to beginning an
     * experiment and to where they will return it after they are done with
     * using it. Is null when the item has not been moved from its initial
     * location, it has only ever been moved between workbenches, or the user
     * does not have permission to view its location.
     */
    private lastNonWorkbenchParent: Container | null;

    /*
     * This is the location within the parent container than the item is
     * located. If the parent container is a list container, or this item is a
     * root container, or if the user does not have permission to view the
     * location of this item, then the value will be null.
     */
    protected parentLocation: Location | null;

    /**
     * This is the immediate parent container of the Inventory record. If it is null,
     * then either it is because the item is a root container or the user does
     * not have permission to view its location.
     */
    public immediateParentContainer: Container | null;

    constructor(...args: any[]) {
      super(...args);
      const [factory, params] = args as [factory: Factory, params: object];
      this.immediateParentContainer = Parsers.getValueWithKey(
        "parentContainers"
      )(params)
        .flatMap(Parsers.isArray)
        .flatMap(ArrayUtils.head)
        .map(
          (immediateParentContainerParams) =>
            factory.newRecord(
              immediateParentContainerParams as Record<string, unknown> & {
                globalId: GlobalId;
              }
            ) as Container
        )
        .orElse(null);
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

    get fieldValues(): InventoryBaseRecordEditableFields &
      InventoryBaseRecordUneditableFields &
      HasLocationEditableFields &
      HasLocationUneditableFields {
      return {
        ...super.fieldValues,
        location: this as InventoryRecord,
      };
    }

    get noValueLabel(): {
      [key in keyof HasLocationEditableFields]: string | null;
    } & {
      [key in keyof HasLocationUneditableFields]: string | null;
    } & {
      [key in keyof InventoryBaseRecordEditableFields]: string | null;
    } & {
      [key in keyof InventoryBaseRecordUneditableFields]: string | null;
    } {
      return {
        ...super.noValueLabel,
        location: null,
      };
    }

    /**
     * Returns the container that lies at the top of the hierarchy; usually
     * either a workbench or some other container that acts as a grouping for
     * lots of other containers and samples e.g. a room or large freezer. If
     * the container is a root container then null is returned, but also when
     * the user does not have permission to view the item's location.
     */
    get rootParentContainer(): Container | null {
      if (this.immediateParentContainer === null) return null;
      return (
        this.immediateParentContainer.rootParentContainer ??
        this.immediateParentContainer
      );
    }

    /**
     * Returns all containers that lie between this container and the root
     * container. Assuming there are at least two containers in the hierarchy,
     * the first element will always be `this.immediateParentContainer` and the
     * last `this.rootParentContainer`. If this container is the root container,
     * or the user does not have permission to view the item's location, an empty
     * array is returned.
     */
    get allParentContainers(): ReadonlyArray<Container> {
      if (this.immediateParentContainer === null) return [];
      return [
        this.immediateParentContainer,
        ...this.immediateParentContainer.allParentContainers,
      ];
    }

    /**
     * Determines whether the Inventory record is on a workbench,
     * which is to say that the root parent container is a workbench.
     * Returns false if the user does not have permission to view the item's
     * location.
     */
    get isOnWorkbench(): boolean {
      if (this.rootParentContainer === null) return false;
      return this.rootParentContainer.isWorkbench;
    }

    /**
     * Determines whether the Inventory record is directly on a workbench,
     * which is to say the immediate parent container is a workbench. If this is
     * true, then it implies that `isOnWorkbench` is also true. Similarly, if
     * `isOnWorkbench` is false because the user does not have permission to view
     * the item's location, then false is returned.
     */
    get isDirectlyOnWorkbench(): boolean {
      return (
        this.isOnWorkbench &&
        this.immediateParentContainer === this.rootParentContainer
      );
    }

    /**
     * Not only is this item on a workbench, but the workbench that it is on is
     * owned by the specified user. Returns false is the user does not have
     * permission to view the location of this item.
     */
    isOnWorkbenchOfUser(user: Person): boolean {
      return (
        this.isOnWorkbench && this.rootParentContainer?.id === user.workbenchId
      );
    }

    /**
     * Not only is this item directly on a workbench, but that workbench that it
     * is on is owned by the specified user. Returns false is the user does not have
     * permission to view the location of this item.
     */
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
export function hasLocation<T extends object>(
  input: T
): Optional<HasLocation & T> {
  return input.hasOwnProperty(HasLocationMarker)
    ? Optional.present(input as HasLocation & T)
    : Optional.empty();
}

/**
 * Filters an iterable collection for those with locations
 */
export function* filterForThoseWithLocations<T extends object>(
  input: Iterable<T>
): Iterable<HasLocation & T> {
  for (const val of input) {
    if (val.hasOwnProperty(HasLocationMarker)) yield val as HasLocation & T;
  }
}
