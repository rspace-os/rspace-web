import { Container, Location } from "../definitions/Container";
import { Factory } from "../definitions/Factory";
import { Person } from "../definitions/Person";
import { ContainerAttrs } from "./ContainerModel";
import * as Parsers from "../../util/parsers";
import Result from "../../util/result";
import { AdjustableTableRowOptions } from "../definitions/Tables";
import { type InventoryRecord } from "../definitions/InventoryRecord";
import React from "react";
import { Optional } from "../../util/optional";

/**
 * Inventory records that model items that physically exist and thus have a
 * real-worth location MUST imlement the HasLocation interface. This class
 * provides the state and methods common to all of these Inventory records.
 */
export class HasLocationCapability {
  /*
   * An Inventory record that has a location will either be inside of another
   * container, in which case this property will reference that container, or it
   * will be a root level container. Only containers may reside at the root
   * level; subsamples must always be inside of another container.
   */
  immediateParentContainer: Container | null;

  lastNonWorkbenchParent: Container | null;
  lastMoveDate: Date | null;
  inventoryRecord: InventoryRecord;
  parentLocation: Location | null;

  constructor({
    parentContainers,
    parentLocation,
    lastMoveDate,
    lastNonWorkbenchParent,
    factory,
    inventoryRecord,
  }: {
    parentContainers: Array<ContainerAttrs> | null;
    parentLocation: Location | null;
    lastMoveDate: string | null;
    lastNonWorkbenchParent: ContainerAttrs | null;
    factory: Factory;
    inventoryRecord: InventoryRecord;
  }) {
    if (parentContainers !== null && parentContainers.length > 0) {
      this.immediateParentContainer = factory.newRecord(
        parentContainers[0]
      ) as Container;
    } else {
      this.immediateParentContainer = null;
    }
    this.lastMoveDate = Result.fromNullable(
      lastMoveDate,
      new Error("Not yet been moved")
    )
      .flatMap(Parsers.parseDate)
      .orElse(null);
    if (lastNonWorkbenchParent !== null) {
      this.lastNonWorkbenchParent = factory.newRecord(
        lastNonWorkbenchParent
      ) as Container;
    } else {
      this.lastNonWorkbenchParent = null;
    }
    this.inventoryRecord = inventoryRecord;
    this.parentLocation = parentLocation;
  }

  get rootParentContainer(): Container | null {
    if (this.immediateParentContainer === null) return null;
    return this.immediateParentContainer.rootParentContainer;
  }

  isMovable(): boolean {
    return true;
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
    const options: AdjustableTableRowOptions<string> = new Map();

    if (!this.immediateParentContainer)
      throw new Error("Parent container must be known.");
    if (!this.parentLocation) throw new Error("Parent location must be known.");
    const parentLocation = this.parentLocation;
    const gridCoordinatesLabel = Optional.fromNullable(
      this.immediateParentContainer.gridLayout
    )
      .map(
        ({ rowsNumber, columnsNumber }) =>
          `Row ${parentLocation.coordY} of ${rowsNumber}, Column ${parentLocation.coordX} of ${columnsNumber}`
      )
      .orElse("");

    if (this.inventoryRecord.readAccessLevel !== "public") {
      options.set("Previous Location", () =>
        this.lastNonWorkbenchParent
          ? { renderOption: "name", data: this.lastNonWorkbenchParent }
          : { renderOption: "node", data: null }
      );
      options.set("Current Location", () => ({
        renderOption: "location",
        data: this.inventoryRecord,
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
}
