import { Container } from "./Container";
import { InventoryRecord } from "./InventoryRecord";
import { Person } from "./Person";

/**
 * This is used to mark implementations of the HasLocation interface so that at
 * runtime we can filter collections of InventoryRecord, operating just on those
 * that have a location.
 */
export const HasLocationMarker = Symbol("HasLocation");

/**
 * There are no fields associated with records that have a location that are
 * editable. To edit the location of an item, the user must perform a move
 * action rather than directly edit the location property.
 */
export type HasLocationEditableFields = object;

/**
 * When a record has a location, that location can be considered a field that
 * cannot be edited in the UI. To change the location, the user must perform a
 * move operation rather than edit the location property directly.
 */
export type HasLocationUneditableFields = {
  location: InventoryRecord;
};

/**
 * Inventory records that model items that physically exist and thus have a
 * real-world location MUST implement this interface.
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
   * within other containers; these include workbenches, and any other containers
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
   * true, then it implies that `isOnWorkbench` is also true.
   */
  readonly isDirectlyOnWorkbench: boolean;

  /*
   * Not only is this item on a workbench, but the workbench that it is on is
   * owned by the specified user.
   */
  isOnWorkbenchOfUser(user: Person): boolean;

  /*
   * Not only is this item directly on a workbench, but that workbench that it
   * is on is owned by the specified user.
   */
  isDirectlyOnWorkbenchOfUser(user: Person): boolean;

  /**
   * Extends the base class's properties for exposing information to form
   * fields.
   */
  readonly fieldValues: HasLocationEditableFields;
  readonly noValueLabel: {
    [key in keyof HasLocationEditableFields]: string | null;
  } & {
    [key in keyof HasLocationUneditableFields]: string | null;
  };
}
