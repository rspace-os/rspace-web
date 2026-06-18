import { HttpResponse, http, type RequestHandler } from "msw";

export { OAUTH_TOKEN, oauthTokenHandler } from "@/__tests__/mocks/inventoryMocks";

/*
 * Shared MSW fixtures + handlers for the stoichiometry browser-mode tests
 * (the successors to the Playwright component tests). The Playwright versions
 * mocked the network with `router.route`/`page.route`; here the same responses
 * are expressed as MSW request handlers, registered per-test via
 * `worker.use(...)`.
 */

export const chemistryIntegrationHandler = (): RequestHandler =>
  http.get("/integration/integrationInfo", () =>
    HttpResponse.json({
      data: {
        name: "CHEMISTRY",
        displayName: "Chemistry",
        available: true,
        enabled: true,
        oauthConnected: false,
        options: {},
      },
      error: null,
      success: true,
      errorMsg: null,
    }),
  );

export type MockRouteResponse = {
  status?: number;
  body: unknown;
};

/** Full 4-molecule response used by the StoichiometryTable tests. */
export function createMockStoichiometryResponse() {
  return {
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
        role: "REACTANT",
        formula: "C6 H6",
        name: "Benzene",
        smiles: "C1=CC=CC=C1",
        inventoryLink: null,
        coefficient: 1.0,
        molecularWeight: 1.0,
        mass: 10.0,
        moles: 10.0,
        actualAmount: 2.0,
        actualYield: 2.0,
        limitingReagent: true,
        notes: null,
      },
      {
        id: 5,
        rsChemElement: {
          id: 32771,
          parentId: null,
          ecatChemFileId: null,
          dataImage: null,
          chemElements: "C1C=CC=C1",
          smilesString: null,
          chemId: null,
          reactionId: null,
          rgroupId: null,
          metadata: null,
          chemElementsFormat: "MOL",
          creationDate: 1753964548126,
          imageFileProperty: null,
        },
        role: "REACTANT",
        formula: "C5 H6",
        name: "Cyclopentadiene",
        smiles: "C1C=CC=C1",
        inventoryLink: {
          id: 501,
          inventoryItemGlobalId: "SS123",
          stockDeducted: false,
          stoichiometryMoleculeId: 5,
          quantity: {
            numericValue: 10,
            unitId: 20,
          },
        },
        coefficient: 2.0,
        molecularWeight: 1.0,
        mass: 10.0,
        moles: 10.0,
        actualAmount: 5.0,
        actualYield: 5.0,
        limitingReagent: false,
        notes: null,
      },
      {
        id: 6,
        rsChemElement: {
          id: 32772,
          parentId: null,
          ecatChemFileId: null,
          dataImage: null,
          chemElements: "C1CCCCC1",
          smilesString: null,
          chemId: null,
          reactionId: null,
          rgroupId: null,
          metadata: null,
          chemElementsFormat: "MOL",
          creationDate: 1753964548127,
          imageFileProperty: null,
        },
        role: "PRODUCT",
        formula: "C6 H12",
        name: "Cyclopentane",
        smiles: "C1CCCCC1",
        inventoryLink: {
          id: 502,
          inventoryItemGlobalId: "SS124",
          stockDeducted: false,
          stoichiometryMoleculeId: 6,
          quantity: {
            numericValue: 10,
            unitId: 7,
          },
        },
        coefficient: 3.0,
        molecularWeight: 1.0,
        mass: 10.0,
        moles: 10.0,
        actualAmount: 5.0,
        actualYield: 5.0,
        limitingReagent: false,
        notes: null,
      },
      {
        id: 7,
        rsChemElement: {
          id: 32773,
          parentId: null,
          ecatChemFileId: null,
          dataImage: null,
          chemElements: "CCO",
          smilesString: null,
          chemId: null,
          reactionId: null,
          rgroupId: null,
          metadata: null,
          chemElementsFormat: "MOL",
          creationDate: 1753964548128,
          imageFileProperty: null,
        },
        role: "AGENT",
        formula: "C2 H6 O",
        name: "Ethanol",
        smiles: "CCO",
        inventoryLink: {
          id: 503,
          inventoryItemGlobalId: "SS125",
          stockDeducted: false,
          stoichiometryMoleculeId: 7,
          quantity: {
            numericValue: 25,
            unitId: 3,
          },
        },
        coefficient: 1.0,
        molecularWeight: 46.07,
        mass: 5.0,
        moles: 0.109,
        actualAmount: 5.0,
        actualYield: null,
        limitingReagent: false,
        notes: null,
      },
    ],
  };
}

export function createDefaultSubSampleResponses(): Map<number, MockRouteResponse> {
  return new Map<number, MockRouteResponse>([
    [
      123,
      {
        body: {
          id: 123,
          globalId: "SS123",
          quantity: { numericValue: 4, unitId: 7 },
        },
      },
    ],
    [
      124,
      {
        body: {
          id: 124,
          globalId: "SS124",
          quantity: { numericValue: 10, unitId: 7 },
        },
      },
    ],
    [
      125,
      {
        body: {
          id: 125,
          globalId: "SS125",
          quantity: { numericValue: 25, unitId: 3 },
        },
      },
    ],
  ]);
}

