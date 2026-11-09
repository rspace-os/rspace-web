import { createContext, type ReactNode, useContext } from "react";
import type { ApiV2ReadOperation, ApiV2ResourceEntry } from "@/modules/common/api/v2/apiV2Resources";
import type { ApiV2RuntimeFieldNamespace } from "./metadata";
import { runtimeFieldCatalogResource } from "./runtimeFieldCatalog";

type RuntimeFieldResource = ApiV2ResourceEntry<string, { runtimeFields: ApiV2ReadOperation }>;
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

/** The resource serving a namespace's catalog: an injected fake, else the resource that publishes the route. */
export function useRuntimeFieldResources() {
  const resources = useContext(RuntimeFieldResources);
  return (namespace: Pick<ApiV2RuntimeFieldNamespace, "namespace" | "catalog">): RuntimeFieldResource | undefined =>
    resources[namespace.namespace] ?? runtimeFieldCatalogResource(namespace);
}
