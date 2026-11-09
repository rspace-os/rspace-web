import { useQuery } from "@tanstack/react-query";
import { uniq } from "es-toolkit";
import { useMemo, useState } from "react";
import type { ErrorType } from "@/modules/common/api/v2/apiV2Fetch";
import { isRuntimeFieldInNamespace } from "@/modules/common/collection/utils";
import { useQueryKeyScope } from "@/modules/common/queries/queryIdentityScope";
import { type ApiV2CollectionMetadata, apiV2CollectionMetadataFor } from "./metadata";
import type { ApiV2CollectionFetchOptions, ApiV2CollectionResourceOf, ApiV2FilterResourceOf } from "./requestParams";
import type { RuntimeFieldCatalogForNamespace } from "./runtimeFieldCatalog";
import {
  fetchRuntimeFieldDefinitions,
  type RuntimeFieldDefinition,
  registeredRuntimeFieldCatalog,
} from "./runtimeFieldCatalog";

type Selection = { namespace: string; definition: RuntimeFieldDefinition };

const emptySelections: readonly Selection[] = [];
const noCatalogKey = ["apiV2RuntimeFieldCatalog"] as const;

/**
 * Caller-dependent definitions shared by collection and custom-endpoint filter controls.
 * Check `pending` and `error` before treating an empty `missing` list as authoritative.
 */
export function useApiV2RuntimeFields<TDocument, TEntry extends ApiV2CollectionResourceOf<TEntry>>({
  resource,
  selectors,
  request,
  metadata: suppliedMetadata,
}: {
  resource: TEntry;
  selectors: readonly string[];
  request?: ApiV2CollectionFetchOptions<TDocument, ApiV2FilterResourceOf<NoInfer<TEntry>>>;
  metadata?: ApiV2CollectionMetadata<TDocument>;
}) {
  const resourceName = resource.name;
  const metadata = suppliedMetadata ?? apiV2CollectionMetadataFor<TDocument>(resourceName);
  const scope = useQueryKeyScope(request?.authScope);
  // Only namespaces whose catalog route a registered resource publishes offer runtime-field selectors.
  const namespaces = (metadata.runtimeFields ?? []).flatMap((namespace) => {
    const endpoint = registeredRuntimeFieldCatalog(namespace);
    return endpoint ? [{ ...namespace, endpoint }] : [];
  });
  const [firstNamespace] = namespaces;
  const namespaceKeys = namespaces.map(({ namespace, catalog }) => [namespace, catalog]);
  const selectionScope = JSON.stringify([scope, resourceName, namespaceKeys]);
  const [selection, setSelection] = useState<{ scope: string; fields: readonly Selection[] }>({
    scope: selectionScope,
    fields: emptySelections,
  });
  const selected = selection.scope === selectionScope ? selection.fields : emptySelections;
  const requested = uniq(selectors)
    .filter((name) => namespaces.some((namespace) => isRuntimeFieldInNamespace(name, namespace.namespace)))
    .sort();
  const unresolved = requested.filter(
    (name) => !selected.some((entry) => name === `${entry.namespace}.${entry.definition.id}`),
  );
  // Plain useQuery: runtime-field definitions are optional; callers show pending, error and retry inline.
  const hydrated = useQuery<RuntimeFieldCatalogForNamespace[], ErrorType<unknown>>({
    queryKey: [
      ...(firstNamespace?.endpoint.queryKey({ ids: unresolved.join(",") }) ?? noCatalogKey),
      "runtimeFieldDefinitions",
    ],
    enabled: unresolved.length > 0,
    queryFn: async ({ signal }) => {
      return Promise.all(
        namespaces.map(async (namespace) => ({
          namespace: namespace.namespace,
          definitions: [
            ...(await fetchRuntimeFieldDefinitions(
              namespace.endpoint,
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
  const selectRuntimeField = (namespace: string, definition: RuntimeFieldDefinition) => {
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
  };
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
