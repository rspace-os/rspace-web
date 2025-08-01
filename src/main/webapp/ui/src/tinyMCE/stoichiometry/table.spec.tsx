import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import { StoichiometryTableWithDataStory } from "./table.story";
import AxeBuilder from "@axe-core/playwright";

const feature = test.extend<{
  Given: {
    "the table is loaded with data": () => Promise<void>;
  };
  Once: {
    "the table has loaded": () => Promise<void>;
  };
  When: {};
  Then: {
    "the table should be visible": () => Promise<void>;
    "the table displays molecule data": () => Promise<void>;
    "there shouldn't be any axe violations": () => Promise<void>;
    "the default columns should be visible": () => Promise<void>;
    "the first reactant should be selected as the default limiting reagent": () => Promise<void>;
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
    });
  },
  When: async ({ page }, use) => {
    await use({});
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
      "the first reactant should be selected as the default limiting reagent": async () => {
        // Find all rows with reactant role
        const dataRows = page.getByRole("row").filter({ hasNot: page.getByRole("columnheader") });
        
        // Get the first row (should be Benzene based on mock data)
        const firstRow = dataRows.first();
        await expect(firstRow).toContainText("Benzene");
        
        // Check that the radio button in the Limiting Reagent column is checked for the first reactant
        const limitingReagentRadio = firstRow.getByRole("radio", { name: /Select Benzene as limiting reagent/ });
        await expect(limitingReagentRadio).toBeChecked();
      },
    });
  },
});

feature.beforeEach(async ({ router }) => {
  await router.route("/chemical/stoichiometry*", (route) => {
    // Mock data with all limitingReagent values set to false to test default selection behavior
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
            formula: "C6 H6",
            name: "Benzene",
            smiles: "C1=CC=CC=C1",
            coefficient: 1.0,
            molecularWeight: 78.11,
            mass: null,
            moles: null,
            expectedAmount: null,
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
            expectedAmount: null,
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
            expectedAmount: null,
            actualAmount: null,
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

  feature("Sets first reactant as default limiting reagent when none is selected", async ({ Given, Once, Then }) => {
    await Given["the table is loaded with data"]();
    await Once["the table has loaded"]();
    await Then["the first reactant should be selected as the default limiting reagent"]();
  });
});
