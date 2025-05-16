import { Container } from "../definitions/Container";
import { Factory } from "../definitions/Factory";
import { HasLocation } from "../definitions/HasLocation";
import { Person } from "../definitions/Person";
import { ContainerAttrs } from "./ContainerModel";
import * as Parsers from "../../util/parsers";
import Result from "../../util/result";

/**
 * Inventory records that model items that physically exist and thus have a
 * real-worth location MUST imlement the HasLocation interface. This class
 * provides the state and methods common to all of these Inventory records.
 */
export class HasLocationCapability implements HasLocation {
  /*
   * An Inventory record that has a location will either be inside of another
   * container, in which case this property will reference that container, or it
   * will be a root level container. Only containers may reside at the root
   * level; subsamples must always be inside of another container.
   */
  immediateParentContainer: Container | null;

  lastNonWorkbenchParent: Container | null;
  lastMoveDate: Date | null;

  constructor({
    parentContainers,
    lastMoveDate,
    lastNonWorkbenchParent,
    factory,
  }: {
    parentContainers: Array<ContainerAttrs> | null;
    lastMoveDate: string | null;
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

  get isOnWorkbench(): boolean {
    return (
      this.isInWorkbench() &&
      this.immediateParentContainer === this.rootParentContainer
    );
  }

  isInWorkbenchOfUser(user: Person): boolean {
    return (
      this.isInWorkbench() && this.rootParentContainer?.id === user.workbenchId
    );
  }

  isOnWorkbenchOfUser(user: Person): boolean {
    return (
      this.isOnWorkbench && this.rootParentContainer?.id === user.workbenchId
    );
  }
}
