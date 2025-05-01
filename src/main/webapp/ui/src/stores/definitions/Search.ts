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
    parseString("EXCLUDE", str),
    parseString("INCLUDE", str),
    parseString("DELETED_ONLY", str)
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
    parseString("ALL", str),
    parseString("CONTAINER", str),
    parseString("SAMPLE", str),
    parseString("SUBSAMPLE", str),
    parseString("TEMPLATE", str)
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

export type UiConfig = {
  allowedSearchModules: AllowedSearchModules;
  allowedTypeFilters: AllowedTypeFilters;
  mainColumn: AdjustableTableRowLabel;
  adjustableColumns: Array<AdjustableTableRowLabel>;

  /*
   * Determines what ability the user has to select the search results, either
   * by tapping on the list node/table row or by using checkboxes.
   */
  selectionMode: SelectionMode;

  /*
   * If true, when a record is the activeResult it will be highlighted in the
   * search results
   */
  highlightActiveResult: boolean;

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
  hideContentsOfChip: boolean;

  /**
   * The maximum number of locations of containers that the user is allowed to
   * select.
   */
  selectionLimit: number;

  /**
   * Typically, we only allow the selection of container locations that have
   * contents, but in some circumstances we want to allow the user to select
   * the locations themselves, such as when moving items into those empty
   * locations -- that is when this flag should be true.
   */
  onlyAllowSelectingEmptyLocations: boolean;
};

export type PermalinkType =
  | "sample"
  | "container"
  | "subsample"
  | "sampletemplate";
export type Permalink = {
  type: PermalinkType;
  id: number;
  version: number | null;
};

export type CoreFetcherArgs = {
  query?: string;
  pageNumber?: number;
  pageSize?: number;
  orderBy?: string;
  order?: Order;
  parentGlobalId?: GlobalId | null;
  resultType?: ResultType | null;
  endpoint?: string;
  results?: Array<InventoryRecord>;
  loading?: boolean;
  count?: number;
  error?: string;
  permalink?: Permalink | null;
  ownedBy?: string | null;
  owner?: Person | null;
  deletedItems?: DeletedItems;
  benchOwner?: Person | null;
};

/**
 * CoreFetcher provides a standard interaction model with the API to fetch a
 * paginated listings of records.
 */
export interface CoreFetcher {
  loading: boolean;
  setLoading(value: boolean): void;
  error: string;
  setAttributes(param: CoreFetcherArgs): void;

  /*
   * Process search parameters and perform actual search.
   */
  resetFetcher(): void;
  generateQuery(editedParam: CoreFetcherArgs): URLSearchParams;
  generateNewQuery(editedParam: CoreFetcherArgs): URLSearchParams;
  performInitialSearch(params: CoreFetcherArgs | null): Promise<void>;
  reperformCurrentSearch(): Promise<void>;
  generateParams(editedParams: object): CoreFetcherArgs;

  /*
   * The results as returned by the API call. The results can also be edited,
   * though this SHOULD be avoided.
   */
  results: Array<InventoryRecord>;
  count: number;
  setResults(results: Array<InventoryRecord>): void;
  replaceResult(result: InventoryRecord): void;

  /*
   * Each record has a permalink that is used to provide a link to that
   * singular record. When this is set, all other search parameter MUST be
   * ignored.
   */
  permalink: Permalink | null;

  /*
   * Result can be filted based on a query string. For information on what
   * properties of the records are queried and the particulars of filtering
   * partial matches, see the API docs.
   */
  query: string | null;

  /*
   * The results can be filtered based on whether they have a "parent" with a
   * particular Global ID. What it means for a record to a "parent" is specific
   * to each record type.
   */
  parentGlobalId: GlobalId | null;
  readonly parentGlobalIdType: ParentGlobalIdType | null;
  setParentGlobalId(parentGlobalId: GlobalId | null): void;

  /*
   * Ordering of results
   */
  order: string;
  setOrder(order: Order, orderBy: string): void;
  isCurrentSort(key: string): boolean;
  invertSortOrder(): Order;
  defaultSortOrder(key: string): Order;
  readonly isOrderDesc: boolean;

  /*
   * Pagination
   */
  pageNumber: number;
  pageSize: number;
  setPage(pageNumber: number): Promise<void>;
  setPageSize(pageSize: number): void;

  /*
   * Filter by type of record
   */
  resultType: ResultType | null;
  setResultType(resultType: ResultType): void;

  /*
   * Filter by the owner
   */
  ownedBy: Username | null;
  owner: Person | null;
  setOwner(owner: Person | null): void;

  /*
   * Filter by location on a individual's bench
   */
  benchOwner: Person | null;
  setBenchOwner(owner: Person | null): void;

  /*
   * Filter based on whether the items have been deleted or not
   */
  deletedItems: DeletedItems;
  setDeletedItems(value: DeletedItems): void;

  /*
   * Searches can be saved, enabling the user to replay a particular search.
   */
  readonly serialize: Partial<CoreFetcherArgs>;

  /*
   * If true, then the "All" button in the search type filter SHOULD be enabled
   * and the user SHOULD be allowed to request a variety of different types of
   * records.
   */
  readonly allTypesAllowed: boolean;

