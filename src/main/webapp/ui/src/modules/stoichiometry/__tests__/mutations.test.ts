import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  calculateStoichiometry,
  deleteStoichiometry,
  getMoleculeInfo,
  updateStoichiometry,
} from "@/modules/stoichiometry/mutations";
import type {
  MoleculeInfo,
  StoichiometryRequest,
  StoichiometryResponse,
} from "@/modules/stoichiometry/schema";

const API_BASE_URL = "/api/v1";

const mockStoichiometryResponse: StoichiometryResponse = {
  id: 3,
  revision: 2,
  parentReaction: {
    id: 32769,
    parentId: 226,
    ecatChemFileId: null,
    dataImage: "mock-image-data",
    chemElements: "mock-chem-elements",
    smilesString: "C1=CC=CC=C1.C1C=CC=C1>>C1CCCCC1",
    chemId: null,
    reactionId: null,
    rgroupId: null,
    metadata: "{}",
    chemElementsFormat: "KET",
    creationDate: 1753964538000,
    imageFileProperty: {},
  },
  molecules: [
    {
      id: 4,
      rsChemElement: {
        id: 32770,
        parentId: null,
        ecatChemFileId: null,
        dataImage: null,
        chemElements: "C1=CC=CC=C1",
        smilesString: null,
        chemId: null,
        reactionId: null,
        rgroupId: null,
        metadata: null,
        chemElementsFormat: "MOL",
        creationDate: 1753964548124,
        imageFileProperty: null,
      },
      inventoryLink: null,
      role: "REACTANT",
      formula: "C6 H6",
      name: "Benzene",
      smiles: "C1=CC=CC=C1",
      coefficient: 1,
      molecularWeight: 78.11,
      mass: null,
      actualAmount: null,
      actualYield: null,
      limitingReagent: false,
      notes: null,
    },
    {
      id: 5,
      rsChemElement: null,
      inventoryLink: {
        id: 501,
        inventoryItemGlobalId: "SA123",
        stockDeducted: false,
      },
      role: "AGENT",
      formula: null,
      name: "Acetate",
      smiles: "CC(=O)O",
      coefficient: 1,
      molecularWeight: null,
      mass: null,
      actualAmount: null,
      actualYield: null,
      limitingReagent: null,
      notes: null,
    },
  ],
};

const mockStoichiometryRequest: StoichiometryRequest = {
  id: 3,
  molecules: [
    {
      id: 4,
      coefficient: 2,
      role: "REACTANT",
      inventoryLink: {
        inventoryItemGlobalId: "SA123",
      },
    },
    {
      role: "AGENT",
      smiles: "CC(=O)O",
      name: "Acetate",
    },
  ],
};

const mockMoleculeInfo: MoleculeInfo = {
  molecularWeight: 60.052,
  formula: "C2H4O2",
};

beforeEach(() => {
  // TODO: RSDEV-996 Replace with msw once we migrate to Vitest
  fetchMock.resetMocks();
  vi.clearAllMocks();
});

describe("calculateStoichiometry", () => {
  const token = "test-token";

  it("calculates stoichiometry and sends query params", async () => {
    fetchMock.mockResponseOnce(JSON.stringify(mockStoichiometryResponse));

    const result = await calculateStoichiometry(
      {
        recordId: 123,
        chemId: 456,
      },
      token,
    );

    expect(result).toEqual(mockStoichiometryResponse);
    expect(fetchMock).toHaveBeenCalledWith(
      `${API_BASE_URL}/stoichiometry?recordId=123&chemId=456`,
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({
          Authorization: `Bearer ${token}`,
          "X-Requested-With": "XMLHttpRequest",
        }) as Record<string, string>,
      }),
    );
  });

  it("throws API message when calculation request fails", async () => {
    fetchMock.mockResponseOnce(
      JSON.stringify({ message: "Unable to calculate stoichiometry" }),
      {
        status: 400,
        statusText: "Bad Request",
      },
    );

    await expect(
      calculateStoichiometry(
        {
          recordId: 123,
          chemId: 456,
        },
        token,
      ),
    ).rejects.toThrow("Unable to calculate stoichiometry");
  });

  it("bubbles up network failures during calculation", async () => {
    fetchMock.mockRejectOnce(new Error("Network down"));

    await expect(
      calculateStoichiometry(
        {
          recordId: 123,
          chemId: 456,
        },
        token,
      ),
    ).rejects.toThrow("Network down");
  });
});

