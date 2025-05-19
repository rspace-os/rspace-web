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
        ...(immediateParentContainer.allParentContainers ?? []),
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
}
