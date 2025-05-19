import { Container } from "./Container";
import { InventoryRecord } from "./InventoryRecord";
import { Person } from "./Person";

/**
 * This is used to mark implementations of the HasLocation interface so that
 * runtime we can filter collections of InventoryRecord, operating just on those
 * that have a location.
 */
export const HasLocationMarker = Symbol("HasLocation");

/**
 * Inventory records that model items that physically exist and thus have a
 * real-world location MUST implement this interface. To avoid duplicating
 * logic, each class can delegate most of the logic concerning this location
 * state to the HasLocationCapability model class.
 */
export interface HasLocation {
  [HasLocationMarker]: true;

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

  /**
   * A list of containers, starting with the immediateParentContainer and ending
   * with the rootParentContainer.
   */
  readonly allParentContainers: ReadonlyArray<Container>;

  /*
   * Determines whether the Inventory record is on a workbench,
   * which is to say that the root parent container is a workbench.
   */
  readonly isOnWorkbench: boolean;

  /*
   * Determines whether the Inventory record is directly on a workbench,
   * which is to say the immediate parent container is a workbench. If this is
   * true, then it implies that `isOnWorkbench` is always true.
   */
  readonly isDirectlyOnWorkbench: boolean;

  /*
   * Not only is this item transitively on a workbench, but the workbench that
   * it is on is owned by the specified user.
   */
  isOnWorkbenchOfUser(user: Person): boolean;

  /*
   * Not only is this item directly on a workbench, but that workbench that it
   * is on it owned by the specified user.
   */
  isDirectlyOnWorkbenchOfUser(user: Person): boolean;

  /**
   * Extends the base class's properties for exposing information to form
   * fields.
   */
  readonly fieldValues: Record<string, unknown> & { location: InventoryRecord };
  readonly noValueLabel: Record<string, string | null>;
}
