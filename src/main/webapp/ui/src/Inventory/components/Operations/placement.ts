// See DevDocs/adr/0012-operation-wizard-placement-step.md for this module's design.
import { runInAction } from "mobx";
import type { BulkEndpointRecordSerialisation } from "@/common/InvApiService";
import type ContainerModel from "@/stores/models/ContainerModel";

/** Where the new subsamples go after the operation. `container` is null until one is picked. */
export type PlacementSelection = { mode: "workbench" } | { mode: "container"; container: ContainerModel | null };

export const WORKBENCH: PlacementSelection = { mode: "workbench" };

/**
 * The step configures `container.contentSearch`, which is the same observable instance the
 * container's own page renders, so its defaults are restored and its slot selection cleared once
 * the wizard is done with it.
 */
export function releaseContainer(container: ContainerModel): void {
  runInAction(() => {
    const { uiConfig } = container.contentSearch;
    uiConfig.selectionMode = "MULTIPLE";
    uiConfig.selectionLimit = Infinity;
    uiConfig.onlyAllowSelectingEmptyLocations = false;
  });
  for (const location of container.selectedLocations ?? []) location.toggleSelected(false);
}

/**
 * Lets the user pick one empty grid location per new subsample, and none in a list container.
 * Re-run when the count changes: surplus selected locations are dropped from the end.
 */
export function prepareContainer(container: ContainerModel, count: number): void {
  runInAction(() => {
    const { uiConfig } = container.contentSearch;
    uiConfig.selectionMode = container.cType === "GRID" ? "MULTIPLE" : "NONE";
    uiConfig.selectionLimit = count;
    uiConfig.onlyAllowSelectingEmptyLocations = true;
  });
  for (const location of (container.selectedLocations ?? []).slice(count)) location.toggleSelected(false);
}

export type PlacementBlocker =
  | { reason: "loading" | "deleted" | "noPermission" | "image" | "workbench" | "cannotStoreSamples" }
  | { reason: "notEnoughSpace"; free: number }
  | { reason: "selectSlots"; remaining: number };

/**
 * Why `count` new subsamples cannot be placed in `container` yet, or null when they can. Reads the
 * container's own fields only: ContainerModel's canStoreRecords, hasEnoughSpace, canStoreRecordTypes
 * and movingIntoItself read the global moveStore's selection, which is empty here, so they would
 * report that anything fits.
 */
export function placementBlocker(container: ContainerModel, count: number): PlacementBlocker | null {
  if (container.loading) return { reason: "loading" };
  if (container.deleted) return { reason: "deleted" };
  if (!container.canEdit) return { reason: "noPermission" };
  if (container.cType === "IMAGE") return { reason: "image" };
  if (container.cType === "WORKBENCH") return { reason: "workbench" };
  if (!container.canStoreSamples) return { reason: "cannotStoreSamples" };
  const free = container.availableLocations;
  if (free.isAccessible && free.value < count) return { reason: "notEnoughSpace", free: free.value };
  if (container.cType === "GRID") {
    const selected = container.selectedLocations;
    if (selected === null) return { reason: "loading" };
    if (selected.length < count) return { reason: "selectSlots", remaining: count - selected.length };
  }
  return null;
}

export type CreatedSubSample = { id: number; globalId: string };

/** One record of a bulk MOVE request, in the shape MoveStore sends. */
export type PlacementRecord = BulkEndpointRecordSerialisation & {
  globalId: string;
  parentContainers: ReadonlyArray<object>;
  parentLocation?: object;
};

/**
 * Pairs each new subsample, in the order the server returned them, with the container and, for a
 * grid, the next chosen location in grid order.
 */
export function buildPlacementRecords(
  subSamples: ReadonlyArray<CreatedSubSample>,
  container: ContainerModel,
): Array<PlacementRecord> {
  const parentContainers = [container.paramsForBackend];
  const base = (s: CreatedSubSample) => ({
    id: s.id,
    type: "SUBSAMPLE" as const,
    globalId: s.globalId,
    parentContainers,
  });
  if (container.cType !== "GRID") return subSamples.map(base);
  const locations = container.selectedLocations ?? [];
  if (locations.length !== subSamples.length)
    throw new Error(`${subSamples.length} new subsamples but ${locations.length} chosen locations`);
  return subSamples.map((s, i) => ({ ...base(s), parentLocation: locations[i].paramsForBackend }));
}
