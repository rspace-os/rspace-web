import {
  action,
  computed,
  observable,
  makeObservable,
  runInAction,
} from "mobx";
import { mkAlert } from "../../contexts/Alert";
import ApiService from "../../../common/InvApiService";
import getRootStore from "../../stores/RootStore";
import { globalIdPatterns, type GlobalId } from "../../definitions/BaseRecord";
import { omitNull, match, filterObject } from "../../../util/Util";
import { parseInteger } from "../../../util/parsers";
import RsSet from "../../../util/set";
import { type Person, type Username } from "../../definitions/Person";
import { type Factory } from "../../definitions/Factory";
import {
  parseResultType,
  type ResultType,
  parseDeletedItems,
  type DeletedItems,
  type CoreFetcherArgs,
  type Permalink,
  type ParentGlobalIdType,
  type PermalinkType,
} from "../../definitions/Search";
import { type InventoryRecord } from "../../definitions/InventoryRecord";
import { type Order, parseOrder } from "../../../util/types";
import { pick } from "../../../util/unsafeUtils";
import Result from "../../../util/result";

export const DEFAULT_SEARCH = {
  query: "",
  pageSize: 10,
  pageNumber: 0,
  orderBy: "modificationDate",
  order: "desc",
  parentGlobalId: null,
  resultType: "ALL",
  ownedBy: null,
  owner: null,
  deletedItems: "EXCLUDE",
  permalink: null,
  benchOwner: null,
};

export const DEFAULT_FETCHER = {
  results: ([]: Array<InventoryRecord>),
  loading: false,
  count: 0,
  error: "",
};

const omitDefault = <T: {}>(obj: T): T => {
  Object.keys({ ...obj })
    .filter((k) => obj[k] === DEFAULT_SEARCH[k])
    .forEach((k) => delete obj[k]);
  return obj;
};

export const parseCoreFetcherArgsFromUrl = (
  searchParams: URLSearchParams
): CoreFetcherArgs => {
  const query = searchParams.get("query");
  const pageSize = Result.fromNullable(
    searchParams.get("pageSize"),
    new Error(`Search parameter "pageSize" is missing`)
  ).flatMap(parseInteger);
  const pageNumber = Result.fromNullable(
    searchParams.get("pageNumber"),
    new Error(`Search parameter "pageNumber" is missing`)
  ).flatMap(parseInteger);
  const orderBy = searchParams.get("orderBy");
  const order = Result.fromNullable(
    searchParams.get("order"),
    new Error(`Search parameter "order" is missing`)
  ).flatMap(parseOrder);
  const parentGlobalId = searchParams.get("parentGlobalId");
  const resultType = Result.fromNullable(
    searchParams.get("resultType"),
    new Error(`Search parameter "resultType" is missing`)
  ).flatMap(parseResultType);
  const ownedBy = searchParams.get("ownedBy");
  const deletedItems = Result.fromNullable(
    searchParams.get("deletedItems"),
    new Error(`Search parameter "deletedItems" is missing`)
  ).flatMap(parseDeletedItems);
  // prettier-ignore
  return {
    ...((query          ? { query          } : {}): {| query          ?: string       |}),
    ...((orderBy        ? { orderBy        } : {}): {| orderBy        ?: string       |}),
    ...((ownedBy        ? { ownedBy        } : {}): {| ownedBy        ?: string       |}),
    ...((parentGlobalId ? { parentGlobalId } : {}): {| parentGlobalId ?: string       |}),
    ...(deletedItems.map(dItems  => ({ deletedItems: dItems })).orElse({}): {| deletedItems ?: DeletedItems |}),
    ...(order.map(       o       => ({ order:        o      })).orElse({}): {| order        ?: Order        |}),
    ...(pageNumber.map(  pNumber => ({ pageNumber:   pNumber})).orElse({}): {| pageNumber   ?: number       |}),
    ...(pageSize.map(    pSize   => ({ pageSize:     pSize  })).orElse({}): {| pageSize     ?: number       |}),
    ...(resultType.map(  rType   => ({ resultType:   rType  })).orElse({}): {| resultType   ?: ResultType   |}),
  };
};

