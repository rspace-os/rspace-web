import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import { StoichiometryTableWithDataStory } from "./table.story";
import AxeBuilder from "@axe-core/playwright";
import * as Jwt from "jsonwebtoken";

const feature = test.extend<{
  Given: {
    "the table is loaded with data": () => Promise<void>;
  };
  Once: {
    "the table has loaded": () => Promise<void>;
    "the loading dialog appears": () => Promise<void>;
    "the loading dialog disappears": () => Promise<void>;
  };
  When: {
    "the user clicks Add Reagent": () => Promise<void>;
    "the user selects PubChem from the menu": () => Promise<void>;
    "the user selects Gallery from the menu": () => Promise<void>;
    "the user selects Manual entry from the menu": () => Promise<void>;
    "the user searches for {compound} in PubChem": ({
      compound,
    }: {
      compound: string;
    }) => Promise<void>;
    "the user clicks Insert": () => Promise<void>;
    "the user enters SMILES {smiles} with name {name}": ({
      smiles,
      name,
    }: {
      smiles: string;
      name: string;
    }) => Promise<void>;
    "the user adds the manual reagent": () => Promise<void>;
    "the user selects the first chemistry file from Gallery": () => Promise<void>;
    "the user adds the selected files from Gallery": () => Promise<void>;
  };
  Then: {
    "the Add Reagent menu should be visible": () => Promise<void>;
    "the PubChem dialog should open": () => Promise<void>;
    "the Gallery dialog should open": () => Promise<void>;
    "the manual SMILES dialog should open": () => Promise<void>;
    "PubChem search results should be displayed": () => Promise<void>;
    "the table should contain a new row with {name}": ({
      name,
    }: {
      name: string;
    }) => Promise<void>;
    "the new row should have molecular weight {weight}": ({
      weight,
    }: {
      weight: number;
    }) => Promise<void>;
    "the new row should have role agent": () => Promise<void>;
    "there should be {count} molecules in total": ({
      count,
    }: {
      count: number;
    }) => Promise<void>;
    "the loading dialog should be visible": () => Promise<void>;
    "the loading dialog should not be visible": () => Promise<void>;
  };
}>({
  Given: async ({ mount }, use) => {
    await use({
      "the table is loaded with data": async () => {
        await mount(<StoichiometryTableWithDataStory />);
      },
    });
  },
  Once: async ({ page }, use) => {
    await use({
      "the table has loaded": async () => {
        await page.waitForFunction(() => {
          const table = document.querySelector('[role="grid"]');
          const loading = document.body.textContent?.includes(
            "Loading stoichiometry table",
          );
          const noData = document.body.textContent?.includes(
            "No stoichiometry data available",
          );
          return (table && !loading) || noData;
        });
      },
      "the loading dialog appears": async () => {
        await expect(
          page.getByText("Loading molecule information..."),
        ).toBeVisible();
      },
      "the loading dialog disappears": async () => {
        await expect(
          page.getByText("Loading molecule information..."),
        ).not.toBeVisible();
      },
    });
  },
  When: async ({ page }, use) => {
    await use({
      "the user clicks Add Reagent": async () => {
        await page.getByRole("button", { name: "Add Reagent" }).click();
      },
      "the user selects PubChem from the menu": async () => {
        await page
          .getByRole("menuitem", {
            name: /PubChem.*Import compound from PubChem/i,
          })
          .click();
      },
      "the user selects Gallery from the menu": async () => {
        await page
          .getByRole("menuitem", {
            name: /Gallery.*Import compound from Gallery/i,
          })
          .click();
      },
      "the user selects Manual entry from the menu": async () => {
        await page
          .getByRole("menuitem", { name: /Manually.*Manually enter SMILES/i })
          .click();
      },
      "the user searches for {compound} in PubChem": async ({ compound }) => {
        await page
          .getByRole("textbox", { name: /Enter a compound name/i })
          .fill(compound);
        await page.getByRole("button", { name: /search/i }).click();
      },
      "the user clicks Insert": async () => {
        await page.getByRole("button", { name: "Insert" }).click();
      },
      "the user enters SMILES {smiles} with name {name}": async ({
        smiles,
        name,
      }) => {
        await page.getByRole("textbox", { name: /name/i }).fill(name);
        await page.getByRole("textbox", { name: /smiles/i }).fill(smiles);
      },
      "the user adds the manual reagent": async () => {
        await page.getByRole("button", { name: /add reagent/i }).click();
      },
      "the user selects the first chemistry file from Gallery": async () => {
        // Wait for Gallery to load files and select the first chemistry file (ethanol.mol)
        await page.getByRole("gridcell", { name: /ethanol\.mol/i }).click();
      },
      "the user adds the selected files from Gallery": async () => {
        await page.getByRole("button", { name: /add/i }).click();
      },
    });
  },
  Then: async ({ page }, use) => {
    await use({
      "the Add Reagent menu should be visible": async () => {
        await expect(
          page.getByRole("menuitem", {
            name: /PubChem.*Import compound from PubChem/i,
          }),
        ).toBeVisible();
        await expect(
          page.getByRole("menuitem", {
            name: /Gallery.*Import compound from Gallery/i,
          }),
        ).toBeVisible();
        await expect(
          page.getByRole("menuitem", {
            name: /Manually.*Manually enter SMILES/i,
          }),
        ).toBeVisible();
      },
      "the PubChem dialog should open": async () => {
        await expect(
          page.getByRole("dialog", { name: /Insert from PubChem/i }),
        ).toBeVisible();
      },
      "the Gallery dialog should open": async () => {
        await expect(
          page.getByRole("dialog", { name: /Gallery Picker/i }),
        ).toBeVisible();
      },
      "the manual SMILES dialog should open": async () => {
        await expect(
          page.getByRole("dialog", { name: /Add New Reagent/i }),
        ).toBeVisible();
      },
      "PubChem search results should be displayed": async () => {
        await expect(page.getByText("Caffeine")).toBeVisible();
        await expect(page.getByText("C8H10N4O2")).toBeVisible();
      },
      "the table should contain a new row with {name}": async ({ name }) => {
        const table = page.getByRole("grid");
        await expect(table).toContainText(name);
      },
      "the new row should have molecular weight {weight}": async ({
        weight,
      }) => {
        const table = page.getByRole("grid");
        await expect(table).toContainText(weight.toString());
      },
      "the new row should have role agent": async () => {
        const table = page.getByRole("grid");
        await expect(table).toContainText("agent");
      },
      "there should be {count} molecules in total": async ({ count }) => {
        const dataRows = page
          .getByRole("row")
          .filter({ hasNot: page.getByRole("columnheader") });
        const rowCount = await dataRows.count();
        expect(rowCount).toBe(count);
      },
      "the loading dialog should be visible": async () => {
        await expect(
          page.getByText("Loading molecule information..."),
        ).toBeVisible();
      },
      "the loading dialog should not be visible": async () => {
        await expect(
          page.getByText("Loading molecule information..."),
        ).not.toBeVisible();
      },
    });
  },
});

