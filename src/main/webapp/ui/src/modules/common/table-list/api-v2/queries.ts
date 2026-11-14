import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback, useMemo, useState } from "react";
import type { ErrorType } from "@/modules/common/api/v2/apiV2Fetch";
import type { ApiV2ReadOperation, ApiV2ResourceEntry } from "@/modules/common/api/v2/apiV2Resources";
import {
  getGetApiV2OpenApiDocumentQueryKey,
  getGetApiV2OpenApiDocumentQueryOptions,
} from "@/modules/common/api/v2/generated/openapi/openapi";
import { isRuntimeFieldInNamespace } from "@/modules/common/collection/utils";
import { useQueryIdentityScope } from "@/modules/common/queries/queryIdentityScope";
import type { ApiV2CollectionFetchOptions } from "./fetcher";
import { type ApiV2CollectionMetadata, apiV2CollectionMetadataFromOpenApi } from "./metadata";
import type { RuntimeFieldCatalogForNamespace } from "./runtimeFieldCatalog";
import {
  fetchRuntimeFieldDefinitions,
  type RuntimeFieldDefinition,
  runtimeFieldCatalogNamespace,
  runtimeFieldCatalogResource,
} from "./runtimeFieldCatalog";

type Selection = { namespace: string; definition: RuntimeFieldDefinition };

const emptySelections: readonly Selection[] = [];

/**
 * Caller-dependent definitions shared by collection and custom-endpoint filter controls.
 * Check `pending` and `error` before treating an empty `missing` list as authoritative.
 */
export function useApiV2RuntimeFields<
  TDocument,
  TEntry extends ApiV2ResourceEntry<string, { runtimeFields?: ApiV2ReadOperation }>,
>({
  resource,
  selectors,
  request,
  metadata: suppliedMetadata,
}: {
  resource: TEntry;
  selectors: readonly string[];
  request?: ApiV2CollectionFetchOptions<TDocument>;
  metadata?: ApiV2CollectionMetadata<TDocument>;
}) {
  const resourceName = resource.name;
  const queryClient = useQueryClient();
  const metadataQuery = useQuery({
    ...getGetApiV2OpenApiDocumentQueryOptions(),
    enabled: suppliedMetadata === undefined,
    select: (document) => suppliedMetadata ?? apiV2CollectionMetadataFromOpenApi<TDocument>(document, resourceName),
    staleTime: Infinity,
    gcTime: Infinity,
  });
  const metadata = suppliedMetadata ?? metadataQuery.data;
  if (metadataQuery.error && !suppliedMetadata) throw metadataQuery.error;
  if (!metadata) throw queryClient.ensureQueryData(getGetApiV2OpenApiDocumentQueryOptions());
  const identityScope = useQueryIdentityScope(request?.authScope);
  const scope = JSON.stringify([identityScope, request?.authScope ?? null]);
  // Only namespaces whose catalog route a registered resource publishes offer runtime-field selectors.
  const namespaces = (metadata.runtimeFields ?? []).flatMap((namespace) => {
    const catalogResource = runtimeFieldCatalogResource(namespace);
    return catalogResource ? [{ ...namespace, catalogResource }] : [];
  });
  const [firstNamespace] = namespaces;
  const namespaceKeys = namespaces.map(({ namespace, catalog }) => [namespace, catalog]);
  const selectionScope = JSON.stringify([scope, resourceName, namespaceKeys]);
  const [selection, setSelection] = useState<{ scope: string; fields: readonly Selection[] }>({
    scope: selectionScope,
    fields: emptySelections,
  });
  const selected = selection.scope === selectionScope ? selection.fields : emptySelections;
  const requested = [...new Set(selectors)]
    .filter((name) => namespaces.some((namespace) => isRuntimeFieldInNamespace(name, namespace.namespace)))
    .sort();
  const unresolved = requested.filter(
    (name) => !selected.some((entry) => name === `${entry.namespace}.${entry.definition.id}`),
  );
  const hydrated = useQuery<RuntimeFieldCatalogForNamespace[], ErrorType<unknown>>({
    queryKey: [
      ...(firstNamespace?.catalogResource.runtimeFields.queryKey(
        runtimeFieldCatalogNamespace(firstNamespace.namespace) as never,
        { ids: unresolved.join(",") } as never,
      ) ?? getGetApiV2OpenApiDocumentQueryKey()),
      "runtimeFieldDefinitions",
    ],
    enabled: unresolved.length > 0,
    queryFn: async ({ signal }) => {
      return Promise.all(
        namespaces.map(async (namespace) => ({
          namespace: namespace.namespace,
          definitions: [
            ...(await fetchRuntimeFieldDefinitions(
              namespace.catalogResource,
              namespace,
              unresolved
                .filter((name) => isRuntimeFieldInNamespace(name, namespace.namespace))
                .map((name) => name.slice(namespace.namespace.length + 1)),
              { signal },
            )),
          ],
        })),
      );
    },
    staleTime: 60_000,
    retry: false,
  });
  const runtimeFields = useMemo(() => {
    const grouped = new Map<string, Map<string, RuntimeFieldDefinition>>();
    for (const entry of hydrated.data ?? []) {
      grouped.set(entry.namespace, new Map(entry.definitions.map((field) => [field.id, field])));
    }
    for (const entry of selected) {
      const fields = grouped.get(entry.namespace) ?? new Map<string, RuntimeFieldDefinition>();
      fields.set(entry.definition.id, entry.definition);
      grouped.set(entry.namespace, fields);
    }
    return [...grouped].map(([namespace, definitions]) => ({ namespace, definitions: [...definitions.values()] }));
  }, [hydrated.data, selected]);
  const selectRuntimeField = useCallback(
    (namespace: string, definition: RuntimeFieldDefinition) => {
      setSelection((current) => {
        const fields = current.scope === selectionScope ? current.fields : emptySelections;
        return {
          scope: selectionScope,
          fields: [
            ...fields.filter((entry) => entry.namespace !== namespace || entry.definition.id !== definition.id),
            { namespace, definition },
          ],
        };
      });
    },
    [selectionScope],
  );
  const pending = unresolved.length > 0 && hydrated.isPending;
  const error = unresolved.length > 0 ? hydrated.error : null;
  const missing =
    pending || error
      ? []
      : requested.filter(
          (name) =>
            !runtimeFields.some((entry) =>
              entry.definitions.some((field) => name === `${entry.namespace}.${field.id}`),
            ),
        );
  return { metadata, runtimeFields, selectRuntimeField, pending, error, missing, scope, retry: hydrated.refetch };
}
