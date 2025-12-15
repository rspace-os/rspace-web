import { type Sample } from "./Sample";
import { type Field } from "./Field";

export interface Template extends Sample {
  version: number;
  defaultUnitId: number;
  getLatest(): void;
  historicalVersion: boolean;
  latest: Template | null;
  moveField(field: Field, newIndex: number): void;
}
