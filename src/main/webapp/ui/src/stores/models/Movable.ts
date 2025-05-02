import type GlobalId from "../models/Result";
import { isoToLocale } from "../../util/Util";
import React from "react";
import { type AdjustableTableRowOptions } from "../definitions/Tables";
import getRootStore from "../stores/RootStore";
import ContainerModel, { type ContainerAttrs } from "./ContainerModel";
import { type Factory } from "../definitions/Factory";
import { type InventoryRecord } from "../definitions/InventoryRecord";
import { type Location, type Container } from "../definitions/Container";
import { type ReadAccessLevel } from "../definitions/Record";

export class Movable {
  parentContainers: ?Array<ContainerAttrs>;
  immediateParentContainer: ?ContainerModel;
  allParentContainers: () => Array<Container>;
  rootParentContainer: ?Container;
  parentLocation: ?Location;
  lastNonWorkbenchParent: ContainerModel;
  lastMoveDate: ?string;
  created: string;
  readAccessLevel: ReadAccessLevel;

  initializeMovableMixin(factory: Factory) {
    // first check required for 'public view' case
    if (this.parentContainers && this.parentContainers.length > 0) {
      // $FlowExpectedError[incompatible-type] Will return a ContainerModel
      this.immediateParentContainer = factory.newRecord(
        this.parentContainers[0]
      );
      this.allParentContainers = () => [
        // $FlowExpectedError[incompatible-type] immediateParentContainer is not null
        this.immediateParentContainer,
        // $FlowExpectedError[incompatible-use] allParentContainers will be not null
        // $FlowExpectedError[not-a-function]
        ...this.immediateParentContainer.allParentContainers(),
      ];
      this.parentContainers = null;
      this.rootParentContainer = this.allParentContainers().pop();
    } else {
      this.immediateParentContainer = null;
      this.allParentContainers = () => [];
      this.rootParentContainer = null;
      this.parentContainers = null;
    }
    if (this.lastNonWorkbenchParent)
      // $FlowExpectedError[incompatible-type]
      this.lastNonWorkbenchParent = factory.newRecord(
        this.lastNonWorkbenchParent
      );
  }

  hasParentContainers(): boolean {
    return Boolean(this.immediateParentContainer);
  }

  isMovable(): boolean {
    return true;
  }

  isInWorkbench(): boolean {
    return (
      this.hasParentContainers() &&
      (this.rootParentContainer?.isWorkbench ?? false)
    );
  }

  isInCurrentUsersWorkbench(): boolean {
    return (
      this.isInWorkbench() &&
      this.rootParentContainer?.id ===
        getRootStore().peopleStore.currentUser?.workbenchId
    );
  }

  isOnWorkbench(): boolean {
    return (
      this.isInWorkbench() &&
      this.immediateParentContainer === this.rootParentContainer
    );
  }

  isOnCurrentUsersWorkbench(): boolean {
    return (
      this.isOnWorkbench() &&
      this.rootParentContainer?.id ===
        getRootStore().peopleStore.currentUser?.workbenchId
    );
  }

  isInGridContainer(): boolean {
    return this.immediateParentContainer?.cType === "GRID";
  }

  timeInCurrentLocation(): number {
    const now = new Date();
    const movedIn = new Date(this.lastMoveDate || this.created);
    const msInLocation = now.getTime() - movedIn.getTime();
    return msInLocation;
  }

  wasHereLast(containerGlobalId: GlobalId): boolean {
    return this.lastNonWorkbenchParent.globalId === containerGlobalId;
  }

  gridCoordinatesLabel(): string {
    if (!this.immediateParentContainer)
      throw new Error("Parent container must be known.");
    if (!this.parentLocation) throw new Error("Parent location must be known.");
    return `Row ${this.parentLocation.coordY} of ${this.immediateParentContainer.rows.length}, Column ${this.parentLocation.coordX} of ${this.immediateParentContainer.columns.length}`;
  }

  adjustableTableOptions_movable(): AdjustableTableRowOptions<string> {
    const options: AdjustableTableRowOptions<string> = new Map();
    if (this.readAccessLevel !== "public") {
      options.set("Previous Location", () =>
        this.lastNonWorkbenchParent
          ? { renderOption: "name", data: this.lastNonWorkbenchParent }
          : { renderOption: "node", data: null }
      );
      options.set("Current Location", () => ({
        renderOption: "location",
        // $FlowFixMe[prop-missing]: this will be an InventoryRecord
        data: (this: InventoryRecord),
      }));
      if (this.isInGridContainer()) {
        options.set("Grid Coordinates", () => ({
          renderOption: "node",
          data: this.gridCoordinatesLabel(),
        }));
      }
      options.set("Last Moved", () => ({
        renderOption: "node",
        data: this.lastMoveDate ? isoToLocale(this.lastMoveDate) : <>&mdash;</>,
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
      if (this.isInGridContainer()) {
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
