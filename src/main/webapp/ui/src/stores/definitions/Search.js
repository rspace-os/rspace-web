//@flow

import { type InventoryRecord } from "./InventoryRecord";
import { type Username, type Person } from "./Person";
import RsSet from "../../util/set";
import { type TreeView } from "./TreeView";
import { type AdjustableTableRowLabel } from "./Tables";
import { type Sample } from "./Sample";
import { type Id, type GlobalId } from "./BaseRecord";
import { type SubSample } from "./SubSample";
import { type Order } from "../../util/types";
import { type Basket } from "./Basket";
import { parseString } from "../../util/parsers";
import Result from "../../util/result";

export type SearchView = "LIST" | "TREE" | "CARD" | "IMAGE" | "GRID";
export const TYPE_LABEL = {
  LIST: "List",
  TREE: "Tree",
  CARD: "Card",
  GRID: "Grid",
  IMAGE: "Visual",
};

export type SearchModule =
  | "BENCHES"
  | "TYPE"
  | "STATUS"
  | "OWNER"
  | "SCAN"
  | "TAG"
  | "SAVEDSEARCHES"
  | "SAVEDBASKETS";
/*
 * Uses Set rather than RsSet as Set has a smaller memory footprint and the
 * extended functionality of RsSet is not needed.
 */
export type AllowedSearchModules = Set<SearchModule>;
export type SelectionMode = "NONE" | "SINGLE" | "MULTIPLE";
export type DeletedItems = "EXCLUDE" | "INCLUDE" | "DELETED_ONLY";

export function parseDeletedItems(str: string): Result<DeletedItems> {
  return Result.first(
    (parseString("EXCLUDE", str): Result<DeletedItems>),
    (parseString("INCLUDE", str): Result<DeletedItems>),
    (parseString("DELETED_ONLY", str): Result<DeletedItems>)
  );
}

export type ResultType =
  | "ALL"
  | "CONTAINER"
  | "SAMPLE"
  | "SUBSAMPLE"
  | "TEMPLATE";

export function parseResultType(str: string): Result<ResultType> {
  return Result.first(
    (parseString("ALL", str): Result<ResultType>),
    (parseString("CONTAINER", str): Result<ResultType>),
    (parseString("SAMPLE", str): Result<ResultType>),
    (parseString("SUBSAMPLE", str): Result<ResultType>),
    (parseString("TEMPLATE", str): Result<ResultType>)
  );
}

export type ParentGlobalIdType =
  | "SAMPLE"
  | "SUBSAMPLE"
  | "CONTAINER"
  | "TEMPLATE"
  | "BENCH"
  | "BASKET";

/*
 * Except for container's contentSearch in  'public view case', must include
 * at least one of "SAMPLE", "SUBSAMPLE", "CONTAINER", "TEMPLATE".
 * "ALL" can optionally be included if more than one of the above are added.
 *
 * Uses Set rather than RsSet as Set has a smaller memory footprint and the
 * extended functionality of RsSet is not needed.
 */
export type AllowedTypeFilters = Set<ResultType>;

export type UiConfig = {|
  allowedSearchModules: AllowedSearchModules,
  allowedTypeFilters: AllowedTypeFilters,
  mainColumn: AdjustableTableRowLabel,
  adjustableColumns: Array<AdjustableTableRowLabel>,

  /*
   * Determines what ability the user has to select the search results, either
   * by tapping on the list node/table row or by using checkboxes.
   */
  selectionMode: SelectionMode,

  /*
   * If true, when a record is the activeResult it will be highlighted in the
   * search results
   */
  highlightActiveResult: boolean,

  /*
   * When parentGlobalId is set, the search component shows a label indicating
   * that all of the search results are the contents of a particular record,
   * with a close button that allows the user to remove this search parameter.
   * This is so that in the pickers, e.g. in the List of Materials, it is
   * possible to back out of a search that has a parentGlobalId parameter set.
   *
   * However, it is often the case that we do not want the user to be able to
   * remove the parentGlobalId parameter. For example, when viewing the content
   * of a container or sample within the right panel it should not be possible
   * for the user to remove the parentGlobalId parameter. Therefore, this flag
   * instructs the search component not to show such a label (as it is obvious
   * from the context that only the records inside the parent are shown) and
   * to not show a button to remove the parentGlobalId parameter.
   */
  hideContentsOfChip: boolean,

  /**
   * The maximum number of locations of containers that the user is allowed to
   * select.
   */
  selectionLimit: number,
|};

export type PermalinkType =
  | "sample"
  | "container"
  | "subsample"
  | "sampletemplate";