/*
 * Rather than update the parameters of the whole page's search (i.e.
 * SearchStore's) a lot of the code generates the URL that includes those
 * parameters and navigates to it using react-router. This updates the URL and
 * SearchRouter then updates the search parameters. This flow ensures that
 * everything stays in sync. This method is for code that does not wish to
 * simply amend the current search parameters, but instead to replace them all
 * with either specified values or else their defaults.
 */
export const generateUrlFromCoreFetcherArgs = (
  fetcherArgs: CoreFetcherArgs
): string => {
  const params = pick(...Object.keys(DEFAULT_SEARCH))({
    ...DEFAULT_SEARCH,
    ...fetcherArgs,
  });
  delete params.permalink;
  delete params.benchOwner;
  delete params.owner;
  const searchParams = new URLSearchParams(omitDefault(omitNull(params)));
  return `/inventory/search?${searchParams.toString()}`;
};

export default class CoreFetcher {
  results: Array<InventoryRecord> = [];
  loading: boolean;
  count: number;
  error: string;
  endpoint: string = "search";
  query: ?string;
  resultType: ?ResultType;
  pageNumber: number;
  pageSize: number;
  orderBy: string;
  order: string;
  parentGlobalId: ?GlobalId;
  permalink: ?Permalink;
  ownedBy: ?Username;
  owner: ?Person;
  deletedItems: DeletedItems;
  benchOwner: ?Person;
  factory: Factory;

  constructor(
    factory: Factory,
    params: ?CoreFetcherArgs = {
      ...DEFAULT_SEARCH,
      ...DEFAULT_FETCHER,
    }
  ) {
    makeObservable(this, {
      results: observable,
      loading: observable,
      count: observable,
      error: observable,
      endpoint: observable,
      query: observable,
      resultType: observable,
      pageNumber: observable,
      pageSize: observable,
      orderBy: observable,
      order: observable,
      parentGlobalId: observable,
      permalink: observable,
      ownedBy: observable,
      owner: observable,
      deletedItems: observable,
      benchOwner: observable,
      setAttributes: action,
      setPage: action,
      setOrder: action,
      setPageSize: action,
      setEndpoint: action,
      performInitialSearch: action,
      search: action,
      generateParams: action,
      applySearchParams: action,
      generateQuery: action,
      generateNewQuery: action,
      setResults: action,
      addResults: action,
      replaceResult: action,
      resetSearch: action,
      setDeletedItems: action,
      setParentGlobalId: action,
      setOwner: action,
      setBenchOwner: action,
      setLoading: action,
      setResultType: action,
      parentGlobalIdType: computed,
      isOrderDesc: computed,
      parentIsContainer: computed,
      parentIsSample: computed,
      parentIsBench: computed,
      basketSearch: computed,
      allTypesAllowed: computed,
      deletedItemsLabel: computed,
      serialize: computed,
    });
    this.setAttributes({
      ...DEFAULT_SEARCH,
      ...DEFAULT_FETCHER,
      ...params,
    });
    this.factory = factory;
  }

  setLoading(value: boolean): void {
    this.loading = value;
  }

  setAttributes(params: CoreFetcherArgs): void {
    Object.assign(this, params);
  }

  async setPage(pageNumber: number): Promise<void> {
    this.pageNumber = pageNumber;
    await this.reperformCurrentSearch();
  }

  setOrder(order: Order, orderBy: string): void {
    this.order = order;
    this.orderBy = orderBy;
    void this.setPage(0);
  }

  setPageSize(pageSize: number): void {
    this.pageSize = pageSize;
  }

