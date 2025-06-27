import axios from "@/common/axios";
import useOauthToken from "@/common/useOauthToken";

export type ChemicalCompound = {
  name: string;
  pngImage: string;
  smiles: string;
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
      return axios
        .post<ReadonlyArray<ChemicalCompound>>(
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
        )
        .then((response) => response.data);
    } catch (e) {
      throw new Error("Could not search for chemical compounds", {
        cause: e,
      });
    }
  }

  return { search };
}
