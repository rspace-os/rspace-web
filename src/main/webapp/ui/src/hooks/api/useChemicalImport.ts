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

  return { search };
}
