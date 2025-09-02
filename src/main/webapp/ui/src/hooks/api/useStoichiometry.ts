import React from "react";
import axios from "@/common/axios";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import { getErrorMessage } from "@/util/error";
import useOauthToken from "../auth/useOauthToken";

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
  name: string | null;
  smiles: string;
  coefficient: number;
  molecularWeight: number;
  mass: number | null;
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
  revision: number;
  parentReaction: ParentReaction;
  molecules: ReadonlyArray<StoichiometryMolecule>;
};

type NewReagant = {
  role: "AGENT";
  smiles: string;
  name: string;
};

export type StoichiometryRequest = {
  id: number;
  molecules: ReadonlyArray<StoichiometryMolecule | NewReagant>;
};

export type MoleculeInfo = {
  molecularWeight: number;
  formula: string;
  // Add other properties as they become available from the API
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
    stoichiometryId,
    revision,
  }: {
    stoichiometryId: number;
    revision?: number;
  }) => Promise<StoichiometryResponse>;

  /**
   * Updates stoichiometry information for a chemical compound.
   * This performs a PUT request to update the data.
   */
  updateStoichiometry: ({
    stoichiometryId,
    stoichiometryData,
  }: {
    stoichiometryId: number;
    stoichiometryData: StoichiometryRequest;
  }) => Promise<StoichiometryResponse>;

  /**
   * Deletes existing stoichiometry information for a chemical compound.
   * This performs a DELETE request to remove the data.
   */
  deleteStoichiometry: ({
    stoichiometryId,
  }: {
    stoichiometryId: number;
  }) => Promise<void>;

  /**
   * Gets additional molecule information for a SMILES string.
   * This performs a POST request to retrieve molecular weight, formula, etc.
   */
  getMoleculeInfo: ({ smiles }: { smiles: string }) => Promise<MoleculeInfo>;
} {
  const { addAlert } = React.useContext(AlertContext);
  const { getToken } = useOauthToken();

  async function calculateStoichiometry({
    chemId,
  }: {
    chemId: number;
  }): Promise<StoichiometryResponse> {
    try {
      const formData = new FormData();
      formData.append("chemId", chemId.toString());
      const { data } = await axios.post<StoichiometryResponse>(
        "/api/v1/stoichiometry",
        formData,
        {
          headers: {
            Authorization: `Bearer ${await getToken()}`,
          },
        },
      );
      return data;
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
    stoichiometryId,
    revision,
  }: {
    stoichiometryId: number;
    revision?: number;
  }): Promise<StoichiometryResponse> {
    try {
      const { data } = await axios.get<StoichiometryResponse>(
        "/api/v1/stoichiometry",
        {
          params: { stoichiometryId, revision },
          headers: {
            Authorization: `Bearer ${await getToken()}`,
          },
        },
      );
      return data;
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

  async function updateStoichiometry({
    stoichiometryId,
    stoichiometryData,
  }: {
    stoichiometryId: number;
    stoichiometryData: StoichiometryRequest;
  }): Promise<StoichiometryResponse> {
    try {
      const { data } = await axios.put<StoichiometryResponse>(
        "/api/v1/stoichiometry",
        stoichiometryData,
        {
          params: { stoichiometryId },
          headers: {
            Authorization: `Bearer ${await getToken()}`,
          },
        },
      );
      addAlert(
        mkAlert({
          variant: "success",
          message: "Successfully updated stoichiometry table",
        }),
      );
      return data;
    } catch (e) {
      addAlert(
        mkAlert({
          variant: "error",
          title: "Error updating stoichiometry data",
          message: getErrorMessage(e, "An unknown error occurred."),
        }),
      );
      throw new Error("Could not update stoichiometry data", { cause: e });
    }
  }

  async function deleteStoichiometry({
    stoichiometryId,
  }: {
    stoichiometryId: number;
  }): Promise<void> {
    try {
      await axios.delete(`/api/v1/stoichiometry`, {
        params: { stoichiometryId },
        headers: {
          Authorization: `Bearer ${await getToken()}`,
        },
      });
      addAlert(
        mkAlert({
          variant: "success",
          title: "Stoichiometry data deleted",
          message: "The stoichiometry data was successfully deleted.",
        }),
      );
    } catch (e) {
      addAlert(
        mkAlert({
          variant: "error",
          title: "Error deleting stoichiometry data",
          message: getErrorMessage(e, "An unknown error occurred."),
        }),
      );
      throw new Error("Could not delete stoichiometry data", { cause: e });
    }
  }

  async function getMoleculeInfo({
    smiles,
  }: {
    smiles: string;
  }): Promise<MoleculeInfo> {
    try {
      const { data } = await axios.post<MoleculeInfo>(
        "/api/v1/stoichiometry/molecule/info",
        { chemical: smiles },
        {
          headers: {
            Authorization: `Bearer ${await getToken()}`,
          },
        },
      );
      return data;
    } catch (e) {
      addAlert(
        mkAlert({
          variant: "error",
          title: "Error retrieving molecule information",
          message: getErrorMessage(e, "An unknown error occurred."),
        }),
      );
      throw new Error("Could not retrieve molecule information", { cause: e });
    }
  }

  return {
    calculateStoichiometry,
    getStoichiometry,
    updateStoichiometry,
    deleteStoichiometry,
    getMoleculeInfo,
  };
}
