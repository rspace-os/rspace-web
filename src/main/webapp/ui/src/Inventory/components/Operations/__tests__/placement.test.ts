import "@/stores/stores/RootStore";
import { describe, expect, it } from "vitest";
import { makeMockBench, makeMockContainer } from "@/stores/models/__tests__/ContainerModel/mocking";
import type { ContainerAttrs } from "@/stores/models/ContainerModel";
import { placementBlocker, prepareContainer, releaseContainer } from "../placement";

const summary = (totalCount: number) => ({
  totalCount,
  subSampleCount: totalCount,
  containerCount: 0,
  instrumentCount: 0,
});

/** A 2x2 grid box with `used` of its four locations taken. */
const gridBox = (used = 0, attrs: Partial<ContainerAttrs> = {}) =>
  makeMockContainer({
    cType: "GRID",
    gridLayout: { columnsNumber: 2, rowsNumber: 2, columnsLabelType: "N123", rowsLabelType: "ABC" },
    locationsCount: 4,
    contentSummary: summary(used),
    ...attrs,
  });

describe("placementBlocker", () => {
  it.each([
    ["deleted", () => makeMockContainer({ deleted: true })],
    ["noPermission", () => makeMockContainer({ permittedActions: ["READ"] })],
    ["image", () => makeMockContainer({ cType: "IMAGE" })],
    ["workbench", () => makeMockBench({})],
    ["cannotStoreSamples", () => makeMockContainer({ canStoreSamples: false })],
  ])("blocks with %s", (reason, make) => {
    expect(placementBlocker(make(), 1)).toEqual({ reason });
  });

  it("blocks while the container is loading", () => {
    const container = makeMockContainer({});
    container.setLoading(true);
    expect(placementBlocker(container, 1)).toEqual({ reason: "loading" });
  });

  it("blocks a container with fewer free locations than new subsamples, with the free count", () => {
    expect(placementBlocker(gridBox(3), 2)).toEqual({ reason: "notEnoughSpace", free: 1 });
  });

  it("lets a list container through with no slot selection", () => {
    expect(placementBlocker(makeMockContainer({ cType: "LIST" }), 5)).toBeNull();
  });

  it("asks for the remaining grid slots, then lets the grid through once all are chosen", () => {
    const box = gridBox();
    prepareContainer(box, 2);
    expect(placementBlocker(box, 2)).toEqual({ reason: "selectSlots", remaining: 2 });
    box.locations?.[0].toggleSelected(true);
    expect(placementBlocker(box, 2)).toEqual({ reason: "selectSlots", remaining: 1 });
    box.locations?.[3].toggleSelected(true);
    expect(placementBlocker(box, 2)).toBeNull();
  });
});

describe("prepareContainer", () => {
  it("limits a grid's slot selection to empty locations, one per new subsample", () => {
    const box = gridBox();
    prepareContainer(box, 3);
    expect(box.contentSearch.uiConfig).toMatchObject({
      selectionMode: "MULTIPLE",
      selectionLimit: 3,
      onlyAllowSelectingEmptyLocations: true,
    });
  });

  it("allows no slot selection in a list container", () => {
    const list = makeMockContainer({ cType: "LIST" });
    prepareContainer(list, 3);
    expect(list.contentSearch.uiConfig.selectionMode).toBe("NONE");
  });

  it("deselects surplus slots from the end when the count drops", () => {
    const box = gridBox();
    for (const location of box.locations ?? []) location.toggleSelected(true);
    prepareContainer(box, 2);
    expect(box.locations?.map((l) => l.selected)).toEqual([true, true, false, false]);
  });
});

describe("releaseContainer", () => {
  it("restores the content search defaults and clears the slot selection", () => {
    const box = gridBox();
    prepareContainer(box, 2);
    box.locations?.[1].toggleSelected(true);
    releaseContainer(box);
    expect(box.contentSearch.uiConfig).toMatchObject({
      selectionMode: "MULTIPLE",
      selectionLimit: Infinity,
      onlyAllowSelectingEmptyLocations: false,
    });
    expect(box.selectedLocations).toEqual([]);
  });
});
