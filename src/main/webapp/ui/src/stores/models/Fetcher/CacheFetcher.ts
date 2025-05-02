import { observable, override, makeObservable, runInAction } from "mobx";
import CoreFetcher from "./CoreFetcher";
import {
  type CoreFetcherArgs,
  type CacheFetcher as CacheFetcherInterface,
} from "../../definitions/Search";
import { type Factory } from "../../definitions/Factory";
import { type InventoryRecord } from "../../definitions/InventoryRecord";

export default class CacheFetcher
  extends CoreFetcher
  implements CacheFetcherInterface
{
  cachedResults: Array<InventoryRecord> = [];
  cachedPageSize: number = 0;

  constructor(factory: Factory, params: ?CoreFetcherArgs) {
    super(factory, params);
    makeObservable(this, {
      cachedResults: observable,
      cachedPageSize: observable,
      setPage: override,
      performInitialSearch: override,
      search: override,
      setAttributes: override,
      setBenchOwner: override,
    });
  }

  setAttributes(params: CoreFetcherArgs): void {
    // Some search filters are not allowed in grid and image view
    const p = { ...params };
    delete p.benchOwner;
    delete p.deletedItems;

    super.setAttributes(p);
  }

  setBenchOwner(_: mixed) {
    // do nothing; bench filter is not allowed in grid and image view
  }

  setDeletedItems(_: mixed) {
    // do nothing; status filter is not allowed in grid and image view
  }

  async setPage(pageNumber: number): Promise<void> {
    this.pageNumber = pageNumber;
  }

  performInitialSearch(params: ?CoreFetcherArgs = null): Promise<void> {
    if (params?.query || params?.resultType || params?.owner) {

      /*
       * this.results will be set on the first call, or when instances of this
       * class are instantiated. Thereafer, we keep a reference to this
       * original search results (this.cachedResults) so that we can show the
       * records that don't appear in new search but in a disabled/greyed-out
       * state. We don't want to remove them completely as we would in
       * list/card/tree view because then the locations would appear empty.
       */
      runInAction(() => {
        this.cachedResults = this.cachedResults.length
          ? this.cachedResults
          : this.results;
        this.cachedResults.map((r) => r.toggleSelected(false));
        this.cachedPageSize = this.cachedPageSize || this.pageSize;
      });

      return super.performInitialSearch({
        ...params,
        pageSize: this.results.length,
      });
    } else {

      /*
       * If we're no longer filtering grid/image view by one of the allowed
       * search parameters, then we simply restore the cached search results.
       */
      runInAction(() => {
        if (this.cachedResults.length) {
          this.results = this.cachedResults;
          this.pageSize = this.cachedPageSize;
        }
      });

      return this.search(params, (results: Array<InventoryRecord>) =>
        this.setResults(results)
      );
    }
  }

  async search(
    params: ?CoreFetcherArgs = null,
    storeResults: (Array<InventoryRecord>) => void
  ) {
    if (params?.query || params?.resultType || params?.owner) {
      await super.search(params, storeResults);
    } else {
      if (!params) params = this.generateParams();
      params = this.applySearchParams(params);
      this.setEndpoint();
    }
  }
}
