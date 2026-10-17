import { useSuspenseQuery } from "@tanstack/react-query";
import { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import type {
  CollectionConfig,
  CollectionRow,
  FieldName,
  ResolvedCollectionConfig,
} from "@/modules/common/collection/collectionConfig";
import type { CollectionFetcher, FilterExpression } from "../../tableListState";
import { type UseTableListOptions, type UseTableListResult, useTableList } from "../../useTableList";
import { savedViewSelectors } from "../../useTableListQueryString";
import { type ApiV2CollectionMetadata, fetchApiV2CollectionMetadata } from "./apiV2CollectionMetadata";
import {
  type ApiV2CollectionAdapter,
  type ApiV2DocumentSchema,
  createApiV2CollectionAdapter,
} from "./createApiV2CollectionAdapter";
import { type ApiV2CollectionFetchOptions, createApiV2CollectionFetcher } from "./createApiV2CollectionFetcher";
import { fetchRuntimeFieldDefinitions, type RuntimeFieldDefinition } from "./runtimeFieldCatalog";

type TableOptions<TRow extends Record<string, unknown>> = Omit<UseTableListOptions<TRow>, "config" | "dataSource">;

function collectFilterFields<TDocument>(expression: FilterExpression<TDocument> | null, into: Set<string>): void {
  if (expression === null) return;
  if (expression.kind === "comparison") {
    into.add(expression.field);
    return;
  }
  for (const child of expression.children) collectFilterFields(child, into);
}

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
  selectRuntimeField: (namespace: string, definition: RuntimeFieldDefinition) => void;
};

async function resolveToken(token: string | (() => string | Promise<string>) | undefined): Promise<string | undefined> {
  return typeof token === "function" ? await token() : token;
}

type RuntimeFieldSelection = {
  namespace: string;
  definition: RuntimeFieldDefinition;
};

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
  const metadata = suppliedMetadata ?? metadataQuery.data;
  const namespaces = metadata.runtimeFields ?? [];
  const endpoint = request?.endpoint ?? `/api/v2/${metadata.resourceName}`;
  const authScope = request?.authScope ?? (typeof request?.token === "string" ? request.token : undefined);
  const dataScope = JSON.stringify([resourceName, endpoint, authScope]);
  const savedRuntimeIds = useMemo(() => {
    const selectors = new Set<string>();
    for (const name of table?.initialState?.visibleFields ?? []) selectors.add(String(name));
    collectFilterFields(table?.initialState?.filters?.expression ?? null, selectors);
    const queryString = table?.queryString ?? true;
    if (queryString !== false) {
      for (const name of savedViewSelectors(config.slug, queryString)) selectors.add(name);
    }
    return [...selectors].filter((name) => namespaces.some((n) => name.startsWith(`${n.namespace}.`))).sort();
  }, [
    config.slug,
    namespaces,
    table?.initialState?.filters?.expression,
    table?.initialState?.visibleFields,
    table?.queryString,
  ]);
  const hydratedQuery = useSuspenseQuery({
    queryKey: [
      "api-v2",
      "runtime-fields",
      "ids",
      resourceName,
      namespaces.map(({ namespace, catalog }) => [namespace, catalog]),
      authScope,
      savedRuntimeIds,
    ],
    queryFn: async ({ signal }) => {
      const token = await resolveToken(request?.token);
      const headers = new Headers();
      if (token) headers.set("Authorization", `Bearer ${token}`);
      return Promise.all(
        namespaces.map(async (namespace) => {
          const ids = savedRuntimeIds
            .filter((name) => name.startsWith(`${namespace.namespace}.`))
            .map((name) => name.slice(namespace.namespace.length + 1))
            .map((name) => (namespace.via === "" ? name : name.slice(name.indexOf(".") + 1)));
          const fields = await fetchRuntimeFieldDefinitions(namespace, ids, {
            fetch: request?.fetch,
            signal,
            headers,
          });
          return [namespace.namespace, { fields }] as const;
        }),
      );
    },
    staleTime: 60_000,
  });
  const [selected, setSelected] = useState<readonly RuntimeFieldSelection[]>([]);
  const selectRuntimeField = useCallback((namespace: string, definition: RuntimeFieldDefinition) => {
    setSelected((current) =>
      current.some((entry) => entry.namespace === namespace && entry.definition.id === definition.id)
        ? current
        : [...current, { namespace, definition }],
    );
  }, []);
  const runtimeFields = useMemo(() => {
    const byNamespace = new Map<string, Map<string, RuntimeFieldDefinition>>();
    for (const [namespace, page] of hydratedQuery.data) {
      const known = byNamespace.get(namespace) ?? new Map<string, RuntimeFieldDefinition>();
      for (const field of page.fields) known.set(field.id, field);
      byNamespace.set(namespace, known);
    }
    for (const entry of selected) {
      const known = byNamespace.get(entry.namespace) ?? new Map<string, RuntimeFieldDefinition>();
      known.set(entry.definition.id, entry.definition);
      byNamespace.set(entry.namespace, known);
    }
    return [...byNamespace.entries()].map(([namespace, fields]) => ({
      namespace,
      definitions: [...fields.values()],
    }));
  }, [hydratedQuery.data, selected]);
  const adapter = useMemo(
    () =>
      createApiV2CollectionAdapter({
        config,
        documentSchema,
        metadata,
        runtimeFields,
        translate: (key, values) => String(t(key as never, values as never)),
      }),
    [config, documentSchema, metadata, runtimeFields, t],
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
        endpoint,
        authScope,
        {
          state,
          depth: request?.depth,
          projection: request?.projection,
          baseFilter: request?.baseFilter,
          metadata,
          runtimeFields,
          config: {
            idField: config.idField,
            useAsTitle: config.useAsTitle,
            listSearchableFields: config.listSearchableFields,
            fields: config.fields.map((field) => ({
              name: field.name,
              type: field.type,
              dependencies: field.list === false ? undefined : field.list?.dependencies,
              relationTo: field.type === "relationship" ? field.relationTo : undefined,
            })),
          },
          schemaFields: Object.keys(documentSchema.entries),
        },
      ],
      dataScope,
      fetch: fetchCollection as unknown as CollectionFetcher<Row>,
      ...query,
    },
  });
  return {
    ...result,
    tableProps: {
      ...result.tableProps,
      onSelectRuntimeField: selectRuntimeField,
      runtimeFieldDefinitions: runtimeFields,
    },
    adapter,
    selectRuntimeField,
  };
}
