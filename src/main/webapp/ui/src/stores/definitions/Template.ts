import type { Field } from "./Field";
import type { Sample } from "./Sample";

export interface Template extends Sample {
  version: number;
  /**
   * How many of the current user's samples were created from an older version
   * of this template and could therefore be updated to its latest version.
   */
  samplesToUpdateCount: number;
  defaultUnitId: number;
  getLatest(): void;
  historicalVersion: boolean;
  latest: Template | null;
  moveField(field: Field, newIndex: number): void;
}
