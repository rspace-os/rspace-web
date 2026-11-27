import type { ApiV2CollectionMetadata } from "../../api-v2/metadata";

type Metadata<TDocument> = ApiV2CollectionMetadata<TDocument>;

/**
 * API v2 collection metadata for a test: no sorting, no selectors, the usual filter limits and paging, with
 * `overrides` supplying the collection's own parts. `filtering` overrides merge into the default filtering.
 */
export function metadataFixture<TDocument>({
  filtering,
  ...overrides
}: Pick<Metadata<TDocument>, "resourceName" | "fields"> &
  Partial<Omit<Metadata<TDocument>, "filtering">> & {
    filtering?: Partial<Metadata<TDocument>["filtering"]>;
  }): Metadata<TDocument> {
  return {
    sorting: { fields: [], default: [], maximumFields: 5 },
    pagination: { defaultLimit: 20, maximumLimit: 100 },
    ...overrides,
    filtering: {
      selectors: {},
      limits: {
        maximumComparisons: 50,
        maximumLikeComparisons: 10,
        maximumNesting: 10,
        maximumArguments: 100,
        maximumWhereLength: 4096,
      },
      ...filtering,
    },
  };
}
