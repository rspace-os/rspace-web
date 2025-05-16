import { type GlobalId } from "../definitions/BaseRecord";
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
  // @ts-expect-error Initialized by the concrete class that implement this one
  parentContainers: Array<ContainerAttrs> | null;
  // @ts-expect-error Initialized by initializeMovableMixin
  immediateParentContainer: ContainerModel | null;
  // @ts-expect-error Initialized by initializeMovableMixin
  allParentContainers: () => Array<Container>;
  // @ts-expect-error Initialized by initializeMovableMixin
  rootParentContainer: Container | null;
  // @ts-expect-error Initialized by the concrete class that implement this one
  parentLocation: Location | null;
  // @ts-expect-error Initialized by the concrete class that implement this one
  lastNonWorkbenchParent: ContainerModel;
  // @ts-expect-error Initialized by the concrete class that implement this one
  lastMoveDate: string | null;
  // @ts-expect-error Initialized by the concrete class that implement this one
  created: string;
  // @ts-expect-error A computed property defined by ./Result
  readAccessLevel: ReadAccessLevel;

  initializeMovableMixin(factory: Factory) {
    // first check required for 'public view' case
    if (this.parentContainers && this.parentContainers.length > 0) {
      const immediateParentContainer = factory.newRecord(
        this.parentContainers[0]
      ) as ContainerModel;
      this.immediateParentContainer = immediateParentContainer;
      this.allParentContainers = () => [
        immediateParentContainer,
        ...(immediateParentContainer.allParentContainers?.() ?? []),
      ];
      this.parentContainers = null;
    } else {
      this.immediateParentContainer = null;
      this.allParentContainers = () => [];
      this.parentContainers = null;
    }
    if (this.lastNonWorkbenchParent)
      this.lastNonWorkbenchParent = factory.newRecord(
        this.lastNonWorkbenchParent as unknown as Record<string, unknown> & {
          globalId: GlobalId;
        }
      ) as ContainerModel;
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

  isOnWorkbench(): boolean {
    return (
      this.isInWorkbench() &&
      this.immediateParentContainer === this.rootParentContainer
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
        data: this as unknown as InventoryRecord,
      }));
      if (this.isInGridContainer()) {
        options.set("Grid Coordinates", () => ({
          renderOption: "node",
          data: this.gridCoordinatesLabel(),
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
