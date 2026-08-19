import { useSuspenseQuery } from "@tanstack/react-query";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import type {
  CollectionConfig,
  CollectionRow,
  FieldName,
  ResolvedCollectionConfig,
} from "@/modules/common/collection/collectionConfig";
import type { CollectionFetcher } from "../../tableListState";
import { type UseTableListOptions, type UseTableListResult, useTableList } from "../../useTableList";
import { type ApiV2CollectionMetadata, fetchApiV2CollectionMetadata } from "./apiV2CollectionMetadata";
import {
  type ApiV2CollectionAdapter,
  type ApiV2DocumentSchema,
  createApiV2CollectionAdapter,
} from "./createApiV2CollectionAdapter";
import {
  type ApiV2CollectionFetchOptions,
  apiV2CollectionRequestParams,
  createApiV2CollectionFetcher,
} from "./createApiV2CollectionFetcher";

type TableOptions<TRow extends Record<string, unknown>> = Omit<UseTableListOptions<TRow>, "config" | "dataSource">;

export type UseApiV2TableListOptions<
  TDocument extends Record<string, unknown>,
  TId extends FieldName<TDocument> = FieldName<TDocument>,
  TTitle extends FieldName<TDocument> = FieldName<TDocument>,
> = {
  resourceName: string;
  config: CollectionConfig<TDocument, TId, TTitle>;
  documentSchema: ApiV2DocumentSchema<TDocument>;
  request?: ApiV2CollectionFetchOptions<TDocument>;
  metadata?: ApiV2CollectionMetadata<TDocument>;
  table?: TableOptions<CollectionRow<TDocument, TId | TTitle>>;
  query?: {
    staleTime?: number;
    gcTime?: number;
    retry?: boolean | number;
    refetchInterval?: number | false;
    keepPreviousData?: boolean;
  };
};

export type UseApiV2TableListResult<
  TDocument extends Record<string, unknown>,
  TRow extends Record<string, unknown>,
> = UseTableListResult<TRow> & {
  adapter: ApiV2CollectionAdapter<TDocument>;
};

/**
 * A row carries the ID field and the title field at all times, because a request always selects
 * them. Each other field is present when the active request projection selects it.
 */
export function useApiV2TableList<
  TDocument extends Record<string, unknown>,
  const TId extends FieldName<TDocument> = FieldName<TDocument>,
  const TTitle extends FieldName<TDocument> = FieldName<TDocument>,
>({
  resourceName,
  config,
  documentSchema,
  request,
  metadata: suppliedMetadata,
  table,
  query,
}: UseApiV2TableListOptions<TDocument, TId, TTitle>): UseApiV2TableListResult<
  TDocument,
  CollectionRow<TDocument, TId | TTitle>
> {
  type Row = CollectionRow<TDocument, TId | TTitle>;
  const { t } = useTranslation();

  const metadataQuery = useSuspenseQuery({
    queryKey: ["api-v2", "openapi", resourceName],
    queryFn: ({ signal }) =>
      suppliedMetadata ?? fetchApiV2CollectionMetadata<TDocument>(resourceName, { fetch: request?.fetch, signal }),
    staleTime: Infinity,
    gcTime: Infinity,
  });
  const adapter = useMemo(
    () =>
      createApiV2CollectionAdapter({
        config,
        documentSchema,
        metadata: metadataQuery.data,
        translate: (key) => t(key as never),
      }),
    [config, documentSchema, metadataQuery.data, t],
  );
  const fetchCollection = useMemo(() => createApiV2CollectionFetcher(adapter, request), [adapter, request]);
  const result = useTableList<Row>({
    ...table,
    // The configuration keeps the strict document type, and a row carries the projection. A field
    // renderer reads its own field only, and it declares each other field it reads in
    // `list.dependencies`, which the request then selects. A renderer therefore never reads a field
    // that the response omitted.
    config: adapter.config as unknown as ResolvedCollectionConfig<Row>,
    dataSource: {
      type: "remote",
      queryKey: (state) => [
        "api-v2",
        resourceName,
        apiV2CollectionRequestParams(adapter, state, request?.depth, request?.projection).toString(),
      ],
      fetch: fetchCollection as unknown as CollectionFetcher<Row>,
      ...query,
    },
  });
  return { ...result, adapter };
}
