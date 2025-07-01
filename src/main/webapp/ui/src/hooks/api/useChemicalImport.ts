import React from "react";
import axios from "@/common/axios";
import useOauthToken from "@/common/useOauthToken";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import { getErrorMessage } from "@/util/error";

export type ChemicalCompound = {
  name: string;
  pngImage: string;
  smiles: string;
  cas: string | null;
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
  search: ({
    searchType,
    searchTerm,
  }: {
    searchType: "NAME" | "SMILES";
    searchTerm: string;
  }) => Promise<ReadonlyArray<ChemicalCompound>>;
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
  formatAsHtml: ({
    id,
    fieldId,
  }: {
    id: RspaceCompoundId;
    fieldId: string;
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
        "/api/v1/chemical/search",
        {
          searchType,
          searchTerm,
        },
        {
          headers: {
            Authorization: `Bearer ${await getToken()}`,
          },
        }
      );
      return data;
    } catch (e) {
      addAlert(
        mkAlert({
          variant: "error",
          title: "Error searching for chemical compounds",
          message: getErrorMessage(e, "An unknown error occurred."),
        })
      );
      throw new Error("Could not search for chemical compounds", {
        cause: e,
      });
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
        })
      );
      throw new Error("Could not save chemical compounds", { cause: e });
    }
  }

  async function formatAsHtml({
    id,
    fieldId,
  }: {
    id: string;
    fieldId: string;
  }): Promise<string> {
    const fullWidth = 500;
    const fullHeight = 500;
    const previewWidth = 250;
    const previewHeight = 250;
    const milliseconds = new Date().getTime();
    const json = {
      id,
      ecatChemFileId: null,
      sourceParentId: fieldId,
      width: previewWidth,
      height: previewHeight,
      fullwidth: fullWidth,
      fullheight: fullHeight,
      fieldId,
      tstamp: milliseconds,
    };
    const htmlTemplate = await axios.get(
      "/fieldTemplates/ajax/chemElementLink"
    );
    // @ts-expect-error Globally available on the document editor page
    return Mustache.render(htmlTemplate.data, json) as string;
  }

  return { search, save, formatAsHtml };
}
