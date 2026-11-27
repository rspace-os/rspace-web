import { createContext, type ReactNode, useContext } from "react";
import type { ApiV2RuntimeFieldNamespace } from "./metadata";
import {
  bindRuntimeFieldSearch,
  type RuntimeFieldSearch,
  type RuntimeFieldSearchResource,
  registeredRuntimeFieldSearches,
  runtimeFieldCatalogNamespace,
} from "./runtimeFieldCatalog";

type RuntimeFieldResource = RuntimeFieldSearchResource<string>;
const RuntimeFieldResources = createContext<Readonly<Record<string, RuntimeFieldResource>>>({});

/** Fake entries let standalone filter stories supply catalogs without replacing global fetch. */
export function RuntimeFieldResourcesProvider({
  resources,
  children,
}: {
  resources: Readonly<Record<string, RuntimeFieldResource>>;
  children: ReactNode;
}) {
  return <RuntimeFieldResources.Provider value={resources}>{children}</RuntimeFieldResources.Provider>;
}

/**
 * The searches serving these namespaces, with the sources each answers: one per injected fake, covering every
 * namespace it serves, else one per resource that publishes the remaining routes.
 */
export function useRuntimeFieldSearches() {
  const resources = useContext(RuntimeFieldResources);
  return <S extends Pick<ApiV2RuntimeFieldNamespace, "namespace" | "catalog">>(
    sources: readonly S[],
  ): readonly { search: RuntimeFieldSearch; sources: readonly S[] }[] => {
    const injected = [
      ...Map.groupBy(sources, (source): RuntimeFieldResource | undefined => resources[source.namespace]),
    ].flatMap(([resource, group]) => {
      const search =
        resource &&
        bindRuntimeFieldSearch(
          resource,
          group.map((source) => source.namespace),
        );
      if (!search) return [];
      const answered = group.filter((source) =>
        search.namespaces.includes(runtimeFieldCatalogNamespace(source.namespace)),
      );
      return [{ search, sources: answered }];
    });
    const served = new Set(injected.flatMap(({ sources: answered }) => answered));
    return [...injected, ...registeredRuntimeFieldSearches(sources.filter((source) => !served.has(source)))];
  };
}
