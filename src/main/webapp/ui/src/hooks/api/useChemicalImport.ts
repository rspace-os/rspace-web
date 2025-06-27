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
  async function search({
    searchType,
    searchTerm,
  }: {
    searchType: "NAME" | "SMILES";
    searchTerm: string;
  }): Promise<ReadonlyArray<ChemicalCompound>> {
    console.debug("search");
    return Promise.resolve([
      {
        name: "2-acetoxybenzoic acid",
        pngImage:
          "https://pubchem.ncbi.nlm.nih.gov/image/imgsrv.fcgi?cid=2244&t=l",
        smiles: "CC(=O)OC1=CC=CC=C1C(=O)O",
        formula: "C9H8O4",
        pubchemId: "2244",
        pubchemUrl: "https://pubchem.ncbi.nlm.nih.gov/compound/2244",
      },
    ]);
  }

  return { search };
}
