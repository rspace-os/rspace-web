import { describe, expect, it } from "vitest";
import { type ApiV2OperationSet, type ApiV2ResourceEntry, apiV2Resources } from "../apiV2Resources";
import { getListInstrumentsQueryKey, listInstruments } from "../generated/instruments/instruments";

describe("apiV2Resources", () => {
  it("pairs generated reads and keys and omits unsupported capabilities", () => {
    expect(apiV2Resources.instruments.list.request).toBe(listInstruments);
    expect(apiV2Resources.instruments.list.queryKey).toBe(getListInstrumentsQueryKey);
    expect(apiV2Resources.instruments).not.toHaveProperty("create");
  });
  it("accepts fake resources with their own parameter and result types", async () => {
    const resource = {
      name: "widgets",
      list: {
        request: async (params: { limit: number }) => ({
          docs: Array.from({ length: params.limit }, (_, id) => ({ id })),
        }),
        queryKey: (params: { limit: number }) => ["widgets", params] as const,
      },
    } satisfies ApiV2ResourceEntry<"widgets", ApiV2OperationSet>;
    expect((await resource.list.request({ limit: 2 })).docs).toHaveLength(2);
    expect(resource.list.queryKey({ limit: 2 })).toEqual(["widgets", { limit: 2 }]);
  });
});
