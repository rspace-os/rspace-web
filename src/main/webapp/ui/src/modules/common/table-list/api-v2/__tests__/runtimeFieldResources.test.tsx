import { renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it } from "vitest";
import { RuntimeFieldResourcesProvider, useRuntimeFieldSearches } from "../runtimeFieldResources";

describe("useRuntimeFieldSearches", () => {
  it("searches every namespace a shared fake serves in one request", () => {
    const fake = {
      name: "instruments",
      runtimeFieldNamespaces: ["customFields", "extraFields"],
      runtimeFieldSearch: { queryKey: () => [], request: async () => ({ catalogs: {} }) },
    };
    const sources = [
      { namespace: "target.customFields", catalog: "/api/v2/instruments/fields/customFields" },
      { namespace: "target.extraFields", catalog: "/api/v2/instruments/fields/extraFields" },
    ];
    const wrapper = ({ children }: { children: ReactNode }) => (
      <RuntimeFieldResourcesProvider resources={{ "target.customFields": fake, "target.extraFields": fake }}>
        {children}
      </RuntimeFieldResourcesProvider>
    );
    const { result } = renderHook(() => useRuntimeFieldSearches(), { wrapper });
    const searches = result.current(sources);
    expect(searches).toHaveLength(1);
    expect(searches[0].search.namespaces).toEqual(["customFields", "extraFields"]);
    expect(searches[0].sources).toEqual(sources);
  });
});
