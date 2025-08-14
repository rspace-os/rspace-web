import React from "react";
import axios from "@/common/axios";
import useOauthToken from "../auth/useOauthToken";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import { getErrorMessage } from "@/util/error";

export type ChemicalCompound = {
  name: string;
  pngImage: string;
  smiles: string;
  cas: string; // may be empty if not available
  formula: string;
  pubchemId: string;
  pubchemUrl: string;
};

export type RspaceCompoundId = string;

/**
 * This custom hook provides functionality to search for chemical compounds,
 * using the `/chemical/*` endpoints.
 */
export default function useChemicalImport(): {
  /**
   * Searches PubChem for chemical compounds by name or SMILES.
   */
  search: ({
    searchType,
    searchTerm,
  }: {
    searchType: "NAME" | "SMILES";
    searchTerm: string;
  }) => Promise<ReadonlyArray<ChemicalCompound>>;

  /**
   * Saves a chemical compound into the database, but doesn't create a new
   * Gallery file for it.
   */
  save: ({
    chemElements,
    chemElementsFormat,
    fieldId,
    metadata,
  }: {
    chemElements: string;
    chemElementsFormat: "ket" | "smi";
    fieldId: string;
    metadata?: Record<string, string>;
  }) => Promise<{ id: RspaceCompoundId }>;

  /**
   * Saves a chemical compound, based on a SMILES string, into the database
   * and creates a new Gallery file for it.
   */
  saveSmilesString: ({
    name,
    smiles,
    fieldId,
    metadata,
  }: {
    name: string;
    smiles: string;
    fieldId: string;
    metadata?: Record<string, string>;
  }) => Promise<{ id: RspaceCompoundId; chemFileId: string }>;

  /**
   * Formats a chemical compound as HTML, suitable for rendering in the document
   * editor.
   * @arg id      - The Rspace ID of the saved chemical compound. To get this
   *                id, use the `save` or `saveSmilesString` methods.
   * @arg fieldId - The ID of the field in which the outputted HTML will be
   *                inserted.
   */
  formatAsHtml: ({
    id,
    fieldId,
    chemFileId,
  }: {
    id: RspaceCompoundId;
    fieldId: string;
    chemFileId?: string | null;
  }) => Promise<string>;
} {
  const { getToken } = useOauthToken();
  const { addAlert } = React.useContext(AlertContext);

  async function search({
    searchType,
    searchTerm,
  }: {
    searchType: "NAME" | "SMILES";
    searchTerm: string;
  }): Promise<ReadonlyArray<ChemicalCompound>> {
    try {
      /*
       * We use a POST because encoding SMILES in a URL can be problematic
       */
      const { data } = await axios.post<ReadonlyArray<ChemicalCompound>>(
        "/api/v1/pubchem/search",
        {
          searchType,
          searchTerm,
        },
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
          title: "Error searching for chemical compounds",
          message: getErrorMessage(e, "An unknown error occurred."),
        }),
      );
      throw new Error("Could not search for chemical compounds", {
        cause: e,
      });
    }
  }

  async function saveSmilesString({
    name,
    smiles,
    fieldId,
    metadata = {},
  }: {
    name: string;
    smiles: string;
    fieldId: string;
    metadata?: Record<string, string>;
  }): Promise<{ id: RspaceCompoundId; chemFileId: string }> {
    const file = new File([smiles], `${name}.smiles`, { type: "text/plain" });
    const formData = new FormData();
    formData.append("xfile", file);
    try {
      const {
        data: {
          data: { id: ecatChemFileId },
        },
      } = await axios.post("/gallery/ajax/uploadFile", formData, {
        headers: {
          "Content-Type": "multipart/form-data",
        },
      });
      const {
        data: {
          data: { rsChemElementId },
        },
      } = await axios.post("/chemical/ajax/createChemElement", {
        ecatChemFileId,
        fieldId,
        metadata: JSON.stringify(metadata),
      });
      return { id: rsChemElementId, chemFileId: ecatChemFileId };
    } catch (e) {
      addAlert(
        mkAlert({
          variant: "error",
          title: "Error saving chemical compounds",
          message: getErrorMessage(e, "An unknown error occurred."),
        }),
      );
      throw new Error("Could not save chemical compounds", { cause: e });
    }
  }

  async function save({
    chemElements,
    chemElementsFormat,
    fieldId,
    metadata = {},
  }: {
    chemElements: string;
    chemElementsFormat: "ket" | "smi";
    fieldId: string;
    metadata?: Record<string, string>;
  }): Promise<{ id: RspaceCompoundId }> {
    try {
      const { data } = await axios.post("/chemical/save", {
        chemElements,
        chemElementsFormat,
        chemId: "",
        imageBase64: "",
        fieldId,
        metadata: JSON.stringify(metadata),
      });
      return { id: data.id };
    } catch (e) {
      addAlert(
        mkAlert({
          variant: "error",
          title: "Error saving chemical compounds",
          message: getErrorMessage(e, "An unknown error occurred."),
        }),
      );
      throw new Error("Could not save chemical compounds", { cause: e });
    }
  }

  async function formatAsHtml({
    id,
    fieldId,
    chemFileId = null,
  }: {
    id: string;
    fieldId: string;
    chemFileId?: string | null;
  }): Promise<string> {
    const fullWidth = 500;
    const fullHeight = 500;
    const previewWidth = 250;
    const previewHeight = 250;
    const milliseconds = new Date().getTime();
    const json = {
      id,
      ecatChemFileId: chemFileId,
      sourceParentId: fieldId,
      width: previewWidth,
      height: previewHeight,
      fullwidth: fullWidth,
      fullheight: fullHeight,
      fieldId,
      tstamp: milliseconds,
    };
    const htmlTemplate = await axios.get(
      "/fieldTemplates/ajax/chemElementLink",
    );
    // @ts-expect-error Globally available on the document editor page
    return Mustache.render(htmlTemplate.data, json) as string;
  }

  return { search, save, saveSmilesString, formatAsHtml };
}