export type Permalink = {| type: PermalinkType, id: number, version: ?number |};

export type CoreFetcherArgs = {|
  query?: string,
  pageNumber?: number,
  pageSize?: number,
  orderBy?: string,
  order?: Order,
  parentGlobalId?: ?GlobalId,
  resultType?: ?ResultType,
  endpoint?: string,
  results?: Array<InventoryRecord>,
  loading?: boolean,
  count?: number,
  error?: string,
  permalink?: ?Permalink,
  ownedBy?: ?string,
  owner?: ?Person,
  deletedItems?: DeletedItems,
  benchOwner?: ?Person,
|};

/*
 * CoreFetcher provides a standard interaction model with the API to fetch a
 * paginated listings of records.
 */
export interface CoreFetcher {
  loading: boolean;
  setLoading(boolean): void;
  error: string;
  setAttributes(CoreFetcherArgs): void;

  /*
   * Process search parameters and perform actual search.
   */
  resetFetcher(): void;
  generateQuery(CoreFetcherArgs): URLSearchParams;
  generateNewQuery(CoreFetcherArgs): URLSearchParams;
  performInitialSearch(?CoreFetcherArgs): Promise<void>;
  reperformCurrentSearch(): Promise<void>;
  generateParams(?CoreFetcherArgs | {}): CoreFetcherArgs;

  /*
   * The results as returned by the API call. The results can also be edited,
   * though this SHOULD be avoided.
   */
  results: Array<InventoryRecord>;
  count: number;
  setResults(Array<InventoryRecord>): void;
  replaceResult(InventoryRecord): void;

  /*
   * Each record has a permalink that is used to provide a link to that
   * singular record. When this is set, all other search parameter MUST be
   * ignored.
   */
  permalink: ?Permalink;

  /*
   * Result can be filted based on a query string. For information on what
   * properties of the records are queried and the particulars of filtering
   * partial matches, see the API docs.
   */
  query: ?string;

  /*
   * The results can be filtered based on whether they have a "parent" with a
   * particular Global ID. What it means for a record to a "parent" is specific
   * to each record type.
   */
  parentGlobalId: ?GlobalId;
  +parentGlobalIdType: ?ParentGlobalIdType;
  setParentGlobalId(?GlobalId): void;

  /*
   * Ordering of results
   */
  order: string;
  setOrder(Order, string): void;
  isCurrentSort(string): boolean;
  invertSortOrder(): Order;
  defaultSortOrder(string): Order;
  +isOrderDesc: boolean;

  /*
   * Pagination
   */
  pageNumber: number;
  pageSize: number;
  setPage(number): Promise<void>;
  setPageSize(number): void;

  /*
   * Filter by type of record
   */
  resultType: ?ResultType;
  setResultType(ResultType): void;

  /*
   * Filter by the owner
   */
  ownedBy: ?Username;
  owner: ?Person;
  setOwner(?Person): void;

  /*
   * Filter by location on a individual's bench
   */
  benchOwner: ?Person;
  setBenchOwner(?Person): void;

  /*
   * Filter based on whether the items have been deleted or not
   */
  deletedItems: DeletedItems;
  setDeletedItems(DeletedItems): void;

  /*
   * Searches can be saved, enabling the user to replay a particular search.
   */
  +serialize: Partial<CoreFetcherArgs>;

  /*
   * If true, then the "All" button in the search type filter SHOULD be enabled
   * and the user SHOULD be allowed to request a variety of different types of
   * records.
   */
  +allTypesAllowed: boolean;

  /*
   * Predicates regarding parentGlobalId
   */
  +parentIsContainer: boolean;
  +parentIsBench: boolean;
  +parentIsSample: boolean;
  +parentIsTemplate: boolean;
  +basketSearch: boolean;

  /*
   * To aid the UI in rendering the current deleted status.
   */
  +deletedItemsLabel: string;
}

/*
 * DynamicFetcher extends CoreFetcher with the means to provide an
 * infinite-scrolling interaction model i.e. each page of data MUST be appended
 * to the previously data as it is fetched.
 */
export interface DynamicFetcher extends CoreFetcher {
  dynamicSearch(): void;
  +nextDynamicPageSize: number;
}

/*
 * CacheFetcher SHOULD provide an alternative implementation of CoreFetcher
 * designed for search views that require all of the data be fetched initially;
 * where pagination cannot be supported.
 */
export interface CacheFetcher extends CoreFetcher {}

export type ExportMode = "FULL" | "COMPACT";
export type OptionalContent = "INCLUDE" | "EXCLUDE";
export type ExportFileType = "ZIP" | "SINGLE_CSV";

