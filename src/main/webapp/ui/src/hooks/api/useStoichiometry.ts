import React from "react";
import axios from "@/common/axios";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import { getErrorMessage } from "@/util/error";

export type RsChemElement = {
  id: number;
  parentId: number | null;
  ecatChemFileId: string | null;
  dataImage: string | null;
  chemElements: string;
  smilesString: string | null;
  chemId: string | null;
  reactionId: string | null;
  rgroupId: string | null;
  metadata: string | null;
  chemElementsFormat: string;
  creationDate: number;
  imageFileProperty: any | null;
};

export type StoichiometryMolecule = {
  id: number;
  rsChemElement: RsChemElement;
  role: string;
  formula: string;
  name: string;
  smiles: string;
  coefficient: number;
  molecularWeight: number;
  mass: number | null;
  moles: number | null;
  expectedAmount: number | null;
  actualAmount: number | null;
  actualYield: number | null;
  limitingReagent: boolean;
  notes: string | null;
};

export type ParentReaction = {
  id: number;
  parentId: number;
  ecatChemFileId: string | null;
  dataImage: string;
  chemElements: string;
  smilesString: string;
  chemId: string | null;
  reactionId: string | null;
  rgroupId: string | null;
  metadata: string;
  chemElementsFormat: string;
  creationDate: number;
  imageFileProperty: any;
};

export type StoichiometryResponse = {
  id: number;
  parentReaction: ParentReaction;
  molecules: ReadonlyArray<StoichiometryMolecule>;
};

/**
 * This custom hook provides functionality for stoichiometry calculations
 * and data retrieval using the `/chemical/stoichiometry` endpoints.
 */
export default function useStoichiometry(): {
  /**
   * Calculates stoichiometry information for a chemical compound.
   * This performs a POST request to generate/calculate the data.
   */
  calculateStoichiometry: ({
    chemId,
  }: {
    chemId: number;
  }) => Promise<StoichiometryResponse>;

  /**
   * Gets existing stoichiometry information for a chemical compound.
   * This performs a GET request to retrieve previously calculated data.
   */
  getStoichiometry: ({
    chemId,
  }: {
    chemId: number;
  }) => Promise<StoichiometryResponse>;
} {
  const { addAlert } = React.useContext(AlertContext);

  async function calculateStoichiometry({
    chemId,
  }: {
    chemId: number;
  }): Promise<StoichiometryResponse> {
    try {
      const formData = new FormData();
      formData.append("chemId", chemId.toString());
      const { data } = await axios.post<{ data: StoichiometryResponse }>(
        "/chemical/stoichiometry",
        formData,
      );
      return data.data;
    } catch (e) {
      addAlert(
        mkAlert({
          variant: "error",
          title: "Error calculating stoichiometry data",
          message: getErrorMessage(e, "An unknown error occurred."),
        }),
      );
      throw new Error("Could not calculate stoichiometry data", { cause: e });
    }
  }

  async function getStoichiometry({
    chemId,
  }: {
    chemId: number;
  }): Promise<StoichiometryResponse> {
    try {
      const { data } = await axios.get<{ data: StoichiometryResponse }>(
        "/chemical/stoichiometry",
        {
          params: { chemId },
        }
      );
      return data.data;
    } catch (e) {
      addAlert(
        mkAlert({
          variant: "error",
          title: "Error retrieving stoichiometry data",
          message: getErrorMessage(e, "An unknown error occurred."),
        }),
      );
      throw new Error("Could not retrieve stoichiometry data", { cause: e });
    }
  }

  return { calculateStoichiometry, getStoichiometry };
}
