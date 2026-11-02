import "@/stores/stores/RootStore";
import { runInAction } from "mobx";
import { afterEach, describe, expect, test } from "vitest";
import { prepareContainer } from "@/Inventory/components/Operations/placement";
import { makeMockSubSample } from "@/stores/models/__tests__/SubSampleModel/mocking";
import type Search from "@/stores/models/Search";
import getRootStore from "@/stores/stores/getRootStore";
import { containerAttrs, makeMockContainer } from "./mocking";

const grid = { columnsNumber: 2, rowsNumber: 2, columnsLabelType: "N123", rowsLabelType: "ABC" } as const;

const emptyBox = () =>
  makeMockContainer({
    cType: "GRID",
    gridLayout: grid,
    locationsCount: 4,
    contentSummary: { totalCount: 0, subSampleCount: 0, containerCount: 0, instrumentCount: 0 },
  });

const boxWithA2Taken = () =>
  makeMockContainer({
    cType: "GRID",
    gridLayout: grid,
    locationsCount: 4,
    contentSummary: { totalCount: 1, subSampleCount: 0, containerCount: 1, instrumentCount: 0 },
    locations: [{ id: 11, coordX: 2, coordY: 1, content: containerAttrs({ id: 9, globalId: "IC9", name: "Rack" }) }],
  });

describe("onSelect when choosing empty locations", () => {
  afterEach(() => {
    runInAction(() => {
      const { moveStore } = getRootStore();
      moveStore.isMoving = false;
      moveStore.search = null;
      moveStore.selectedResults = [];
    });
  });

  test("with room for one location, picking another moves the selection there", () => {
    const box = emptyBox();
    prepareContainer(box, 1);
    const [a1, b1] = box.locations ?? [];
    box.onSelect(a1, box.contentSearch);
    box.onSelect(b1, box.contentSearch);
    expect([a1.selected, b1.selected]).toEqual([false, true]);
  });

  test("with room for several, picking past the limit is still refused", () => {
    const box = emptyBox();
    prepareContainer(box, 2);
    const [a1, b1, a2] = box.locations ?? [];
    box.onSelect(a1, box.contentSearch);
    box.onSelect(b1, box.contentSearch);
    box.onSelect(a2, box.contentSearch);
    expect([a1.selected, b1.selected, a2.selected]).toEqual([true, true, false]);
  });

  test("with room for one location, picking an occupied one keeps the current selection", () => {
    const box = boxWithA2Taken();
    prepareContainer(box, 1);
    const a1 = box.findLocation(1, 1);
    const a2 = box.findLocation(2, 1);
    if (!a1 || !a2) throw new Error("Locations missing");
    expect(a2.hasContent).toBe(true);
    box.onSelect(a1, box.contentSearch);
    box.onSelect(a2, box.contentSearch);
    expect([a1.selected, a2.selected]).toEqual([true, false]);
  });

  test("when moving one item, picking another location moves the staged item there", () => {
    const box = emptyBox();
    const item = makeMockSubSample({ id: 101, globalId: "SS101" });
    runInAction(() => {
      const { moveStore } = getRootStore();
      moveStore.isMoving = true;
      moveStore.search = { activeResult: box } as unknown as Search;
      moveStore.selectedResults = [item];
    });
    prepareContainer(box, 1);
    const [a1, b1] = box.locations ?? [];
    box.onSelect(a1, box.contentSearch);
    box.onSelect(b1, box.contentSearch);
    expect([a1.selected, b1.selected]).toEqual([false, true]);
    expect(a1.content).toBeNull();
    expect(b1.content?.globalId).toBe("SS101");
  });
});