  /*
   * Predicates regarding parentGlobalId
   */
  readonly parentIsContainer: boolean;
  readonly parentIsBench: boolean;
  readonly parentIsSample: boolean;
  readonly parentIsTemplate: boolean;
  readonly basketSearch: boolean;

  /*
   * To aid the UI in rendering the current deleted status.
   */
  readonly deletedItemsLabel: string;
}

/**
 * DynamicFetcher extends CoreFetcher with the means to provide an
 * infinite-scrolling interaction model i.e. each page of data MUST be appended
 * to the previously data as it is fetched.
 */
export interface DynamicFetcher extends CoreFetcher {
  dynamicSearch(): void;
  readonly nextDynamicPageSize: number;
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

export type ExportOptions = {
  exportMode: ExportMode;
  includeContainerContent?: OptionalContent | null;
  includeSubsamplesInSample?: OptionalContent | null;
  resultFileType?: ExportFileType;
};

export interface Search {
  uiConfig: UiConfig;

  /*
   * Loading states.
   */
  processingContextActions: boolean;
  readonly loading: boolean;

  /*
   * This variable MUST be current search view, as selected by the user or as
   * the default for the type of records being shown.
   */
  searchView: SearchView;
  setSearchView(view: SearchView): Promise<void>;

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
  readonly fetcher: CoreFetcher;

  /*
   * The list of results fetched by the current fetcher MUST be exposed.
   */
  readonly filteredResults: Array<InventoryRecord>;
  readonly count: number;
  isInResults(record: InventoryRecord): boolean;

  setupAndPerformInitialSearch(params: CoreFetcherArgs): Promise<void>;

  /*
   * All searches, in addition to a set of results, have a single result that
   * is the current active one. If none are selected, then the active result
   * can be null. This is to allow the users to perform actions on that one
   * specific result such as creating new or editing existing records.
   */
  activeResult: InventoryRecord | null;
  setActiveResult(
    result: InventoryRecord | null,
    options?: { defaultToFirstResult?: boolean; force?: boolean }
  ): Promise<void>;

  /*
   * These setters MUST update the state of the respective properties of each
   * fetcher. If the second arguments are true then they MUST also fetch the
   * latest data from the server with that new parameter.
   */
  setTypeFilter(resultType: ResultType, doSearch: boolean | null): void;
  setOwner(user: Person | null, doSearch: boolean | null): void;
  setBench(user: Person | null, doSearch: boolean | null): void;
  setDeletedItems(deletedItems: DeletedItems, doSearch: boolean | null): void;
  setParentGlobalId(
    parentGlobalId: GlobalId | null,
    doSearch: boolean | null
  ): void;

  /*
   * These setters MUST update the state of the staticFetcher (the only with
   * pagination) and reperform the search.
   */
  setPageSize(pageSize: number): void;
  setPage(pageNumber: number): Promise<void>;

  /*
   * The UI MAY provide a mechanism to select multiple search results that can
   * then be operated on as a group.
   */
  readonly selectedResults: Array<InventoryRecord>;
  batchEditingRecords: RsSet<InventoryRecord> | null;

  /*
   * The UI CAN use this method to query if a particulat record should be
   * disabled, thereby perventing selection and any other interaction.
   */
  alwaysFilterOut: (record: InventoryRecord) => boolean;

  /*
   * These methods SHOULD perform the associated contextual action.
   */
  createTemplateFromSample(
    name: string,
    sample: Sample,
    includeContentForFields: Set<Id>
  ): Promise<void>;
  deleteRecords(records: Array<InventoryRecord>): Promise<void>;
  duplicateRecords(records: Array<InventoryRecord>): Promise<void>;
  restoreRecords(records: Array<InventoryRecord>): Promise<void>;
  splitRecord(copies: number, subsample: SubSample): Promise<void>;
  transferRecords(
    username: Username,
    records: Array<InventoryRecord>
  ): Promise<void>;
  exportRecords(
    exportOptions: ExportOptions,
    records: Array<InventoryRecord>
  ): Promise<void>;

  /*
   * LIST view MAY provide a mechanism to adjust the column. For more
   * information, see ./Tables.js
   */
  readonly adjustableColumnOptions: RsSet<AdjustableTableRowLabel>;
  setAdjustableColumn(value: AdjustableTableRowLabel, index: number): void;

  /*
   * The model implementating this interface MAY instruct the UI to only allow
   * particular controls or particular options within those control.
   */
  readonly showStatusFilter: boolean;
  readonly showTypeFilter: boolean;
  readonly showOwnershipFilter: boolean;
  readonly showBenchFilter: boolean;
  readonly showSavedSearches: boolean;
  readonly showSavedBaskets: boolean;
  readonly showBarcodeScan: boolean;
  readonly showTagsFilter: boolean;
  readonly allowedStatusFilters: RsSet<DeletedItems>;
  readonly allowedTypeFilters: AllowedTypeFilters;

  /*
   * Predicates regarding the current state of the Search
   */
  readonly benchSearch: boolean;

  currentBasket(basket: ReadonlyArray<Basket>): Basket | null;
}