  setEndpoint() {
    if (this.permalink) {
      const type = this.permalink.type;
      this.endpoint = match<PermalinkType, string>([
        [(t) => t === "sample", "samples"],
        [(t) => t === "container", "containers"],
        [(t) => t === "subsample", "subSamples"],
        [(t) => t === "sampletemplate", "sampleTemplates"],
      ])(type);
      return;
    }
    this.endpoint = "search";
    if (!this.parentGlobalId) {
      if (!this.query && this.resultType === "SAMPLE") {
        this.endpoint = "samples";
      }
      if (!this.query && this.resultType === "SUBSAMPLE") {
        this.endpoint = "subSamples";
      }
      if (!this.query && this.resultType === "CONTAINER") {
        this.endpoint = "containers";
      }
      if (!this.query && this.resultType === "TEMPLATE") {
        this.endpoint = "sampleTemplates";
      }
    }
  }

  /*
   * Clears the search state -- the search results, loading flag, error, etc --
   * and performs a new search with a merging of the passed `params` and the
   * existing search parameters. If you want to perform a search with all of
   * the search parameters cleared then call `setupAndPerformInitialSearch` on
   * the instance of the Search class that owns this CoreFetcher.
   */
  async performInitialSearch(params: ?CoreFetcherArgs = null) {
    this.resetSearch();
    await this.search(params, (r) => this.setResults(r));
  }

  /*
   * Reperform a search with the currently set search paramters.
   */
  async reperformCurrentSearch() {
    await this.search(null, (r) => this.setResults(r));
  }

  async search(
    _params: ?CoreFetcherArgs = null,
    storeResults: (Array<InventoryRecord>) => void
  ) {
    this.setLoading(true);

    let params = _params ?? this.generateParams();

    // Set a default filter if the query is empty so that we don't trigger an error
    if (
      (!params.resultType || params.resultType === "ALL") &&
      !params.parentGlobalId &&
      !params.query &&
      !params.permalink
    ) {
      params = { ...params, resultType: "CONTAINER" };
      runInAction(() => {
        this.resultType = "CONTAINER";
      });
    }

    params = this.applySearchParams(params);
    this.setEndpoint();
    const endpoint = this.endpoint;

    try {
      if (endpoint !== "search" && params.permalink) {
        const slug = params.permalink.version
          ? `${params.permalink.id}/versions/${params.permalink.version}`
          : params.permalink.id;
        const { data } = await ApiService.get<void, any>(endpoint, slug);
        runInAction(() => {
          this.count = 1;
        });
        const record = this.factory.newFactory().newRecord(data);
        storeResults([record]);

        /*
         * If the permalink points to a deleted item then the search parameter
         * chips need to show "In Trash" rather than "Current"
         */
        this.setDeletedItems(record.deleted ? "DELETED_ONLY" : "EXCLUDE");
      } else {
        if (params.resultType === "ALL") {
          params.resultType = null;
        }
        /*
         * A URLSearchParams object, rather than `params` as it is, is passed
         * to ApiService.query so that square brackets are properly encoded.
         */
        const { data } = await ApiService.query<void, any>(
          endpoint,
          // $FlowExpectedError[incompatible-call]
          new URLSearchParams(omitNull(params))
        );
        const records = match<void, Array<{ ... }>>([
          [() => endpoint === "search", data.records],
          [() => endpoint === "samples", data.samples],
          [() => endpoint === "subSamples", data.subSamples],
          [() => endpoint === "containers", data.containers],
          [() => endpoint === "sampleTemplates", data.templates],
        ])();
        runInAction(() => {
          this.count = data.totalHits || records.length;
        });
        const factory = this.factory.newFactory();
        storeResults(
          records.map((result) => {
            const newRecord = factory.newRecord(result);
            newRecord.populateFromJson(factory, result);
            return newRecord;
          })
        );
      }
      if (this.endpoint !== endpoint)
        console.warn(
          "search.endpoint has changed during fetching, which may result in buggy behaviour."
        );
      this.setLoading(false);
    } catch (error) {
      this.resetSearch();
      getRootStore().uiStore.addAlert(
        mkAlert({
          title: `Could not perform search.`,
          message:
            error.response?.data.message ?? error.message ?? "Unknown reason.",
          variant: "error",
          details:
            error.response?.data.errors.map((error) => ({
              title: error,
              variant: "error",
            })) ?? [],
        })
      );
      console.error("Could not perform search with parameters", params, error);
    }
  }