describe("updateStoichiometry", () => {
  const token = "test-token";

  it("updates stoichiometry with JSON body and query param", async () => {
    fetchMock.mockResponseOnce(JSON.stringify(mockStoichiometryResponse));

    const result = await updateStoichiometry(
      {
        stoichiometryId: 3,
        stoichiometryData: mockStoichiometryRequest,
      },
      token,
    );

    expect(result).toEqual(mockStoichiometryResponse);
    const callArgs = fetchMock.mock.calls[0];
    expect(callArgs[0]).toBe(
      `${API_BASE_URL}/stoichiometry?stoichiometryId=3`,
    );
    expect(callArgs[1]).toEqual(
      expect.objectContaining({
        method: "PUT",
        headers: expect.objectContaining({
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        }) as Record<string, string>,
      }),
    );
    expect(JSON.parse(callArgs[1]?.body as string)).toEqual(
      mockStoichiometryRequest,
    );
  });

  it("parses inventory links using stockDeducted shape", async () => {
    fetchMock.mockResponseOnce(JSON.stringify(mockStoichiometryResponse));

    const result = await updateStoichiometry(
      {
        stoichiometryId: 3,
        stoichiometryData: mockStoichiometryRequest,
      },
      token,
    );

    expect(result.molecules[1]?.inventoryLink).toEqual({
      id: 501,
      inventoryItemGlobalId: "SA123",
      stockDeducted: false,
    });
  });

  it("throws API message when update request fails", async () => {
    fetchMock.mockResponseOnce(
      JSON.stringify({ message: "Could not update stoichiometry" }),
      {
        status: 409,
        statusText: "Conflict",
      },
    );

    await expect(
      updateStoichiometry(
        {
          stoichiometryId: 3,
          stoichiometryData: mockStoichiometryRequest,
        },
        token,
      ),
    ).rejects.toThrow("Could not update stoichiometry");
  });

  it("bubbles up network failures during updates", async () => {
    fetchMock.mockRejectOnce(new Error("Network down"));

    await expect(
      updateStoichiometry(
        {
          stoichiometryId: 3,
          stoichiometryData: mockStoichiometryRequest,
        },
        token,
      ),
    ).rejects.toThrow("Network down");
  });
});

describe("deleteStoichiometry", () => {
  const token = "test-token";

  it("handles boolean response", async () => {
    fetchMock.mockResponseOnce(JSON.stringify(true), { status: 200 });

    const result = await deleteStoichiometry(
      {
        stoichiometryId: 3,
      },
      token,
    );

    expect(result).toBe(true);
    expect(fetchMock).toHaveBeenCalledWith(
      `${API_BASE_URL}/stoichiometry?stoichiometryId=3`,
      expect.objectContaining({
        method: "DELETE",
      }),
    );
  });

  it("handles success object response", async () => {
    fetchMock.mockResponseOnce(JSON.stringify({ success: true }), {
      status: 200,
    });

    const result = await deleteStoichiometry(
      {
        stoichiometryId: 3,
      },
      token,
    );

    expect(result).toBe(true);
  });

  it("throws API message when request fails", async () => {
    fetchMock.mockResponseOnce(
      JSON.stringify({ message: "No stoichiometry found with id 3" }),
      {
        status: 404,
        statusText: "Not Found",
      },
    );

    await expect(
      deleteStoichiometry({ stoichiometryId: 3 }, token),
    ).rejects.toThrow("No stoichiometry found with id 3");
  });

  it("falls back to status text when delete error body cannot be parsed", async () => {
    fetchMock.mockResponseOnce("", {
      status: 500,
      statusText: "Internal Server Error",
    });

    await expect(
      deleteStoichiometry({ stoichiometryId: 3 }, token),
    ).rejects.toThrow("Failed to delete stoichiometry: Internal Server Error");
  });

  it("bubbles up network failures during deletion", async () => {
    fetchMock.mockRejectOnce(new Error("Network down"));

    await expect(
      deleteStoichiometry({ stoichiometryId: 3 }, token),
    ).rejects.toThrow("Network down");
  });
});

describe("getMoleculeInfo", () => {
  const token = "test-token";

  it("posts SMILES payload and parses molecule info", async () => {
    fetchMock.mockResponseOnce(JSON.stringify(mockMoleculeInfo));

    const result = await getMoleculeInfo(
      {
        smiles: "CC(=O)O",
      },
      token,
    );

    expect(result).toEqual(mockMoleculeInfo);
    expect(fetchMock).toHaveBeenCalledWith(
      `${API_BASE_URL}/stoichiometry/molecule/info`,
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ chemical: "CC(=O)O" }),
      }),
    );
  });

  it("throws API message when molecule info request fails", async () => {
    fetchMock.mockResponseOnce(
      JSON.stringify({ message: "Unsupported SMILES string" }),
      {
        status: 422,
        statusText: "Unprocessable Entity",
      },
    );

    await expect(
      getMoleculeInfo(
        {
          smiles: "CC(=O)O",
        },
        token,
      ),
    ).rejects.toThrow("Unsupported SMILES string");
  });

  it("bubbles up network failures during molecule info requests", async () => {
    fetchMock.mockRejectOnce(new Error("Network down"));

    await expect(
      getMoleculeInfo(
        {
          smiles: "CC(=O)O",
        },
        token,
      ),
    ).rejects.toThrow("Network down");
  });
});