export type ExportOptions = {|
  exportMode: ExportMode,
  includeContainerContent?: ?OptionalContent,
  includeSubsamplesInSample?: ?OptionalContent,
  resultFileType?: ExportFileType,
|};

export interface Search {
  uiConfig: UiConfig;

  /*
   * Loading states.
   */
  processingContextActions: boolean;
  +loading: boolean;

  /*
   * This variable MUST be current search view, as selected by the user or as
   * the default for the type of records being shown.
   */
  searchView: SearchView;
  setSearchView(SearchView): Promise<void>;

  /*
   * Tree is needed to display TREE view.
   */
  tree: TreeView;

  /*
   * The fetchers are what actually perform the interactions with the API.
   * staticFetcher is for LIST view, dynamicFetcher for TREE and CARD view, and
   * cacheFetcher for IMAGE and GRID views. The fetcher computed property MUST
   * be the current fetcher, as specified by the searchView property.
   */
  staticFetcher: CoreFetcher;
  dynamicFetcher: DynamicFetcher;
  cacheFetcher: CacheFetcher;
  +fetcher: CoreFetcher;

  /*
   * The list of results fetched by the current fetcher MUST be exposed.
   */
  +filteredResults: Array<InventoryRecord>;
  +count: number;
  isInResults(InventoryRecord): boolean;

  setupAndPerformInitialSearch(CoreFetcherArgs): Promise<void>;

  /*
   * All searches, in addition to a set of results, have a single result that
   * is the current active one. If none are selected, then the active result
   * can be null. This is to allow the users to perform actions on that one
   * specific result such as creating new or editing existing records.
   */
  activeResult: ?InventoryRecord;
  setActiveResult(
    ?InventoryRecord,
    options?: {| defaultToFirstResult?: boolean, force?: boolean |}
  ): Promise<void>;

  /*
   * These setters MUST update the state of the respective properties of each
   * fetcher. If the second arguments are true then they MUST also fetch the
   * latest data from the server with that new parameter.
   */
  setTypeFilter(ResultType, ?boolean): void;
  setOwner(?Person, ?boolean): void;
  setBench(?Person, ?boolean): void;
  setDeletedItems(DeletedItems, ?boolean): void;
  setParentGlobalId(?GlobalId, ?boolean): void;

  /*
   * These setters MUST update the state of the staticFetcher (the only with
   * pagination) and reperform the search.
   */
  setPageSize(number): void;
  setPage(number): Promise<void>;

  /*
   * The UI MAY provide a mechanism to select multiple search results that can
   * then be operated on as a group.
   */
  +selectedResults: Array<InventoryRecord>;
  batchEditingRecords: ?RsSet<InventoryRecord>;

  /*
   * The UI CAN use this method to query if a particulat record should be
   * disabled, thereby perventing selection and any other interaction.
   */
  alwaysFilterOut: (InventoryRecord) => boolean;

  /*
   * These methods SHOULD perform the associated contextual action.
   */
  createTemplateFromSample(string, Sample, Set<Id>): Promise<void>;
  deleteRecords(Array<InventoryRecord>): Promise<void>;
  duplicateRecords(Array<InventoryRecord>): Promise<void>;
  restoreRecords(Array<InventoryRecord>): Promise<void>;
  splitRecord(number, SubSample): Promise<void>;
  transferRecords(Username, Array<InventoryRecord>): Promise<void>;
  exportRecords(ExportOptions, Array<InventoryRecord>): Promise<void>;

  /*
   * LIST view MAY provide a mechanism to adjust the column. For more
   * information, see ./Tables.js
   */
  +adjustableColumnOptions: RsSet<AdjustableTableRowLabel>;
  setAdjustableColumn(AdjustableTableRowLabel, number): void;

  /*
   * The model implementating this interface MAY instruct the UI to only allow
   * particular controls or particular options within those control.
   */
  +showStatusFilter: boolean;
  +showTypeFilter: boolean;
  +showOwnershipFilter: boolean;
  +showBenchFilter: boolean;
  +showSavedSearches: boolean;
  +showSavedBaskets: boolean;
  +showBarcodeScan: boolean;
  +showTagsFilter: boolean;
  +allowedStatusFilters: RsSet<DeletedItems>;
  +allowedTypeFilters: AllowedTypeFilters;

  /*
   * Predicates regarding the current state of the Search
   */
  +benchSearch: boolean;

  currentBasket($ReadOnlyArray<Basket>): ?Basket;
}