  /*
   * Creates a full, but minimal, CoreFetcherArgs object using the params that
   * have been explicitly edited by the user, else the params already set on
   * `this`, else defaults where values are required or omitting them where
   * they are not.
   */
  generateParams(editedParams: ?CoreFetcherArgs | {} = {}): CoreFetcherArgs {
    const keys = new Set([...Object.keys(DEFAULT_SEARCH), "permalink"]);

    // $FlowExpectedError[cannot-spread-indexer] There's no way flow can tell what's going on here
    const params = {
      ...DEFAULT_SEARCH,
      ...filterObject((k) => keys.has(k), { ...this }),
      ...editedParams,
    };

    return omitDefault(omitNull(params));
  }

  /*
   * Stores the passed fetcher args and returns them in a form for sending to
   * the API, removing any data stored on `this` that cannot be serialised or
   * should not be passed to the server.
   */
  applySearchParams(params: CoreFetcherArgs): CoreFetcherArgs {
    this.setAttributes(params);
    const preparedParams: CoreFetcherArgs = Object.entries(
      DEFAULT_SEARCH
    ).reduce(
      (acc, [k, v]) => ({
        ...acc,
        // $FlowExpectedError[invalid-computed-prop]
        [k]: acc[k] || this[k] || v,
      }),
      params
    );
    preparedParams.orderBy = `${String(preparedParams.orderBy)} ${String(
      preparedParams.order
    )}`;
    delete preparedParams.order;
    delete preparedParams.owner;
    delete preparedParams.benchOwner;
    return preparedParams;
  }

  /*
   * Creates a minimal URLSearchParams object, encompassing the parts of the
   * fetcher args that can be URL serialised, from params that have been
   * explcitly set by the user (the passed argument), those already set on
   * `this`, or else the defaults.
   */
  generateQuery(editedParams: CoreFetcherArgs): URLSearchParams {
    const params = pick(...Object.keys(DEFAULT_SEARCH))(
      this.generateParams(editedParams)
    );

    // These aren't URL serialisable
    delete params.owner;
    delete params.benchOwner;
    delete params.permalink;

    params.pageNumber = 0;

    return new URLSearchParams(omitDefault(omitNull(params)));
  }

  /*
   * Creates a minimal URLSearchParams object encompassing the parameters
   * required for a default search, unless otherwise specified in the passed
   * argument. Most notably, unlike `generateQuery`, this ignores any values
   * currently set on `this`.
   */
  generateNewQuery(editedParams: CoreFetcherArgs): URLSearchParams {
    const params = pick(...Object.keys(DEFAULT_SEARCH))(
      this.generateParams({
        ...DEFAULT_SEARCH,
        ...editedParams,
      })
    );
    // Don't need to delete those that aren't serialisable as they are null.
    return new URLSearchParams(omitDefault(omitNull(params)));
  }

  /*
   * A plain object instantiation of CoreFetcherArgs, where complex objects
   * have been removed or simplified, for the purposes of serialising out to
   * JSON (e.g. for caching/localStorage)
   */
  get serialize(): Partial<CoreFetcherArgs> {
    const keysOfComplexData = new RsSet(["owner", "benchOwner", "permalink"]);
    const keysOfSimpleData: RsSet<string> = (new RsSet(
      Object.keys(DEFAULT_SEARCH)
    ): RsSet<string>).subtract(keysOfComplexData);
    // $FlowExpectedError[incompatible-type]
    // $FlowExpectedError[incompatible-return]
    // $FlowExpectedError[prop-missing]
    // $FlowExpectedError[incompatible-indexer]
    // $FlowExpectedError[incompatible-exact]
    // $FlowExpectedError[incompatible-call]
    return filterObject((k) => keysOfSimpleData.has(k), {
      ...DEFAULT_SEARCH,
      ...this,
    });
  }