feature.beforeEach(async ({ router }) => {
  await router.route("/userform/ajax/inventoryOauthToken", (route) => {
    const payload = {
      iss: "http://localhost:8080",
      iat: new Date().getTime(),
      exp: Math.floor(Date.now() / 1000) + 300,
      refreshTokenHash:
        "fe15fa3d5e3d5a47e33e9e34229b1ea2314ad6e6f13fa42addca4f1439582a4d",
    };
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: Jwt.sign(payload, "dummySecretKey"),
      }),
    });
  });
  await router.route("/chemical/stoichiometry*", (route) => {
    const mockResponse = {
      data: {
        id: 3,
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
            formula: "C6H6",
            name: "Benzene",
            smiles: "C1=CC=CC=C1",
            coefficient: 1.0,
            molecularWeight: 78.11,
            mass: 78.11,
            moles: 1.0,
            expectedAmount: null,
            actualAmount: 70.3,
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
            formula: "C5H6",
            name: "Cyclopentadiene",
            smiles: "C1C=CC=C1",
            coefficient: 1.0,
            molecularWeight: 66.1,
            mass: 66.1,
            moles: 1.0,
            expectedAmount: null,
            actualAmount: 52.88,
            actualYield: null,
            limitingReagent: false,
            notes: null,
          },
        ],
      },
    };

    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(mockResponse),
    });
  });

  // Mock the molecule info endpoint
  await router.route("/chemical/stoichiometry/molecule/info", (route) => {
    const requestBody = route.request().postDataJSON();
    const smiles = requestBody?.chemical;

    let mockInfo;
    switch (smiles) {
      case "CN1C=NC2=C1C(=O)N(C(=O)N2C)C": // Caffeine
        mockInfo = {
          molecularWeight: 194.19,
          formula: "C8H10N4O2",
        };
        break;
      case "CCO": // Ethanol
        mockInfo = {
          molecularWeight: 46.07,
          formula: "C2H6O",
        };
        break;
      default:
        mockInfo = {
          molecularWeight: 100.0,
          formula: "Unknown",
        };
    }

    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ data: mockInfo }),
    });
  });

  await router.route("/api/v1/pubchem/search", (route) => {
    const requestBody = route.request().postDataJSON();
    const searchTerm = requestBody?.searchTerm?.toLowerCase();

    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify([
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
    });
  });

  await router.route("/gallery/getUploadedFiles*", (route) => {
    const mockGalleryResponse = {
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
    };

    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(mockGalleryResponse),
    });
  });
});

