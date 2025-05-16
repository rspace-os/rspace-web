import { Container } from "../definitions/Container";
import { Factory } from "../definitions/Factory";
import { HasLocation } from "../definitions/HasLocation";
import { Person } from "../definitions/Person";
import { ContainerAttrs } from "./ContainerModel";

/**
 * Inventory records that model items that physically exist and thus have a
 * real-worth location MUST imlement the HasLocation interface. This class
 * provides the state and methods common to all of these Inventory records.
 */
export class HasLocationCapability implements HasLocation {
  immediateParentContainer: Container | null;
  lastNonWorkbenchParent: Container | null;

  constructor({
    parentContainers,
    lastNonWorkbenchParent,
    factory,
  }: {
    parentContainers: Array<ContainerAttrs> | null;
    lastNonWorkbenchParent: ContainerAttrs | null;
    factory: Factory;
  }) {
    if (parentContainers !== null && parentContainers.length > 0) {
      this.immediateParentContainer = factory.newRecord(
        parentContainers[0]
      ) as Container;
    } else {
      this.immediateParentContainer = null;
    }
    if (lastNonWorkbenchParent !== null) {
      this.lastNonWorkbenchParent = factory.newRecord(
        lastNonWorkbenchParent
      ) as Container;
    } else {
      this.lastNonWorkbenchParent = null;
    }
  }

  get rootParentContainer(): Container | null {
    if (this.immediateParentContainer === null) return null;
    return this.immediateParentContainer.rootParentContainer;
  }

  isMovable(): boolean {
    return true;
  }

  isInWorkbench(): boolean {
    if (this.rootParentContainer === null) return false;
    return this.rootParentContainer.isWorkbench;
  }

  isInWorkbenchOfUser(currentUser: Person): boolean {
    return (
      this.isInWorkbench() &&
      this.rootParentContainer?.id === currentUser.workbenchId
    );
  }
}
