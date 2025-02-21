// @flow

import { type InventoryRecord } from "./InventoryRecord";
import { type Search, type SearchView } from "./Search";
import { type SubSample } from "./SubSample";
import { type Id } from "./BaseRecord";
import { type Point } from "../../util/types";
import { type Permissioned } from "./PermissionedData";

export type WorkbenchId = number;
export type ContainerType = "LIST" | "GRID" | "IMAGE" | "WORKBENCH";
export type Axis = "ABC" | "CBA" | "N123" | "N321";

export type GridLayout = {
  columnsNumber: number | "",
  rowsNumber: number | "",
  columnsLabelType: Axis,
  rowsLabelType: Axis,
};

export type ContentSummary = {|
  totalCount: number,
  subSampleCount: number,
  containerCount: number,
|};

export function cTypeToDefaultSearchView(cType: ContainerType): SearchView {
  if (cType === "GRID") return "GRID";
  if (cType === "IMAGE") return "IMAGE";
  return "LIST";
}

export interface Location extends Point {
  id: ?number;
  content: ?(SubSample | Container); //eslint-disable-line no-use-before-define
  coordX: number;
  coordY: number;
  selected: boolean;
  parentContainer: Container; //eslint-disable-line no-use-before-define

  toggleSelected(?boolean): void;
  selectOnlyThis(): void;
  setPosition(number, number): void;
  setDimensions(number, number): void;

  +siblings: Array<Location>;
  isShallow(Search): boolean;
  isShallowSelected(Search): boolean;
  isSelectable(Search): boolean;
  isShallowUnselected(Search): boolean;
  +isSiblingSelected: ?boolean;
  isGreyedOut(Search): boolean;
  +name: ?string;
  +hasContent: boolean;
  +uniqueColor: string;

  /*
   * The marshalled version of the data modelled by the implementation of this
   * interface, including the properties listed above. This computed property
   * MUST always be JSON serialisable, and it is advisable to write a unit test
   * to assert as such for each implementation.
   */
  +paramsForBackend: { ... };
}

/*
 * Containers form the organisational structure of the Inventory system,
 * enabling the modelling of a lab's means of storing samples and other items
 * of interest. Containers form a tree structure with containers capable of
 * storing other containers to an arbitrary depth. As such, containers can be
 * considered the branches of this tree and subsamples the leaves.
 *
 * Containers come in four types: List, Grid, Visual, and Workbench.
 * - List containers have an infinite capacity and in additional to modelling
 *   an unstructured physical container like a shelf, they are also used for
 *   modelling logical containers such as a set of records collectively
 *   imported.
 * - Grid containers store their contents in a 2-dimensional grid; the
 *   canonical use-case being a microplate.
 * - Visual containers store their contents in arbitrary locations defined on a
 *   specified image. This gives the maximum flexibility in describing exactly
 *   where a particular item can be located relative to the container in
 *   question, ideally for labelling drawers and boxes.
 * - Workbenches model the set of the items that the user currently has out on
 *   their bench. It is intended as a temporary storage location whilst an item
 *   is in use.
 */
export interface Container extends InventoryRecord {
  cType: ContainerType;

  /*
   * A summary of just the immediate contents of the container i.e. not
   * including any of the contents of the containers within this container.
   * This is usually only visible if the user has full access to th
   * container, i.e. they have the permittedAction of READ, however there are
   * circumstances where the user can view the content summary even without a
   * specific read access level such as viewing benches.
   */
  contentSummary: Permissioned<ContentSummary>;

  /*
   * Container can be restricted by what type of records they can contain. Here
   * `canStoreSamples` determines whether it can store subsamples -- samples
   * themselves are purely logically groupings of subsamples and as such can't
   * be stored in containers.
   */
  canStoreContainers: boolean;
  canStoreSamples: boolean;

  /*
   * If the container is a Grid container, then this defines the labelling of
   * the axis.
   */
  gridLayout: ?GridLayout;

  /*
   * An instance of the standard Search mechanisn, intended for making possible
   * the searching of the container's content.
   */
  contentSearch: Search;

  /*
   * For determining what colour to highlight a location, to identify groups of
   * location content based on properties like shared sample.
   */
  getColor(Id): ?string;

  /*
   * For un/selecting all locations (e.g. when selecting one for creation).
   */
  toggleAllLocations(boolean): void;

  /*
   * Calculates whether an ongoing more operation can store its current
   * selection in this container. It is unfortunate that the implementation of
   * this computed property requires that it reach out and grab the global
   * variable defining what is currently being moved.
   */
  +canStoreRecords: boolean;

  /*
   * All containers have locations in which content may or may not be stored.
   * - List containers have as many locations as is necessary store their
   *   contents. None of the locations are empty, and each has an Y-coordinate
   *   of 1 and an incrementing X-coordinate.
   * - Grid containers have as many locations as their width times their
   *   height. Each locations may or may not have any contents and the X and
   *   Y-coordinates fill the grid.
   * - Visual containers have some number of locations and fixed points
   *   relative to the associated locations image. Locations can be add, moved,
   *   and removed (provided they are empty).
   */
  locationsCount: number;
  locations: ?Array<Location>;
  findLocation(col: number, row: number): ?Location;

  /*
   * All of the container's locations, sorted by their ID.
   */
  +sortedLocations: ?Array<Location>;

  /*
   * Locations can be selected independently of the contents they may contain
   * so that empty locations can be operated on. If a Location has contents,
   * then the selected state of the Location MUST be synchronised with the
   * selected state of the Record.
   */
  +selectedLocations: ?Array<Location>;

  /*
   * Some containers are used to model user workbenches, an underlying
   * implementation detail that should be opaque from a user's perspective.
   */
  +isWorkbench: boolean;

  /*
   * State variables used for selecting regions of a container to ease
   * selection of several locations.
   */
  selectionMode: boolean;
  selectionStart: Point;
  selectionEnd: Point;
}
