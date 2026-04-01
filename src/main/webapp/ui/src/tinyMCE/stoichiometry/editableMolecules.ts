import { produce } from "immer";
import type { StoichiometryResponse } from "@/modules/stoichiometry/schema";
import type { EditableMolecule } from "@/tinyMCE/stoichiometry/types";

export function toEditableMolecules(
  stoichiometry: StoichiometryResponse,
): ReadonlyArray<EditableMolecule> {
  const molecules = stoichiometry.molecules.map((molecule) => ({
    ...molecule,
    /*
     * We add these properties to facilitate editing the computed values;
     * they will only ever not be null during the onChange event handler.
     * The displayed value for these fields is always computed on the fly.
     */
    moles: null,
    actualMoles: null,
  }));
  const hasLimitingReagent = molecules.some(
    (m) => m.limitingReagent && m.role.toLowerCase() === "reactant",
  );

  if (hasLimitingReagent) {
    return molecules;
  }

  const firstReactant = molecules.find(
    (m) => m.role.toLowerCase() === "reactant",
  );
  if (!firstReactant) {
    return molecules;
  }

  return produce(molecules, (draftMolecules) => {
    const firstReactantDraft = draftMolecules.find(
      (m) => m.id === firstReactant.id,
    );
    if (firstReactantDraft) {
      firstReactantDraft.limitingReagent = true;
    }
  });
}

