import { describe, expect, test, vi } from "vitest";
import { parseCoreFetcherArgsFromUrl } from "../../CoreFetcher";

vi.mock("../../../../stores/getRootStore", () => ({
  default: () => ({}),
}));

describe("parseCoreFetcherArgsFromUrl", () => {
  test.each([
    ["resultType=sample", { resultType: "SAMPLE" }],
    ["resultType=sample_template", { resultType: "SAMPLE_TEMPLATE" }],
    ["deletedItems=deleted_only", { deletedItems: "DELETED_ONLY" }],
    ["resultType=SAMPLE", { resultType: "SAMPLE" }],
    ["resultType=foo", {}],
  ])("%s", (query, expected) => {
    expect(parseCoreFetcherArgsFromUrl(new URLSearchParams(query))).toEqual(expected);
  });
});
