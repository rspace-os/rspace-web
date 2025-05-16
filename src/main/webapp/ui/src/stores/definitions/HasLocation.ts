import { Container } from "./Container";
import { Person } from "./Person";

/**
 * Inventory records that model items that physically exist and thus have a
 * real-world location MUST implement this interface. To avoid duplicating
 * logic, each class can delegate most of the logic concerning this location
 * state to the HasLocationCapability model class.
 */
export interface HasLocation {
  /*
   * This will always be true for implementations of this interface, and is used
   * to filter a collection of Inventory records to operate just on those that
   * have a physical location.
   */
  isMovable(): boolean;

  /*
   * Because items which have a location have a parent container, and containers
   * are themselves items which have a location, we end up with a tree of
   * containers. At the root of the tree are containers which do not reside
   * within other containers; these include workbenches, and any other contaienrs
   * that the users of the system have designated as root containers, perhaps
   * cupboards, freezers, or whole rooms. This property gets a reference to the
   * root container. It will be null if this item is itself a root container.
   */
  readonly rootParentContainer: Container | null;

  /*
   * Determines whether the Inventory record is transitively on a workbench,
   * which is to say that the root parent container is a workbench.
   */
  readonly isInWorkbench: boolean;

  /*
   * Determines whether the Inventory record is directly on a workbench,
   * which is to say the immediate parent container is a workbench.
   */
  readonly isOnWorkbench: boolean;

  /*
   * Not only is this item transitively on a workbench, but the workbench that
   * it is on is owned by the specified user.
   */
  isInWorkbenchOfUser(user: Person): boolean;

  /*
   * Not only is this item directly on a workbench, but that workbench that it
   * is on it owned by the specified user.
   */
  isOnWorkbenchOfUser(user: Person): boolean;

  /*
   * The timestamp of when this item was last moved. If it has never been moved
   * from the location it was created in (usually the owner's workbench) then is
   * null;
   */
  lastMoveDate: Date | null;

  /*
   * This is the last container that the Inventory record was last in. In most
   * circumstances this is probably the storage location for the item from where
   * the user retrieved the item prior to beginning an experiment and to where
   * they will return it after they are done with using it.
   */
  readonly lastNonWorkbenchParent: Container | null;
}
