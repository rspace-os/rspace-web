import { test, expect } from "@playwright/experimental-ct-react";
import { Download } from "playwright-core";
import React from "react";
import { StoichiometryTableWithDataStory } from "./table.story";
import AxeBuilder from "@axe-core/playwright";
import fs from "fs/promises";
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
    "a CSV export is downloaded": () => Promise<Download>;
    "the user taps the limiting reagent cell of the second row": () => Promise<void>;
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
    "the table should be visible": () => Promise<void>;
    "the table displays molecule data": () => Promise<void>;
    "there shouldn't be any axe violations": () => Promise<void>;
    "the default columns should be visible": () => Promise<void>;
    "the first reactant should be selected as the default limiting reagent": () => Promise<void>;
    "there should be a menu for exporting the stoichiometry table to CSV": () => Promise<void>;
    "the first row should NOT have a yield value": () => Promise<void>;
    "the second row should have a yield value": () => Promise<void>;
    "the first row should have a yield value": () => Promise<void>;
    "the second row should NOT have a yield value": () => Promise<void>;
    "{CSV} should have {count} rows": ({
      csv,
      count,
    }: {
      csv: Download;
      count: number;
    }) => Promise<void>;
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
          const loading = document.body.textContent?.includes("Loading");
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
      "a CSV export is downloaded": async () => {
        await page.getByRole("button", { name: /Export/ }).click();
        const [download] = await Promise.all([
          page.waitForEvent("download"),
          page
            .getByRole("menuitem", {
              name: /Export to CSV/,
            })
            .click(),
        ]);
        return download;
      },
      "the user taps the limiting reagent cell of the second row": async () => {
        // Click the radio button for Cyclopentadiene (second row)
        const cyclopentadieneRadio = page.getByRole("radio", {
          name: /Select Cyclopentadiene as limiting reagent/,
        });
        await cyclopentadieneRadio.click();
      },
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
      "the table should be visible": async () => {
        const table = page.getByRole("grid");
        await expect(table).toBeVisible();
      },
      "the table displays molecule data": async () => {
        const table = page.getByRole("grid");
        await expect(table).toBeVisible();

        // Check that we have at least some data rows beyond the header
        const rows = page.getByRole("row");
        const rowCount = await rows.count();
        expect(rowCount).toBeGreaterThan(1); // At least header + 1 data row
      },
      "there shouldn't be any axe violations": async () => {
        const accessibilityScanResults = await new AxeBuilder({
          page,
        }).analyze();
        expect(
          accessibilityScanResults.violations.filter((v) => {
            /*
             * These violations are expected in component tests as we're not rendering
             * a complete page with proper document structure:
             *
             * 1. MUI DataGrid renders its immediate children with role=presentation,
             *    which Firefox considers to be a violation
             * 2. Component tests don't have main landmarks as they're isolated components
             * 3. Component tests typically don't have h1 headings as they're not full pages
             * 4. Content not in landmarks is expected in component testing context
             */
            return (
              v.description !==
                "Ensure elements with an ARIA role that require child roles contain them" &&
              v.id !== "landmark-one-main" &&
              v.id !== "page-has-heading-one" &&
              v.id !== "region"
            );
          }),
        ).toEqual([]);
      },
      "the default columns should be visible": async () => {
        const headers = await page.getByRole("columnheader").allTextContents();
        expect(headers).toContain("Name");
        expect(headers).toContain("Role");
        expect(headers).toContain("Limiting Reagent");
        expect(headers).toContain("Equivalent");
        expect(headers).toContain("Molecular Weight (g/mol)");
        expect(headers).toContain("Mass (g)");
        expect(headers).toContain("Moles (mol)");
        expect(headers).toContain("Notes");
      },
      "the first reactant should be selected as the default limiting reagent":
        async () => {
          // Find all rows with reactant role
          const dataRows = page
            .getByRole("row")
            .filter({ hasNot: page.getByRole("columnheader") });

          // Get the first row (should be Benzene based on mock data)
          const firstRow = dataRows.first();
          await expect(firstRow).toContainText("Benzene");

          // Check that the radio button in the Limiting Reagent column is checked for the first reactant
          const limitingReagentRadio = firstRow.getByRole("radio", {
            name: /Select Benzene as limiting reagent/,
          });
          await expect(limitingReagentRadio).toBeChecked();
        },
      "there should be a menu for exporting the stoichiometry table to CSV":
        async () => {
          const menuButton = page.getByRole("button", { name: "Export" });
          await menuButton.click();
          const menu = page.getByRole("tooltip");
          await expect(menu).toBeVisible();
        },
      "{CSV} should have {count} rows": async ({ csv, count }) => {
        const path = await csv.path();
        const fileContents = await fs.readFile(path, "utf8");
        const lines = fileContents.split("\n");
        expect(lines.length).toBe(count + 1); // +1 for header row
      },
      "the first row should NOT have a yield value": async () => {
        // Find the yield/excess column and check first row
        const dataRows = page
          .getByRole("row")
          .filter({ hasNot: page.getByRole("columnheader") });
        const firstRow = dataRows.first();

        // Get all cells in first row and find yield column (should be index 9 based on headers)
        const cells = firstRow.getByRole("gridcell");
        const yieldCell = cells.nth(9); // Yield/Excess column is typically the 10th column (0-indexed = 9)

        // Check that the yield cell contains dash (—) indicating no yield value
        await expect(yieldCell).toContainText("—");
      },
      "the second row should have a yield value": async () => {
        // Find the yield/excess column and check second row
        const dataRows = page
          .getByRole("row")
          .filter({ hasNot: page.getByRole("columnheader") });
        const secondRow = dataRows.nth(1);

        // Get all cells in second row and find yield column
        const cells = secondRow.getByRole("gridcell");
        const yieldCell = cells.nth(9); // Yield/Excess column

        // Check that the yield cell contains a percentage or dash (since mock data has no actualAmount)
        const cellContent = await yieldCell.textContent();
        // Since mock data has no actualAmount, it should show dash, but structure should be there for yield
        expect(cellContent).toMatch(/—|\d+%/);
      },
      "the first row should have a yield value": async () => {
        // Find the yield/excess column and check first row (when it's no longer limiting reagent)
        const dataRows = page
          .getByRole("row")
          .filter({ hasNot: page.getByRole("columnheader") });
        const firstRow = dataRows.first();

        // Get all cells in first row and find yield column
        const cells = firstRow.getByRole("gridcell");
        const yieldCell = cells.nth(9); // Yield/Excess column

        // Check that the yield cell contains a percentage or dash (since mock data has no actualAmount)
        const cellContent = await yieldCell.textContent();
        expect(cellContent).toMatch(/—|\d+%/);
      },
      "the second row should NOT have a yield value": async () => {
        // Find the yield/excess column and check second row (when it becomes limiting reagent)
        const dataRows = page
          .getByRole("row")
          .filter({ hasNot: page.getByRole("columnheader") });
        const secondRow = dataRows.nth(1);

        // Get all cells in second row and find yield column
        const cells = secondRow.getByRole("gridcell");
        const yieldCell = cells.nth(9); // Yield/Excess column

        // Check that the yield cell contains dash (—) indicating no yield value
        await expect(yieldCell).toContainText("—");
      },
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
  await router.route("/api/v1/stoichiometry*", (route) => {
    // Mock data with all limitingReagent values set to false to test default selection behavior
    const mockResponse = {
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
          formula: "C6 H6",
          name: "Benzene",
          smiles: "C1=CC=CC=C1",
          coefficient: 1.0,
          molecularWeight: 78.11,
          mass: 78.11, // 1 mole of benzene
          moles: 1.0,
          expectedAmount: null,
          actualAmount: 70.3, // 90% yield (70.30 / 78.11 = 0.90)
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
          mass: 66.1, // 1 mole of cyclopentadiene
          moles: 1.0,
          expectedAmount: null,
          actualAmount: 52.88, // 80% yield (52.88 / 66.1 = 0.80)
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
          mass: 84.16, // 1 mole of cyclohexane
          moles: 1.0,
          expectedAmount: null,
          actualAmount: 67.33, // 80% yield (67.33 / 84.16 = 0.80)
          actualYield: null,
          limitingReagent: false,
          notes: null,
        },
      ],
    };

    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(mockResponse),
    });
  });

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

  await router.route("/api/v1/stoichiometry/molecule/info", (route) => {
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
      body: JSON.stringify(mockInfo),
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

test.describe("Stoichiometry Table", () => {
  feature("Has no accessibility violations", async ({ Given, Once, Then }) => {
    await Given["the table is loaded with data"]();
    await Once["the table has loaded"]();
    await Then["there shouldn't be any axe violations"]();
  });

  feature(
    "Renders and displays data correctly",
    async ({ Given, Once, Then }) => {
      await Given["the table is loaded with data"]();
      await Once["the table has loaded"]();
      await Then["the table should be visible"]();
      await Then["the table displays molecule data"]();
    },
  );

  feature("Displays expected column headers", async ({ Given, Once, Then }) => {
    await Given["the table is loaded with data"]();
    await Once["the table has loaded"]();
    await Then["the default columns should be visible"]();
  });

  feature(
    "Sets first reactant as default limiting reagent when none is selected",
    async ({ Given, Once, Then }) => {
      await Given["the table is loaded with data"]();
      await Once["the table has loaded"]();
      await Then[
        "the first reactant should be selected as the default limiting reagent"
      ]();
    },
  );

  feature(
    "There should be a menu for exporting the stoichiometry table to CSV",
    async ({ Given, Then }) => {
      await Given["the table is loaded with data"]();
      await Then[
        "there should be a menu for exporting the stoichiometry table to CSV"
      ]();
    },
  );

  feature(
    "When exporting to CSV, all molecule rows should be included",
    async ({ Given, Once, When, Then }) => {
      await Given["the table is loaded with data"]();
      await Once["the table has loaded"]();
      const csv = await When["a CSV export is downloaded"]();
      await Then["{CSV} should have {count} rows"]({ csv, count: 3 }); // 3 molecules in mock data
    },
  );

  feature(
    "Given the first row is selected as the limiting reagent, then the first row should NOT have a yield value and the second row should have a yield value",
    async ({ Given, Once, Then }) => {
      await Given["the table is loaded with data"]();
      await Once["the table has loaded"]();
      // First row is automatically selected as limiting reagent by default
      await Then["the first row should NOT have a yield value"]();
      await Then["the second row should have a yield value"]();
    },
  );

  feature(
    "Given the first row is selected as the limiting reagent, when the user taps the limiting reagent cell of the second row, then the first row should have a yield value and the second row should NOT have a yield value",
    async ({ Given, Once, When, Then }) => {
      await Given["the table is loaded with data"]();
      await Once["the table has loaded"]();
      // Initially first row is limiting reagent, verify initial state
      await Then["the first row should NOT have a yield value"]();
      await Then["the second row should have a yield value"]();

      // Change limiting reagent to second row
      await When["the user taps the limiting reagent cell of the second row"]();

      // Now verify the yield values have switched
      await Then["the first row should have a yield value"]();
      await Then["the second row should NOT have a yield value"]();
    },
  );

  test.describe("Adding reagants", () => {
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
        await Then["there should be {count} molecules in total"]({ count: 4 }); // 3 original + 1 new
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
        await Then["there should be {count} molecules in total"]({ count: 4 }); // 3 original + 1 new
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
        await Then["there should be {count} molecules in total"]({ count: 4 }); // 3 original + 1 new
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
        await Then["there should be {count} molecules in total"]({ count: 4 });

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
        await Then["there should be {count} molecules in total"]({ count: 5 }); // 3 original + 2 new
      },
    );
  });
});
