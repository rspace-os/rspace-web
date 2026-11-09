import { createContext, type ReactNode, useContext } from "react";
import {
  type ApiV2ReadOperation,
  type ApiV2ResourceEntry,
  apiV2Resources,
} from "@/modules/common/api/v2/apiV2Resources";

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

export function useRuntimeFieldResources() {
  const resources = useContext(RuntimeFieldResources);
  return (namespace: string) => resources[namespace] ?? apiV2Resources.instruments;
}