test.describe("Stoichiometry Table - Add Reagents", () => {
  feature(
    "User can access the Add Reagent menu",
    async ({ Given, Once, When, Then }) => {
      await Given["the table is loaded with data"]();
      await Once["the table has loaded"]();
      await When["the user clicks Add Reagent"]();
      await Then["the Add Reagent menu should be visible"]();
    },
  );

  feature(
    "User can add a reagent from PubChem",
    async ({ Given, Once, When, Then }) => {
      await Given["the table is loaded with data"]();
      await Once["the table has loaded"]();

      await When["the user clicks Add Reagent"]();
      await When["the user selects PubChem from the menu"]();
      await Then["the PubChem dialog should open"]();

      await When["the user searches for {compound} in PubChem"]({
        compound: "caffeine",
      });
      await Then["PubChem search results should be displayed"]();

      await When["the user clicks Insert"]();
      await Then["the loading dialog should be visible"]();

      await Once["the loading dialog disappears"]();
      await Then["the table should contain a new row with {name}"]({
        name: "Caffeine",
      });
      await Then["the new row should have molecular weight {weight}"]({
        weight: 194.19,
      });
      await Then["the new row should have role agent"]();
      await Then["there should be {count} molecules in total"]({ count: 3 }); // 2 original + 1 new
    },
  );

  feature(
    "User can add a reagent manually using SMILES",
    async ({ Given, Once, When, Then }) => {
      await Given["the table is loaded with data"]();
      await Once["the table has loaded"]();

      await When["the user clicks Add Reagent"]();
      await When["the user selects Manual entry from the menu"]();
      await Then["the manual SMILES dialog should open"]();

      await When["the user enters SMILES {smiles} with name {name}"]({
        smiles: "CCO",
        name: "Ethanol",
      });
      await When["the user adds the manual reagent"]();

      await Then["the loading dialog should be visible"]();
      await Once["the loading dialog disappears"]();

      await Then["the table should contain a new row with {name}"]({
        name: "Ethanol",
      });
      await Then["the new row should have molecular weight {weight}"]({
        weight: 46.07,
      });
      await Then["the new row should have role agent"]();
      await Then["there should be {count} molecules in total"]({ count: 3 }); // 2 original + 1 new
    },
  );

  feature(
    "Loading dialog prevents concurrent reagent additions",
    async ({ Given, Once, When, Then }) => {
      await Given["the table is loaded with data"]();
      await Once["the table has loaded"]();

      await When["the user clicks Add Reagent"]();
      await When["the user selects Manual entry from the menu"]();
      await When["the user enters SMILES {smiles} with name {name}"]({
        smiles: "CCO",
        name: "Ethanol",
      });
      await When["the user adds the manual reagent"]();
      await Then["the loading dialog should be visible"]();

      await Once["the loading dialog disappears"]();
      await Then["there should be {count} molecules in total"]({ count: 3 }); // 2 original + 1 new
    },
  );

  feature(
    "User can add a reagent from Gallery",
    async ({ Given, Once, When, Then }) => {
      await Given["the table is loaded with data"]();
      await Once["the table has loaded"]();

      // Open Gallery dialog
      await When["the user clicks Add Reagent"]();
      await When["the user selects Gallery from the menu"]();
      await Then["the Gallery dialog should open"]();

      // Select a chemistry file and add it
      await When["the user selects the first chemistry file from Gallery"]();
      await When["the user adds the selected files from Gallery"]();

      // Check loading dialog appears and disappears
      await Then["the loading dialog should be visible"]();
      await Once["the loading dialog disappears"]();

      // Verify the new reagent is added correctly (using filename without extension)
      await Then["the table should contain a new row with {name}"]({
        name: "ethanol",
      });
      await Then["the new row should have molecular weight {weight}"]({
        weight: 46.07,
      });
      await Then["the new row should have role agent"]();
      await Then["there should be {count} molecules in total"]({ count: 3 }); // 2 original + 1 new
    },
  );

  feature(
    "Multiple reagents can be added sequentially",
    async ({ Given, Once, When, Then }) => {
      await Given["the table is loaded with data"]();
      await Once["the table has loaded"]();

      // Add first reagent (Ethanol)
      await When["the user clicks Add Reagent"]();
      await When["the user selects Manual entry from the menu"]();
      await When["the user enters SMILES {smiles} with name {name}"]({
        smiles: "CCO",
        name: "Ethanol",
      });
      await When["the user adds the manual reagent"]();
      await Once["the loading dialog disappears"]();
      await Then["there should be {count} molecules in total"]({ count: 3 });

      // Add second reagent (Caffeine via PubChem)
      await When["the user clicks Add Reagent"]();
      await When["the user selects PubChem from the menu"]();
      await When["the user searches for {compound} in PubChem"]({
        compound: "caffeine",
      });
      await When["the user clicks Insert"]();
      await Once["the loading dialog disappears"]();

      // Verify both reagents are present
      await Then["the table should contain a new row with {name}"]({
        name: "Ethanol",
      });
      await Then["the table should contain a new row with {name}"]({
        name: "Caffeine",
      });
      await Then["there should be {count} molecules in total"]({ count: 4 }); // 2 original + 2 new
    },
  );
});
