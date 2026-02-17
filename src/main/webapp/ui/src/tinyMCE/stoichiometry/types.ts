import { StoichiometryMolecule } from "@/hooks/api/useStoichiometry";

export type EditableMolecule = StoichiometryMolecule & {
  moles: number | null;
  actualMoles: number | null;
};

export interface StoichiometryTableRef {
  save: () => Promise<number>;
  delete: () => Promise<void>;
}
