// See DevDocs/adr/0012-operation-wizard-placement-step.md for this module's design.
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
  const { uiConfig } = container.contentSearch;
  uiConfig.selectionMode = "MULTIPLE";
  uiConfig.selectionLimit = Infinity;
  uiConfig.onlyAllowSelectingEmptyLocations = false;
  for (const location of container.selectedLocations ?? []) location.toggleSelected(false);
}
