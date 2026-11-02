import { useQuery, useSuspenseQuery } from "@tanstack/react-query";
import { useCallback, useMemo, useState } from "react";
import { isRuntimeFieldInNamespace } from "@/modules/common/collection/utils";
import { apiV2RuntimeFieldOptions } from "@/modules/common/queries/apiV2QueryKeys";
import { useQueryIdentityScope } from "@/modules/common/queries/queryIdentityScope";
import { type ApiV2CollectionFetchOptions, requestHeaders } from "./fetcher";
import { type ApiV2CollectionMetadata, apiV2MetadataOptions } from "./metadata";
import { fetchRuntimeFieldDefinitions, type RuntimeFieldDefinition } from "./runtimeFieldCatalog";

type Selection = { namespace: string; definition: RuntimeFieldDefinition };

const emptySelections: readonly Selection[] = [];

/**
 * Caller-dependent definitions shared by collection and custom-endpoint filter controls.
 * Check `pending` and `error` before treating an empty `missing` list as authoritative.
 */
export function useApiV2RuntimeFields<TDocument>({
  resourceName,
  selectors,
  request,
  metadata: suppliedMetadata,
}: {
  resourceName: string;
  selectors: readonly string[];
  request?: ApiV2CollectionFetchOptions<TDocument>;
  metadata?: ApiV2CollectionMetadata<TDocument>;
}) {
  const metadataQuery = useSuspenseQuery(
    apiV2MetadataOptions(resourceName, { fetch: request?.fetch, metadata: suppliedMetadata }),
  );
  const metadata = suppliedMetadata ?? metadataQuery.data;
  const identityScope = useQueryIdentityScope(request?.authScope);
  const scope = JSON.stringify([identityScope, request?.authScope ?? null]);
  const namespaces = metadata.runtimeFields ?? [];
  const selectionScope = JSON.stringify([
    scope,
    resourceName,
    namespaces.map(({ namespace, catalog }) => [namespace, catalog]),
  ]);
  const [selection, setSelection] = useState<{ scope: string; fields: readonly Selection[] }>({
    scope: selectionScope,
    fields: emptySelections,
  });
  const selected = selection.scope === selectionScope ? selection.fields : emptySelections;
  if (namespaces.length > 0 && typeof request?.token === "function" && request.authScope === undefined) {
    throw new Error("Runtime field token callbacks require an authScope");
  }
  const requested = [...new Set(selectors)]
    .filter((name) => namespaces.some((namespace) => isRuntimeFieldInNamespace(name, namespace.namespace)))
    .sort();
  const unresolved = requested.filter(
    (name) => !selected.some((entry) => name === `${entry.namespace}.${entry.definition.id}`),
  );
  const hydrated = useQuery({
    ...apiV2RuntimeFieldOptions(
      "ids",
      [resourceName, scope, namespaces.map((namespace) => [namespace.namespace, namespace.catalog]), unresolved],
      async ({ signal }) => {
        const headers = await requestHeaders(request);
        return Promise.all(
          namespaces.map(async (namespace) => ({
            namespace: namespace.namespace,
            definitions: await fetchRuntimeFieldDefinitions(
              namespace,
              unresolved
                .filter((name) => isRuntimeFieldInNamespace(name, namespace.namespace))
                .map((name) => name.slice(namespace.namespace.length + 1)),
              { fetch: request?.fetch, headers, signal },
            ),
          })),
        );
      },
    ),
    enabled: unresolved.length > 0,
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
