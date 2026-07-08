import "@/stores/stores/RootStore";
import { describe, expect, test } from "vitest";
import { ListOfMaterials } from "../../MaterialsModel";

function makeEmptyList() {
  return new ListOfMaterials({
    id: null,
    name: "foo",
    description: "",
    elnFieldId: 0,
    materials: [],
  });
}

describe("pickerSearch.allowedTypeFilters", () => {
  test("INSTRUMENT is an allowed type filter", () => {
    const list = makeEmptyList();
    expect(list.pickerSearch.allowedTypeFilters.has("INSTRUMENT")).toBe(true);
  });

  test("SUBSAMPLE, SAMPLE, and CONTAINER are allowed type filters", () => {
    const list = makeEmptyList();
    const filters = list.pickerSearch.allowedTypeFilters;
    expect(filters.has("SUBSAMPLE")).toBe(true);
    expect(filters.has("SAMPLE")).toBe(true);
    expect(filters.has("CONTAINER")).toBe(true);
  });
});
