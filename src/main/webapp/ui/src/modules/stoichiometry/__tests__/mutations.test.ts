import { HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { captureRequests } from "@/__tests__/mswRequestCapture";
import {
  calculateStoichiometry,
  deductStock,
  deleteStoichiometry,
  getMoleculeInfo,
  updateStoichiometry,
} from "@/modules/stoichiometry/mutations";
import type { MoleculeInfo, StoichiometryRequest, StoichiometryResponse } from "@/modules/stoichiometry/schema";

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

function mockPost(path: string, response: () => Response): Request[] {
  return captureRequests("post", path, response);
}

function mockPut(path: string, response: () => Response): Request[] {
  return captureRequests("put", path, response);
}

function mockDelete(path: string, response: () => Response): Request[] {
  return captureRequests("delete", path, response);
}

describe("calculateStoichiometry", () => {
  const token = "test-token";

  it("calculates stoichiometry and sends query params", async () => {
    const requests = mockPost(`${API_BASE_URL}/stoichiometry`, () => HttpResponse.json(mockStoichiometryResponse));

    const result = await calculateStoichiometry(
      {
        recordId: 123,
        chemId: 456,
      },
      token,
    );

    expect(result).toEqual(mockStoichiometryResponse);
    expect(requests).toHaveLength(1);
    const request = requests[0];
    expect(Object.fromEntries(new URL(request.url).searchParams)).toEqual({ recordId: "123", chemId: "456" });
    expect(request.headers.get("Authorization")).toBe(`Bearer ${token}`);
    expect(request.headers.get("X-Requested-With")).toBe("XMLHttpRequest");
  });

  it("throws API message when calculation request fails", async () => {
    mockPost(`${API_BASE_URL}/stoichiometry`, () =>
      HttpResponse.json({ message: "Unable to calculate stoichiometry" }, { status: 400, statusText: "Bad Request" }),
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
    const requests = mockPost(`${API_BASE_URL}/stoichiometry`, () => HttpResponse.error());

    await expect(
      calculateStoichiometry(
        {
          recordId: 123,
          chemId: 456,
        },
        token,
      ),
    ).rejects.toThrow();
    expect(requests).toHaveLength(1);
  });
});

describe("updateStoichiometry", () => {
  const token = "test-token";

  it("updates stoichiometry with JSON body and query param", async () => {
    const requests = mockPut(`${API_BASE_URL}/stoichiometry`, () => HttpResponse.json(mockStoichiometryResponse));

    const result = await updateStoichiometry(
      {
        stoichiometryId: 3,
        stoichiometryData: mockStoichiometryRequest,
      },
      token,
    );

    expect(result).toEqual(mockStoichiometryResponse);
    expect(requests).toHaveLength(1);
    const request = requests[0];
    expect(Object.fromEntries(new URL(request.url).searchParams)).toEqual({ stoichiometryId: "3" });
    expect(request.headers.get("Content-Type")).toBe("application/json");
    expect(request.headers.get("Authorization")).toBe(`Bearer ${token}`);
    await expect(request.json()).resolves.toEqual(mockStoichiometryRequest);
  });

  it("parses inventory links using stockDeducted shape", async () => {
    mockPut(`${API_BASE_URL}/stoichiometry`, () => HttpResponse.json(mockStoichiometryResponse));

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
    mockPut(`${API_BASE_URL}/stoichiometry`, () =>
      HttpResponse.json({ message: "Could not update stoichiometry" }, { status: 409, statusText: "Conflict" }),
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
    const requests = mockPut(`${API_BASE_URL}/stoichiometry`, () => HttpResponse.error());

    await expect(
      updateStoichiometry(
        {
          stoichiometryId: 3,
          stoichiometryData: mockStoichiometryRequest,
        },
        token,
      ),
    ).rejects.toThrow();
    expect(requests).toHaveLength(1);
  });
});

describe("deleteStoichiometry", () => {
  const token = "test-token";

  it("handles boolean response", async () => {
    const requests = mockDelete(`${API_BASE_URL}/stoichiometry`, () => HttpResponse.json(true, { status: 200 }));

    const result = await deleteStoichiometry(
      {
        stoichiometryId: 3,
      },
      token,
    );

    expect(result).toBe(true);
    expect(requests).toHaveLength(1);
    expect(Object.fromEntries(new URL(requests[0].url).searchParams)).toEqual({ stoichiometryId: "3" });
  });

  it("handles success object response", async () => {
    mockDelete(`${API_BASE_URL}/stoichiometry`, () => HttpResponse.json({ success: true }, { status: 200 }));

    const result = await deleteStoichiometry(
      {
        stoichiometryId: 3,
      },
      token,
    );

    expect(result).toBe(true);
  });

  it("throws API message when request fails", async () => {
    mockDelete(`${API_BASE_URL}/stoichiometry`, () =>
      HttpResponse.json({ message: "No stoichiometry found with id 3" }, { status: 404, statusText: "Not Found" }),
    );

    await expect(deleteStoichiometry({ stoichiometryId: 3 }, token)).rejects.toThrow(
      "No stoichiometry found with id 3",
    );
  });

  it("falls back to status text when delete error body cannot be parsed", async () => {
    mockDelete(
      `${API_BASE_URL}/stoichiometry`,
      () => new HttpResponse(null, { status: 500, statusText: "Internal Server Error" }),
    );

    await expect(deleteStoichiometry({ stoichiometryId: 3 }, token)).rejects.toMatchObject({
      key: "stoichiometry.errors.deleteFailed",
      values: { status: "Internal Server Error" },
    });
  });

  it("bubbles up network failures during deletion", async () => {
    const requests = mockDelete(`${API_BASE_URL}/stoichiometry`, () => HttpResponse.error());

    await expect(deleteStoichiometry({ stoichiometryId: 3 }, token)).rejects.toThrow();
    expect(requests).toHaveLength(1);
  });
});

describe("deductStock", () => {
  const token = "test-token";

  it("posts link ids to the stoichiometry deductStock endpoint", async () => {
    const requests = mockPost(`${API_BASE_URL}/stoichiometry/link/deductStock`, () =>
      HttpResponse.json({
        stoichiometryId: 9,
        revisionNumber: 12,
        results: [
          { linkId: 501, success: true },
          {
            linkId: 502,
            success: false,
            errorMessage: "Insufficient stock",
          },
        ],
      }),
    );

    const result = await deductStock(
      {
        stoichiometryId: 9,
        linkIds: [501, 502],
      },
      token,
    );

    expect(result).toEqual({
      stoichiometryId: 9,
      revisionNumber: 12,
      results: [
        { linkId: 501, success: true },
        {
          linkId: 502,
          success: false,
          errorMessage: "Insufficient stock",
        },
      ],
    });
    expect(requests).toHaveLength(1);
    const request = requests[0];
    expect(request.headers.get("Content-Type")).toBe("application/json");
    expect(request.headers.get("X-Requested-With")).toBe("XMLHttpRequest");
    expect(request.headers.get("Authorization")).toBe(`Bearer ${token}`);
    await expect(request.json()).resolves.toEqual({ stoichiometryId: 9, linkIds: [501, 502] });
  });

  it("throws API message when deductStock fails", async () => {
    mockPost(`${API_BASE_URL}/stoichiometry/link/deductStock`, () =>
      HttpResponse.json({ message: "Unable to deduct stock" }, { status: 500, statusText: "Internal Server Error" }),
    );

    await expect(
      deductStock(
        {
          stoichiometryId: 9,
          linkIds: [501],
        },
        token,
      ),
    ).rejects.toThrow("Unable to deduct stock");
  });

  it("bubbles up network failures during deductStock", async () => {
    const requests = mockPost(`${API_BASE_URL}/stoichiometry/link/deductStock`, () => HttpResponse.error());

    await expect(
      deductStock(
        {
          stoichiometryId: 9,
          linkIds: [501],
        },
        token,
      ),
    ).rejects.toThrow();
    expect(requests).toHaveLength(1);
  });
});

describe("getMoleculeInfo", () => {
  const token = "test-token";

  it("posts SMILES payload and parses molecule info", async () => {
    const requests = mockPost(`${API_BASE_URL}/stoichiometry/molecule/info`, () => HttpResponse.json(mockMoleculeInfo));

    const result = await getMoleculeInfo(
      {
        smiles: "CC(=O)O",
      },
      token,
    );

    expect(result).toEqual(mockMoleculeInfo);
    expect(requests).toHaveLength(1);
    await expect(requests[0].json()).resolves.toEqual({ chemical: "CC(=O)O" });
  });

  it("throws API message when molecule info request fails", async () => {
    mockPost(`${API_BASE_URL}/stoichiometry/molecule/info`, () =>
      HttpResponse.json({ message: "Unsupported SMILES string" }, { status: 422, statusText: "Unprocessable Entity" }),
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
    const requests = mockPost(`${API_BASE_URL}/stoichiometry/molecule/info`, () => HttpResponse.error());

    await expect(
      getMoleculeInfo(
        {
          smiles: "CC(=O)O",
        },
        token,
      ),
    ).rejects.toThrow();
    expect(requests).toHaveLength(1);
  });
});
