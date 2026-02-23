import { beforeEach, describe, expect, it, vi } from "vitest";
import { getStoichiometry } from "@/modules/stoichiometry/queries";
import type { StoichiometryResponse } from "@/modules/stoichiometry/schema";

const API_BASE_URL = "/api/v1";

const mockStoichiometryResponse: StoichiometryResponse = {
  id: 3,
  revision: 1,
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
  ],
};

beforeEach(() => {
  // TODO: RSDEV-996 Replace with msw once we migrate to Vitest
  fetchMock.resetMocks();
  vi.clearAllMocks();
});

describe("getStoichiometry", () => {
  const token = "test-token";

  it("fetches stoichiometry by id and revision", async () => {
    fetchMock.mockResponseOnce(JSON.stringify(mockStoichiometryResponse));

    const result = await getStoichiometry({
      stoichiometryId: 3,
      revision: 1,
      token,
    });

    expect(result).toEqual(mockStoichiometryResponse);
    expect(fetchMock).toHaveBeenCalledWith(
      `${API_BASE_URL}/stoichiometry?stoichiometryId=3&revision=1`,
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({
          Authorization: `Bearer ${token}`,
          "X-Requested-With": "XMLHttpRequest",
        }) as Record<string, string>,
      }),
    );
  });

  it("omits revision when not provided", async () => {
    fetchMock.mockResponseOnce(JSON.stringify(mockStoichiometryResponse));

    await getStoichiometry({
      stoichiometryId: 3,
      token,
    });

    expect(fetchMock).toHaveBeenCalledWith(
      `${API_BASE_URL}/stoichiometry?stoichiometryId=3`,
      expect.any(Object),
    );
  });

  it("throws message from API when present", async () => {
    fetchMock.mockResponseOnce(JSON.stringify({ message: "Link not found" }), {
      status: 404,
      statusText: "Not Found",
    });

    await expect(
      getStoichiometry({ stoichiometryId: 3, token }),
    ).rejects.toThrow("Link not found");
  });

  it("throws fallback message when API error shape is unknown", async () => {
    fetchMock.mockResponseOnce(JSON.stringify({ nope: true }), {
      status: 500,
      statusText: "Internal Server Error",
    });

    await expect(
      getStoichiometry({ stoichiometryId: 3, token }),
    ).rejects.toThrow("Failed to fetch stoichiometry: Internal Server Error");
  });
});
