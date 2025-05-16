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
   * An Inventory record that has a location will either be inside of another
   * container, in which case this property will reference that container, or it
   * will be a root level container. Only containers may reside at the root
   * level; subsamples must always be inside of another container.
   */
  readonly immediateParentContainer: Container | null;

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
   * Determines whether the Inventory record is on a workbench, which is to say
   * that the root parent container is a workbench.
   */
  isInWorkbench(): boolean;

  /*
   * Not only is this item on a workbench, but the workbench that it is on is
   * owned by the specified user.
   */
  isInWorkbenchOfUser(user: Person): boolean;

  /*
   * This is the last container that the Inventory record was last in. In most
   * circumstances this is probably the storage location for the item from where
   * the user retrieved the item prior to beginning an experiment and to where
   * they will return it after they are done with using it.
   */
  readonly lastNonWorkbenchParent: Container | null;
}
