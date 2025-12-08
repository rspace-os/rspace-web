import { action, computed, makeObservable, override } from "mobx";
import type { Factory } from "../../definitions/Factory";
import type { InventoryRecord } from "../../definitions/InventoryRecord";
import type { CoreFetcherArgs, DynamicFetcher as DynamicFetcherInterface } from "../../definitions/Search";
import CoreFetcher from "./CoreFetcher";

/**
 * This is so that the number of search results is highly divisible, for
 * distributing over 2, 3, or 4 columns.
 */
export const DYNAMIC_PAGE_SIZE = 12;

export default class DynamicFetcher extends CoreFetcher implements DynamicFetcherInterface {
    constructor(factory: Factory, params: CoreFetcherArgs | null) {
        super(factory, params);
        makeObservable(this, {
            dynamicSearch: action,
            setPage: override,
            performInitialSearch: override,
            nextDynamicPageSize: computed,
        });

        this.pageSize = DYNAMIC_PAGE_SIZE;
    }

    async setPage(pageNumber: number): Promise<void> {
        this.pageNumber = pageNumber;
        await this.search(null, (results: Array<InventoryRecord>) =>
            pageNumber === 0 ? this.setResults(results) : this.addResults([], results),
        );
    }

    dynamicSearch() {
        void this.setPage(this.pageNumber + 1);
    }

    get nextDynamicPageSize(): number {
        return Math.min(DYNAMIC_PAGE_SIZE, this.count - this.results.length);
    }

    async performInitialSearch(params: CoreFetcherArgs | null = null) {
        this.resetSearch();
        if (params) params.pageNumber = 0;
        await this.search(params, (r) => this.addResults([], r));
    }
}