  setResults(results: Array<InventoryRecord> = []): void {
    this.results = results;
  }

  addResults(
    prependResults: Array<InventoryRecord> = [],
    appendResults: Array<InventoryRecord> = []
  ): void {
    this.results = [...prependResults, ...this.results, ...appendResults];
  }

  replaceResult(result: InventoryRecord): void {
    this.results = this.results.map((r) =>
      r.globalId === result.globalId ? result : r
    );
  }

  resetSearch(): void {
    this.setResults();
    this.pageNumber = 0;
    this.setAttributes({ ...DEFAULT_FETCHER });
    this.permalink = null;
  }

  resetFetcher(): void {
    this.setAttributes({
      ...DEFAULT_SEARCH,
      ...DEFAULT_FETCHER,
    });
  }

  get parentGlobalIdType(): ?ParentGlobalIdType {
    return match<?GlobalId, ?ParentGlobalIdType>([
      [(id) => globalIdPatterns.sample.test(id ?? ""), "SAMPLE"],
      [(id) => globalIdPatterns.subsample.test(id ?? ""), "SUBSAMPLE"],
      [(id) => globalIdPatterns.container.test(id ?? ""), "CONTAINER"],
      [(id) => globalIdPatterns.sampleTemplate.test(id ?? ""), "TEMPLATE"],
      [(id) => globalIdPatterns.bench.test(id ?? ""), "BENCH"],
      [(id) => globalIdPatterns.basket.test(id ?? ""), "BASKET"],
      [(id) => !id, null],
    ])(this.parentGlobalId);
  }

  setDeletedItems(value: DeletedItems) {
    this.deletedItems = value;
  }

  setResultType(resultType: ResultType) {
    this.resultType = resultType;
  }

  setOwner(owner: ?Person) {
    this.owner = owner;
    this.ownedBy = owner?.username;
  }

  setBenchOwner(owner: ?Person) {
    this.benchOwner = owner;
    if (owner?.workbenchId) {
      this.parentGlobalId = `BE${owner.workbenchId}`;
    } else {
      this.parentGlobalId = null;
    }
  }

  setParentGlobalId(parentGlobalId: ?GlobalId) {
    this.parentGlobalId = parentGlobalId;
  }

  get isOrderDesc(): boolean {
    return this.order === "desc";
  }

  isCurrentSort(key: string): boolean {
    return this.orderBy === key;
  }

  invertSortOrder(): Order {
    return this.isOrderDesc ? "asc" : "desc";
  }

  defaultSortOrder(key: string): Order {
    return ["creationDate", "modificationDate"].includes(key) ? "desc" : "asc";
  }

  /* context-related */

  get parentIsContainer(): boolean {
    return globalIdPatterns.container.test(this.parentGlobalId ?? "");
  }

  get parentIsSample(): boolean {
    return globalIdPatterns.sample.test(this.parentGlobalId ?? "");
  }

  get parentIsTemplate(): boolean {
    return globalIdPatterns.sampleTemplate.test(this.parentGlobalId ?? "");
  }

  get parentIsBench(): boolean {
    return globalIdPatterns.bench.test(this.parentGlobalId ?? "");
  }

  get basketSearch(): boolean {
    return globalIdPatterns.basket.test(this.parentGlobalId ?? "");
  }

  get allTypesAllowed(): boolean {
    return Boolean(this.query) || Boolean(this.parentGlobalId);
  }

  get deletedItemsLabel(): string {
    return match<void, string>([
      [() => this.deletedItems === "EXCLUDE", "Current"],
      [() => this.deletedItems === "INCLUDE", "Current & In Trash"],
      [() => this.deletedItems === "DELETED_ONLY", "In Trash"],
    ])();
  }
}