/** 3-molecule "no values yet" response used by the StoichiometryDialog tests. */
export function createDialogStoichiometryResponse() {
  return {
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
        role: "REACTANT",
        formula: "C6 H6",
        name: "Benzene",
        smiles: "C1=CC=CC=C1",
        coefficient: 1.0,
        molecularWeight: 78.11,
        mass: null,
        moles: null,
        actualAmount: null,
        actualYield: null,
        limitingReagent: false,
        notes: null,
      },
      {
        id: 5,
        rsChemElement: {
          id: 32771,
          parentId: null,
          ecatChemFileId: null,
          dataImage: null,
          chemElements: "C1C=CC=C1",
          smilesString: null,
          chemId: null,
          reactionId: null,
          rgroupId: null,
          metadata: null,
          chemElementsFormat: "MOL",
          creationDate: 1753964548126,
          imageFileProperty: null,
        },
        role: "REACTANT",
        formula: "C5 H6",
        name: "Cyclopentadiene",
        smiles: "C1C=CC=C1",
        coefficient: 1.0,
        molecularWeight: 66.1,
        mass: null,
        moles: null,
        actualAmount: null,
        actualYield: null,
        limitingReagent: false,
        notes: null,
      },
      {
        id: 6,
        rsChemElement: {
          id: 32772,
          parentId: null,
          ecatChemFileId: null,
          dataImage: null,
          chemElements: "C1CCCCC1",
          smilesString: null,
          chemId: null,
          reactionId: null,
          rgroupId: null,
          metadata: null,
          chemElementsFormat: "MOL",
          creationDate: 1753964548127,
          imageFileProperty: null,
        },
        role: "PRODUCT",
        formula: "C6 H12",
        name: "Cyclohexane",
        smiles: "C1CCCCC1",
        coefficient: 1.0,
        molecularWeight: 84.16,
        mass: null,
        moles: null,
        actualAmount: null,
        actualYield: null,
        limitingReagent: false,
        notes: null,
      },
    ],
  };
}

/** Molecule-info lookups keyed by SMILES (PubChem/Gallery/manual add flows). */
export function moleculeInfoHandler(): RequestHandler {
  return http.post("/api/v1/stoichiometry/molecule/info", async ({ request }) => {
    const body = (await request.json().catch(() => null)) as {
      chemical?: string;
    } | null;
    switch (body?.chemical) {
      case "CN1C=NC2=C1C(=O)N(C(=O)N2C)C": // Caffeine
        return HttpResponse.json({ molecularWeight: 194.19, formula: "C8H10N4O2" });
      case "CCO": // Ethanol
        return HttpResponse.json({ molecularWeight: 46.07, formula: "C2H6O" });
      default:
        return HttpResponse.json({ molecularWeight: 100.0, formula: "Unknown" });
    }
  });
}

export function pubchemSearchHandler(): RequestHandler {
  return http.post("/api/v1/pubchem/search", () =>
    HttpResponse.json([
      {
        name: "Caffeine",
        pngImage: "data:image/png;base64,mock-caffeine-image",
        smiles: "CN1C=NC2=C1C(=O)N(C(=O)N2C)C",
        cas: "58-08-2",
        formula: "C8H10N4O2",
        pubchemId: "2519",
        pubchemUrl: "https://pubchem.ncbi.nlm.nih.gov/compound/2519",
      },
    ]),
  );
}

/*
 * The Gallery picker fires a number of fire-and-forget support requests
 * (deployment properties, office/collabora capabilities, linked documents,
 * thumbnails). They are irrelevant to the stoichiometry flow, but if they 404
 * the resulting axios rejections surface as unhandled errors under Vitest, so
 * answer them with benign payloads.
 */
export function galleryPickerSupportHandlers(): RequestHandler[] {
  return [
    http.get("/deploymentproperties/ajax/property", () => HttpResponse.json(false)),
    http.get("/collaboraOnline/supportedExts", () => HttpResponse.json([])),
    http.get("/officeOnline/supportedExts", () => HttpResponse.json([])),
    http.get("/gallery/ajax/getLinkedDocuments/:id", () => HttpResponse.json({ data: { results: [], totalHits: 0 } })),
    http.get("/gallery/getChemThumbnail/:id/:revision", () => HttpResponse.json(null)),
  ];
}

export function galleryFilesHandler(): RequestHandler {
  return http.get("/gallery/getUploadedFiles", () =>
    HttpResponse.json({
      data: {
        items: {
          results: [
            {
              id: 1001,
              oid: { idString: "GF1001" },
              name: "ethanol.mol",
              extension: "mol",
              type: "Chemistry",
              size: 1024,
              creationDate: 1754999824000,
              modificationDate: 1754999824000,
              thumbnailId: -1,
              version: 1,
              chemString: "CCO",
            },
          ],
          totalHits: 1,
        },
      },
    }),
  );
}
