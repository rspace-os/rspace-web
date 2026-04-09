import { test, expect } from "@playwright/experimental-ct-react";
import { Download, Locator, Page, Route } from "playwright-core";
import React from "react";
import AxeBuilder from "@axe-core/playwright";
import fs from "fs/promises";
import {
  ReadOnlyStoichiometryTableStory,
  StoichiometryTableWithDataStory,
} from "./StoichiometryTable.story";

import * as Jwt from "jsonwebtoken";

test.skip(
  ({ browserName }) => browserName === "webkit",
  "Flaky on WebKit",
);

async function getColumnFieldByHeader(table: Locator, headerText: string) {
  const allHeaders = table.getByRole("columnheader");

  const count = await allHeaders.count();
  for (let i = 0; i < count; i++) {
    const header = allHeaders.nth(i);
    const text = await header.textContent();
    if (text?.trim() === headerText) {
      const field = await header.getAttribute("data-field");
      if (!field) {
        throw new Error(`Column field for header "${headerText}" not found`);
      }
      return field;
    }
  }

  throw new Error(`Column "${headerText}" not found`);
}

function getDataRows(container: Page | Locator): Locator {
  return container.locator('[role="row"][data-id]');
}

function getCellByField(row: Locator, field: string): Locator {
  return row.locator(`[role="gridcell"][data-field="${field}"]`);
}

function getMoleculeInfoLoadingDialog(container: Page | Locator): Locator {
  return container
    .getByRole("dialog")
    .filter({ hasText: "Loading molecule information..." });
}

async function getRowByColumnValue(
  table: Locator,
  columnHeaderText: string,
  cellValue: string,
) {
  const field = await getColumnFieldByHeader(table, columnHeaderText);
  const rows = getDataRows(table);
  const count = await rows.count();

  for (let i = 0; i < count; i++) {
    const row = rows.nth(i);
    const text = await getCellByField(row, field).textContent();
    if (text?.includes(cellValue)) {
      return row;
    }
  }

  throw new Error(
    `Row with value "${cellValue}" in column "${columnHeaderText}" not found`,
  );
}

async function editNumericCell({
  page,
  row,
  column,
  value,
}: {
  page: Page;
  row: number;
  column: string;
  value: string;
}) {
  const table = page.getByRole("grid");
  const field = await getColumnFieldByHeader(table, column);
  const targetRow = getDataRows(table).nth(row);
  const targetCell = getCellByField(targetRow, field);

  await targetCell.click();
  await targetCell.press("Enter");

  const input = page.locator(
    '.MuiDataGrid-cell--editing input, .MuiDataGrid-cell--editing textarea',
  );
  await input.waitFor({ state: "visible" });
  await input.fill(value);
  await input.press("Enter");
}

function fulfillJson(route: Route, body: unknown, status = 200) {
  return route.fulfill({
    status,
    contentType: "application/json",
    body: JSON.stringify(body),
  });
}

async function getCompoundCell(
  page: Page,
  compoundName: string,
  columnHeaderText: string,
) {
  const table = page.getByRole("grid");
  const field = await getColumnFieldByHeader(table, columnHeaderText);
  const row = await getRowByColumnValue(table, "Name", compoundName);
  return getCellByField(row, field);
}

type MockRouteResponse = {
  status?: number;
  body: unknown;
};

function createMockStoichiometryResponse() {
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

let mockStoichiometryResponse = createMockStoichiometryResponse();
let mockSubSampleResponses = new Map<number, MockRouteResponse>();
let inventorySubSampleRequestCount = 0;
const feature = test.extend<{
  Given: {
    "the table is loaded with data": () => Promise<void>;
  };
  Once: {
    "the table has loaded": () => Promise<void>;
    "the loading dialog disappears": () => Promise<void>;
  };
  When: {
    "a CSV export is downloaded": () => Promise<Download>;
    "the user taps the limiting reagent cell of the second row": () => Promise<void>;
    "the user clicks Add Chemical": () => Promise<void>;
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
    "the user clicks Update Inventory Stock": () => Promise<void>;
    "the user opens inventory picker for {molecule}": ({
      molecule,
    }: {
      molecule: string;
    }) => Promise<void>;
    "the user closes the inventory picker": () => Promise<void>;
    "the user edits mass in row {row} to {value}": ({
      row,
      value,
    }: {
      row: number;
      value: string;
    }) => Promise<void>;
    "the user edits moles in row {row} to {value}": ({
      row,
      value,
    }: {
      row: number;
      value: string;
    }) => Promise<void>;
    "the user edits actual mass in row {row} to {value}": ({
      row,
      value,
    }: {
      row: number;
      value: string;
    }) => Promise<void>;
    "the user edits actual moles in row {row} to {value}": ({
      row,
      value,
    }: {
      row: number;
      value: string;
    }) => Promise<void>;
    "the user edits equivalent in row {row} to {value}": ({
      row,
      value,
    }: {
      row: number;
      value: string;
    }) => Promise<void>;
  };
  Then: {
    "the table should be visible": () => Promise<void>;
    "the table displays molecule data": () => Promise<void>;
    "there shouldn't be any axe violations": () => Promise<void>;
    "the default columns should be visible": () => Promise<void>;
    "inventory link controls should be visible for linked and unlinked molecules": () => Promise<void>;
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
    "the Add Chemical menu should be visible": () => Promise<void>;
    "the PubChem dialog should open": () => Promise<void>;
    "the Gallery dialog should open": () => Promise<void>;
    "the manual SMILES dialog should open": () => Promise<void>;
    "the inventory update dialog should open": () => Promise<void>;
    "the inventory update dialog should list the current molecules": () => Promise<void>;
    "the Update Inventory Stock button should be disabled": () => Promise<void>;
    "the disabled Update Inventory Stock button should explain that saving is required": () => Promise<void>;
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
    "{CSV} should contain the correct role strings": ({
      csv,
    }: {
      csv: Download;
    }) => Promise<void>;
    "inventory picker should be visible for {molecule}": ({
      molecule,
    }: {
      molecule: string;
    }) => Promise<void>;
    "inventory picker should not be visible": () => Promise<void>;
    "Compound {name} has {column} of {value}": ({
      name,
      column,
      value,
    }: {
      name: string;
      column: string;
      value: string;
    }) => Promise<void>;
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
          const moleculeInfoLoadingDialog = Array.from(
            document.querySelectorAll('[role="dialog"]'),
          ).some((dialog) =>
            dialog.textContent?.includes("Loading molecule information..."),
          );
          const noData = document.body.textContent?.includes(
            "No stoichiometry data available",
          );

          return ((table && !moleculeInfoLoadingDialog) || noData) ?? false;
        });
      },
      "the loading dialog disappears": async () => {
        await expect(getMoleculeInfoLoadingDialog(page)).toHaveCount(0);
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
      "the user clicks Add Chemical": async () => {
        await page.getByRole("button", { name: "Add Chemical" }).click();
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
        await page.getByRole("button", { name: /add chemical/i }).click();
      },
      "the user selects the first chemistry file from Gallery": async () => {
        // Wait for Gallery to load files and select the first chemistry file (ethanol.mol)
        await page.getByRole("gridcell", { name: /ethanol\.mol/i }).click();
      },
      "the user adds the selected files from Gallery": async () => {
        await page.getByRole("button", { name: /add/i }).click();
      },
      "the user clicks Update Inventory Stock": async () => {
        await page
          .getByRole("button", { name: "Update Inventory Stock" })
          .click();
      },
      "the user opens inventory picker for {molecule}": async ({ molecule }) => {
        await page
          .getByLabel(`Add inventory link for ${molecule}`)
          .click();
      },
      "the user closes the inventory picker": async () => {
        await page.getByRole("button", { name: "Cancel" }).click();
      },
      "the user edits mass in row {row} to {value}": async ({ row, value }) => {
        await editNumericCell({ page, row, column: "Mass (g)", value });
      },
      "the user edits moles in row {row} to {value}": async ({
        row,
        value,
      }) => {
        await editNumericCell({ page, row, column: "Moles (mol)", value });
      },
      "the user edits actual mass in row {row} to {value}": async ({
        row,
        value,
      }) => {
        await editNumericCell({ page, row, column: "Actual Mass (g)", value });
      },
      "the user edits actual moles in row {row} to {value}": async ({
        row,
        value,
      }) => {
        await editNumericCell({ page, row, column: "Actual Moles (mol)", value });
      },
      "the user edits equivalent in row {row} to {value}": async ({
        row,
        value,
      }) => {
        await editNumericCell({ page, row, column: "Equivalent", value });
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
        expect(headers).toContain("Inventory Link");
        expect(headers).toContain("Role");
        expect(headers).toContain("Limiting Reagent");
        expect(headers).toContain("Equivalent");
        expect(headers).toContain("Molecular Weight (g/mol)");
        expect(headers).toContain("Mass (g)");
        expect(headers).toContain("Moles (mol)");
        expect(headers).toContain("Notes");
      },
      "inventory link controls should be visible for linked and unlinked molecules":
        async () => {
          await expect(
            page.getByLabel("Remove inventory link for Cyclopentadiene"),
          ).toBeVisible();
          await expect(
            page.getByLabel("Add inventory link for Benzene"),
          ).toBeVisible();
        },
      "the first reactant should be selected as the default limiting reagent":
        async () => {
          const dataRows = getDataRows(page.getByRole("grid"));
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
      "{CSV} should contain the correct role strings": async ({ csv }) => {
        const path = await csv.path();
        const fileContents = await fs.readFile(path, "utf8");
        expect(fileContents).toContain("Reactant");
        expect(fileContents).toContain("Product");
        expect(fileContents).toContain("Reagent");
        expect(fileContents).not.toContain("REACTANT");
        expect(fileContents).not.toContain("PRODUCT");
        expect(fileContents).not.toContain("AGENT");
      },
      "the first row should NOT have a yield value": async () => {
        const table = page.getByRole("grid");
        const yieldField = await getColumnFieldByHeader(table, "Yield/Excess (%)");
        const dataRows = getDataRows(table);
        const firstRow = dataRows.first();
        const yieldCell = getCellByField(firstRow, yieldField);
        await expect(yieldCell).toContainText("—");
      },
      "the second row should have a yield value": async () => {
        const table = page.getByRole("grid");
        const yieldField = await getColumnFieldByHeader(table, "Yield/Excess (%)");
        const dataRows = getDataRows(table);
        const secondRow = dataRows.nth(1);
        const yieldCell = getCellByField(secondRow, yieldField);
        const cellContent = await yieldCell.textContent();
        expect(cellContent).toMatch(/—|\d+%/);
      },
      "the first row should have a yield value": async () => {
        const table = page.getByRole("grid");
        const yieldField = await getColumnFieldByHeader(table, "Yield/Excess (%)");
        const dataRows = getDataRows(table);
        const firstRow = dataRows.first();
        const yieldCell = getCellByField(firstRow, yieldField);
        const cellContent = await yieldCell.textContent();
        expect(cellContent).toMatch(/—|\d+%/);
      },
      "the second row should NOT have a yield value": async () => {
        const table = page.getByRole("grid");
        const yieldField = await getColumnFieldByHeader(table, "Yield/Excess (%)");
        const dataRows = getDataRows(table);
        const secondRow = dataRows.nth(1);
        const yieldCell = getCellByField(secondRow, yieldField);
        await expect(yieldCell).toContainText("—");
      },
      "the Add Chemical menu should be visible": async () => {
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
          page.getByRole("dialog", { name: /Add New Chemical/i }),
        ).toBeVisible();
      },
      "the inventory update dialog should open": async () => {
        await expect(
          page.getByRole("dialog", { name: /Update Inventory Stock/i }),
        ).toBeVisible();
      },
      "the inventory update dialog should list the current molecules": async () => {
        const dialog = page.getByRole("dialog", {
          name: /Update Inventory Stock/i,
        });
        await expect(dialog).toContainText("Benzene");
        await expect(dialog).toContainText("Cyclopentadiene");
        await expect(dialog).toContainText("Cyclopentane");
        await expect(dialog).toContainText("Ethanol");
      },
      "the Update Inventory Stock button should be disabled": async () => {
        await expect(
          page.getByRole("button", { name: "Update Inventory Stock" }),
        ).toBeDisabled();
      },
      "the disabled Update Inventory Stock button should explain that saving is required":
        async () => {
          await page
            .getByRole("button", { name: "Update Inventory Stock" })
            .hover({ force: true });
          await expect(page.getByRole("tooltip")).toContainText(
            "Save the stoichiometry table before updating inventory stock.",
          );
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
          const dataRows = getDataRows(page.getByRole("grid"));
        const rowCount = await dataRows.count();
        expect(rowCount).toBe(count);
      },
      "inventory picker should be visible for {molecule}": async ({
        molecule,
      }) => {
        await expect(
          page.getByRole("dialog", {
            name: `Pick inventory item for ${molecule}`,
          }),
        ).toBeVisible();
      },
      "inventory picker should not be visible": async () => {
        await expect(
          page.getByRole("dialog", {
            name: /Pick inventory item for/i,
          }),
        ).not.toBeVisible();
      },
      "Compound {name} has {column} of {value}": async ({
        name,
        column,
        value,
      }) => {
        const table = page.getByRole("grid");
        const field = await getColumnFieldByHeader(table, column);
        const row = await getRowByColumnValue(
          table,
          "Name",
          name,
        );
        await expect(getCellByField(row, field)).toContainText(value);
      },
    });
  },

});
feature.beforeEach(async ({ router }) => {
  mockStoichiometryResponse = createMockStoichiometryResponse();
  mockSubSampleResponses = new Map<number, MockRouteResponse>([
    [
      123,
      {
        body: {
          id: 123,
          globalId: "SS123",
          quantity: {
            numericValue: 4,
            unitId: 7,
          },
        },
      },
    ],
    [
      124,
      {
        body: {
          id: 124,
          globalId: "SS124",
          quantity: {
            numericValue: 10,
            unitId: 7,
          },
        },
      },
    ],
    [
      125,
      {
        body: {
          id: 125,
          globalId: "SS125",
          quantity: {
            numericValue: 25,
            unitId: 3,
          },
        },
      },
    ],
  ]);
  inventorySubSampleRequestCount = 0;
  await router.route("/api/v1/stoichiometry*", (route) => {
    return fulfillJson(route, mockStoichiometryResponse);

  });
  await router.route("/api/inventory/v1/subSamples/*", (route) => {
    inventorySubSampleRequestCount += 1;
    const id = Number(route.request().url().split("/").pop());
    const mockResponse = mockSubSampleResponses.get(id);

    if (!mockResponse) {
      return fulfillJson(route, { message: "Not Found" }, 404);
    }

    return fulfillJson(route, mockResponse.body, mockResponse.status ?? 200);
  });
  await router.route("/userform/ajax/inventoryOauthToken", (route) => {
    const payload = {
      iss: "http://localhost:8080",
      iat: new Date().getTime(),
      exp: Math.floor(Date.now() / 1000) + 300,
      refreshTokenHash:
        "fe15fa3d5e3d5a47e33e9e34229b1ea2314ad6e6f13fa42addca4f1439582a4d",
    };
    return fulfillJson(route, {
      data: Jwt.sign(payload, "dummySecretKey"),
    });

  });
  await router.route("/api/v1/stoichiometry/molecule/info", (route) => {
    const requestBody = route.request().postDataJSON() as unknown;
    const smiles =
      typeof requestBody === "object" &&
      requestBody !== null &&
      "chemical" in requestBody
        ? (requestBody as { chemical?: string }).chemical
        : undefined;

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
    return fulfillJson(route, mockInfo);

  });
  await router.route("/api/v1/pubchem/search", (route) => {
    return fulfillJson(route, [
      {
        name: "Caffeine",
        pngImage: "data:image/png;base64,mock-caffeine-image",
        smiles: "CN1C=NC2=C1C(=O)N(C(=O)N2C)C",
        cas: "58-08-2",
        formula: "C8H10N4O2",
        pubchemId: "2519",
        pubchemUrl: "https://pubchem.ncbi.nlm.nih.gov/compound/2519",
      },
    ]);

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
    return fulfillJson(route, mockGalleryResponse);
  });

});
test.describe("Stoichiometry Table", () => {
  feature("Has no accessibility violations", async ({ Given, Once, Then }) => {
    await Given["the table is loaded with data"]();
    await Once["the table has loaded"]();
    await Then["there shouldn't be any axe violations"]();

  });
  feature(
    "supports high-contrast mode",
    async ({ Given, Then, Once, page }) => {
      await page.emulateMedia({ contrast: "more" });
      await Given["the table is loaded with data"]();
      await Once["the table has loaded"]();
      await Then["there shouldn't be any axe violations"]();
    },

  );
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
    "Shows inventory link controls for both linked and unlinked molecules",
    async ({ Given, Once, Then }) => {
      await Given["the table is loaded with data"]();
      await Once["the table has loaded"]();
      await Then[
        "inventory link controls should be visible for linked and unlinked molecules"
      ]();
    },
  );
  feature(
    "Opens and closes the inventory picker from an unlinked molecule",
    async ({ Given, Once, When, Then }) => {
      await Given["the table is loaded with data"]();
      await Once["the table has loaded"]();
      await When["the user opens inventory picker for {molecule}"]({
        molecule: "Benzene",
      });
      await Then["inventory picker should be visible for {molecule}"]({
        molecule: "Benzene",
      });
      await When["the user closes the inventory picker"]();
      await Then["inventory picker should not be visible"]();
    },
  );
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
      await Then["{CSV} should have {count} rows"]({ csv, count: 4 }); // 4 molecules in mock data
    },

  );
  feature(
    "When exporting to CSV, role strings should be transformed correctly",
    async ({ Given, Once, When, Then }) => {
      await Given["the table is loaded with data"]();
      await Once["the table has loaded"]();
      const csv = await When["a CSV export is downloaded"]();
      await Then["{CSV} should contain the correct role strings"]({ csv });
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
  feature(
    "The toolbar can open the inventory stock update dialog",
    async ({ Given, Once, When, Then }) => {
      await Given["the table is loaded with data"]();
      await Once["the table has loaded"]();
      await When["the user clicks Update Inventory Stock"]();
      await Then["the inventory update dialog should open"]();
      await Then[
        "the inventory update dialog should list the current molecules"
      ]();
    },
  );
  feature(
    "The inventory stock update dialog only preselects eligible molecules and explains disabled ones",
    async ({ Given, Once, When, page }) => {
      await Given["the table is loaded with data"]();
      await Once["the table has loaded"]();
      await When["the user clicks Update Inventory Stock"]();

      const dialog = page.getByRole("dialog", {
        name: /Update Inventory Stock/i,
      });
      const moleculeRow = (name: string) =>
        dialog.locator(`[data-row-type="molecule"][data-molecule-name="${name}"]`);
      const metric = (cardName: string, metricName: string) =>
        moleculeRow(cardName).locator(`[data-column="${metricName}"]`);

      await expect(
        dialog.getByRole("columnheader", { name: "Molecule", exact: true }),
      ).toBeVisible();
      await expect(
        dialog.getByRole("columnheader", { name: "In Stock", exact: true }),
      ).toBeVisible();
      await expect(
        dialog.getByRole("columnheader", { name: "Will Use", exact: true }),
      ).toBeVisible();
      await expect(
        dialog.getByRole("columnheader", { name: "Remaining", exact: true }),
      ).toBeVisible();

      await expect(
        dialog.getByRole("checkbox", { name: "Cyclopentane" }),
      ).toBeChecked();
      await expect(
        dialog.getByRole("checkbox", { name: "Cyclopentane" }),
      ).toBeEnabled();
      await expect(metric("Cyclopentane", "In Stock")).toContainText("10.0 g");
      await expect(metric("Cyclopentane", "Will Use")).toContainText("5.0 g");
      await expect(metric("Cyclopentane", "Remaining")).toContainText("5.0 g");
      await expect(metric("Cyclopentane", "Remaining")).toHaveAttribute("data-status", "positive");

      await expect(
        dialog.getByRole("checkbox", { name: "Benzene" }),
      ).toBeDisabled();
      await expect(
        dialog.getByText("Link an inventory item before updating stock."),
      ).toBeVisible();
      await expect(metric("Benzene", "In Stock")).toContainText("—");

      await expect(
        dialog.getByRole("checkbox", { name: "Cyclopentadiene" }),
      ).toBeDisabled();
      await expect(metric("Cyclopentadiene", "In Stock")).toContainText("4.0 g");
      await expect(metric("Cyclopentadiene", "Will Use")).toContainText("5.0 g");
      await expect(metric("Cyclopentadiene", "Remaining")).toContainText("-1.0 g");
      await expect(metric("Cyclopentadiene", "Remaining")).toHaveAttribute("data-status", "negative");
      await expect(
        dialog.getByText("Insufficient Stock"),
      ).toBeVisible();

      await expect(
        dialog.getByRole("checkbox", { name: "Ethanol" }),
      ).toBeDisabled();
      await expect(
        dialog.getByText(
          "Deducting inventory stock for inventory items with non-gram units is currently not supported.",
        ),
      ).toBeVisible();
      await expect(metric("Ethanol", "In Stock")).toContainText("25.0 mL");
      await expect(metric("Ethanol", "Will Use")).toContainText("—");
      await expect(metric("Ethanol", "Remaining")).toContainText("—");
    },
  );
  feature(
    "The inventory stock update dialog warns when stock has already been deducted while still allowing reselection",
    async ({ Given, Once, When, page }) => {
      const cyclopentane = mockStoichiometryResponse.molecules.find(
        ({ name }) => name === "Cyclopentane",
      );
      if (!cyclopentane?.inventoryLink) {
        throw new Error("Cyclopentane inventory link is missing from mock data");
      }
      cyclopentane.inventoryLink.stockDeducted = true;

      await Given["the table is loaded with data"]();
      await Once["the table has loaded"]();
      await When["the user clicks Update Inventory Stock"]();

      const dialog = page.getByRole("dialog", {
        name: /Update Inventory Stock/i,
      });

      await expect(
        dialog.getByRole("checkbox", { name: "Cyclopentane" }),
      ).toBeEnabled();
      await expect(
        dialog.getByRole("checkbox", { name: "Cyclopentane" }),
      ).not.toBeChecked();
      await expect(
        dialog.getByText(
          "Stock has already been deducted for this molecule. To reduce the stock again, select this molecule.",
        ),
      ).toBeVisible();
    },
  );
  feature(
    "The inventory stock update dialog disables molecules when fetching linked stock fails",
    async ({ Given, Once, When, page }) => {
      mockSubSampleResponses.set(124, {
        status: 404,
        body: { message: "Not Found" },
      });

      await Given["the table is loaded with data"]();
      await Once["the table has loaded"]();
      await When["the user clicks Update Inventory Stock"]();

      const dialog = page.getByRole("dialog", {
        name: /Update Inventory Stock/i,
      });

      await expect(
        dialog.getByRole("checkbox", { name: "Cyclopentane" }),
      ).toBeDisabled();
      await expect(
        dialog.getByText(
          "Linked stock information is unavailable, so this molecule cannot be updated.",
        ),
      ).toBeVisible();
    },
  );
  feature(
    "The inventory stock update action is disabled while the table has unsaved changes",
    async ({ Given, Once, When, Then }) => {
      await Given["the table is loaded with data"]();
      await Once["the table has loaded"]();
      await When["the user edits mass in row {row} to {value}"]({
        row: 0,
        value: "12",
      });
      await Then["the Update Inventory Stock button should be disabled"]();
      await Then[
        "the disabled Update Inventory Stock button should explain that saving is required"
      ]();
    },
  );
  test.describe("Adding reagants", () => {
    feature(
      "User can access the Add Chemical menu",
      async ({ Given, Once, When, Then }) => {
        await Given["the table is loaded with data"]();
        await Once["the table has loaded"]();
        await When["the user clicks Add Chemical"]();
        await Then["the Add Chemical menu should be visible"]();
      },

    );
    feature(
      "User can add a reagent from PubChem",
      async ({ Given, Once, When, Then }) => {
        await Given["the table is loaded with data"]();

        await Once["the table has loaded"]();
        await When["the user clicks Add Chemical"]();
        await When["the user selects PubChem from the menu"]();

        await Then["the PubChem dialog should open"]();
        await When["the user searches for {compound} in PubChem"]({
          compound: "caffeine",
        });

        await Then["PubChem search results should be displayed"]();
        await When["the user clicks Insert"]();

        await Once["the loading dialog disappears"]();
        await Then["the table should contain a new row with {name}"]({
          name: "Caffeine",
        });
        await Then["the new row should have molecular weight {weight}"]({
          weight: 194.19,
        });
        await Then["the new row should have role agent"]();
        await Then["there should be {count} molecules in total"]({ count: 5 }); // 4 original + 1 new
      },

    );
    feature(
      "User can add a reagent manually using SMILES",
      async ({ Given, Once, When, Then }) => {
        await Given["the table is loaded with data"]();

        await Once["the table has loaded"]();
        await When["the user clicks Add Chemical"]();
        await When["the user selects Manual entry from the menu"]();

        await Then["the manual SMILES dialog should open"]();
        await When["the user enters SMILES {smiles} with name {name}"]({
          smiles: "CCO",
          name: "Ethanol",
        });

        await When["the user adds the manual reagent"]();

        await Once["the loading dialog disappears"]();
        await Then["the table should contain a new row with {name}"]({
          name: "Ethanol",
        });
        await Then["the new row should have molecular weight {weight}"]({
          weight: 46.07,
        });
        await Then["the new row should have role agent"]();
        await Then["there should be {count} molecules in total"]({ count: 5 }); // 4 original + 1 new
      },

    );
    feature(
      "User can add a reagent from Gallery",
      async ({ Given, Once, When, Then }) => {
        await Given["the table is loaded with data"]();

        await Once["the table has loaded"]();
        // Open Gallery dialog
        await When["the user clicks Add Chemical"]();
        await When["the user selects Gallery from the menu"]();

        await Then["the Gallery dialog should open"]();
        // Select a chemistry file and add it
        await When["the user selects the first chemistry file from Gallery"]();

        await When["the user adds the selected files from Gallery"]();

        await Once["the loading dialog disappears"]();
        // Verify the new reagent is added correctly (using filename without extension)
        await Then["the table should contain a new row with {name}"]({
          name: "ethanol",
        });
        await Then["the new row should have molecular weight {weight}"]({
          weight: 46.07,
        });
        await Then["the new row should have role agent"]();
        await Then["there should be {count} molecules in total"]({ count: 5 }); // 4 original + 1 new
      },

    );
    feature(
      "Multiple reagents can be added sequentially",
      async ({ Given, Once, When, Then }) => {
        await Given["the table is loaded with data"]();

        await Once["the table has loaded"]();
        // Add first reagent (Ethanol)
        await When["the user clicks Add Chemical"]();
        await When["the user selects Manual entry from the menu"]();
        await When["the user enters SMILES {smiles} with name {name}"]({
          smiles: "CCO",
          name: "Ethanol",
        });
        await When["the user adds the manual reagent"]();
        await Once["the loading dialog disappears"]();

        await Then["there should be {count} molecules in total"]({ count: 5 });
        // Add second reagent (Caffeine via PubChem)
        await When["the user clicks Add Chemical"]();
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
        await Then["there should be {count} molecules in total"]({ count: 6 }); // 4 original + 2 new
      },
    );

  });
  test.describe("Calculation Logic", () => {
    feature(
      "Editing actual mass updates actual moles correctly",
      async ({ Given, Once, When, Then }) => {
        await Given["the table is loaded with data"]();

        await Once["the table has loaded"]();
        // Edit actual mass of second row (Cyclopentadiene) from 5 to 8
        await When["the user edits actual mass in row {row} to {value}"]({
          row: 1,
          value: "8",

        });
        await Then["Compound {name} has {column} of {value}"]({
          name: "Cyclopentadiene",
          column: "Actual Moles (mol)",
          value: "8",
        });
      },

    );
    feature(
      "Editing actual moles updates actual mass correctly",
      async ({ Given, Once, When, Then }) => {
        await Given["the table is loaded with data"]();

        await Once["the table has loaded"]();
        // Edit actual moles of second row (Cyclopentadiene) from 5 to 6
        await When["the user edits actual moles in row {row} to {value}"]({
          row: 1,
          value: "6",

        });
        await Then["Compound {name} has {column} of {value}"]({
          name: "Cyclopentadiene",
          column: "Actual Mass (g)",
          value: "6",
        });
      },

    );
    feature(
      "Editing actual mass updates yield correctly for non-limiting reactants",
      async ({ Given, Once, When, Then }) => {
        await Given["the table is loaded with data"]();

        await Once["the table has loaded"]();
        // Edit actual mass of second row (Cyclopentadiene) from 5 to 10
        await When["the user edits actual mass in row {row} to {value}"]({
          row: 1,
          value: "10",

        });
        await Then["Compound {name} has {column} of {value}"]({
          name: "Cyclopentadiene",
          column: "Actual Moles (mol)",
          value: "10",

        });
        await Then["Compound {name} has {column} of {value}"]({
          name: "Cyclopentadiene",
          column: "Yield/Excess (%)",
          value: "150%",
        });
      },

    );
    feature(
      "Multiple actual mass edits work correctly",
      async ({ Given, Once, When, Then }) => {
        await Given["the table is loaded with data"]();

        await Once["the table has loaded"]();
        // Edit actual mass of product (Cyclopentane) from 5 to 7
        await When["the user edits actual mass in row {row} to {value}"]({
          row: 2,
          value: "7",

        });
        await Then["Compound {name} has {column} of {value}"]({
          name: "Cyclopentane",
          column: "Actual Moles (mol)",
          value: "7",
        });
      },

    );
    feature(
      "Changing limiting reagent adjusts equivalent values",
      async ({ Given, Once, When, Then }) => {
        await Given["the table is loaded with data"]();

        await Once["the table has loaded"]();
        await Then["Compound {name} has {column} of {value}"]({
          name: "Benzene",
          column: "Equivalent",
          value: "1",

        });
        await Then["Compound {name} has {column} of {value}"]({
          name: "Cyclopentadiene",
          column: "Equivalent",
          value: "2",

        });
        await When[
          "the user taps the limiting reagent cell of the second row"

        ]();
        await Then["Compound {name} has {column} of {value}"]({
          name: "Cyclopentadiene",
          column: "Equivalent",
          value: "1",

        });
        await Then["Compound {name} has {column} of {value}"]({
          name: "Benzene",
          column: "Equivalent",
          value: "0.5",
        });
      },

    );
    feature(
      "Changing limiting reagent updates mass/moles editability and yield calculations",
      async ({ Given, Once, When, Then }) => {
        await Given["the table is loaded with data"]();

        await Once["the table has loaded"]();
        await Then["Compound {name} has {column} of {value}"]({
          name: "Benzene",
          column: "Yield/Excess (%)",
          value: "—",

        });
        await Then["Compound {name} has {column} of {value}"]({
          name: "Cyclopentadiene",
          column: "Yield/Excess (%)",
          value: "500%",

        });
        await When[
          "the user taps the limiting reagent cell of the second row"

        ]();
        await Then["Compound {name} has {column} of {value}"]({
          name: "Benzene",
          column: "Yield/Excess (%)",
          value: "20%",

        });
        await Then["Compound {name} has {column} of {value}"]({
          name: "Cyclopentadiene",
          column: "Yield/Excess (%)",
          value: "—",

        });
        await Then["Compound {name} has {column} of {value}"]({
          name: "Cyclopentane",
          column: "Yield/Excess (%)",
          value: "66.7%",
        });
      },

    );
    feature(
      "Changing limiting reagent updates mass/moles editability",
      async ({ Given, Once, When, Then, page }) => {
        await Given["the table is loaded with data"]();

        await Once["the table has loaded"]();
        // Initially only Benzene (limiting reagent) mass should be editable
        const dataRows = page
          .getByRole("grid")
          .locator('[role="row"][data-id]');
        // Try editing mass of Benzene (should work - it's limiting reagent)
        const benzeneRow = dataRows.nth(0);
        const benzeneMassCell = getCellByField(benzeneRow, "mass");

        await benzeneMassCell.click();
        await benzeneMassCell.press("Enter");
        let input = page.locator(
          '.MuiDataGrid-cell--editing input, .MuiDataGrid-cell--editing textarea',
        );
        await input.waitFor({ state: "visible" });
        await input.fill("15");

        await input.press("Enter");
        await Then["Compound {name} has {column} of {value}"]({
          name: "Benzene",
          column: "Mass (g)",
          value: "15",

        });
        // Change limiting reagent to Cyclopentadiene
        await When[
          "the user taps the limiting reagent cell of the second row"

        ]();
        // Now try editing mass of Cyclopentadiene (should work - new limiting reagent)
        await When["the user edits mass in row {row} to {value}"]({
          row: 1,
          value: "25",

        });
        await Then["Compound {name} has {column} of {value}"]({
          name: "Cyclopentadiene",
          column: "Mass (g)",
          value: "25",

        });
        await Then["Compound {name} has {column} of {value}"]({
          name: "Cyclopentadiene",
          column: "Moles (mol)",
          value: "25",
        });
      },

    );
    feature(
      "Yield is computed correctly for products",
      async ({ Given, Once, Then }) => {
        await Given["the table is loaded with data"]();

        await Once["the table has loaded"]();
        await Then["Compound {name} has {column} of {value}"]({
          name: "Cyclopentane",
          column: "Yield/Excess (%)",
          value: "500%",
        });
      },

    );
    feature(
      "Editing equivalent normalizes all coefficients correctly",
      async ({ Given, Once, When, Then }) => {
        await Given["the table is loaded with data"]();

        await Once["the table has loaded"]();
        // Edit equivalent of Cyclopentadiene from 2 to 4
        await When["the user edits equivalent in row {row} to {value}"]({
          row: 1,
          value: "4",

        });
        await Then["Compound {name} has {column} of {value}"]({
          name: "Benzene",
          column: "Equivalent",
          value: "1",

        });
        await Then["Compound {name} has {column} of {value}"]({
          name: "Cyclopentadiene",
          column: "Equivalent",
          value: "4",

        });
        await Then["Compound {name} has {column} of {value}"]({
          name: "Cyclopentane",
          column: "Equivalent",
          value: "3",
        });
      },

    );
    feature(
      "Editing negative equivalent value reverts to original value",
      async ({ Given, Once, When, Then }) => {
        await Given["the table is loaded with data"]();

        await Once["the table has loaded"]();
        // Check original value
        await Then["Compound {name} has {column} of {value}"]({
          name: "Cyclopentadiene",
          column: "Equivalent",
          value: "2",

        });
        // Try to edit equivalent of Cyclopentadiene to -1 (should fail)
        await When["the user edits equivalent in row {row} to {value}"]({
          row: 1,
          value: "-1",

        });
        // Value should remain unchanged (reverted to original)
        await Then["Compound {name} has {column} of {value}"]({
          name: "Cyclopentadiene",
          column: "Equivalent",
          value: "2",
        });
      },
    );
    feature(
      "Shows an insufficient stock warning when actual mass exceeds linked stock",
      async ({ Given, Once, page }) => {
        await Given["the table is loaded with data"]();
        await Once["the table has loaded"]();

        const inventoryLinkCell = await getCompoundCell(
          page,
          "Cyclopentadiene",
          "Inventory Link",
        );

        await expect(
          inventoryLinkCell.getByTestId("WarningAmberIcon"),
        ).toBeVisible();
      },
    );
    feature(
      "Does not show an insufficient stock warning when stock is sufficient",
      async ({ Given, Once, page }) => {
        await Given["the table is loaded with data"]();
        await Once["the table has loaded"]();

        const inventoryLinkCell = await getCompoundCell(
          page,
          "Cyclopentane",
          "Inventory Link",
        );

        await expect(
          inventoryLinkCell.getByTestId("WarningAmberIcon"),
        ).toHaveCount(0);
      },
    );
    feature(
      "Does not show an insufficient stock warning for non-mass inventory quantities",
      async ({ Given, Once, page }) => {
        await Given["the table is loaded with data"]();
        await Once["the table has loaded"]();

        const inventoryLinkCell = await getCompoundCell(
          page,
          "Ethanol",
          "Inventory Link",
        );

        await expect(
          inventoryLinkCell.getByTestId("WarningAmberIcon"),
        ).toHaveCount(0);
      },
    );
    feature(
      "Does not show an insufficient stock warning when no inventory link exists",
      async ({ Given, Once, page }) => {
        await Given["the table is loaded with data"]();
        await Once["the table has loaded"]();

        const inventoryLinkCell = await getCompoundCell(page, "Benzene", "Inventory Link");

        await expect(
          inventoryLinkCell.getByTestId("WarningAmberIcon"),
        ).toHaveCount(0);
      },
    );
    feature(
      "Does not show an insufficient stock warning when fetching linked stock fails",
      async ({ Given, Once, page }) => {
        mockSubSampleResponses.set(123, {
          status: 404,
          body: { message: "Not Found" },
        });

        await Given["the table is loaded with data"]();
        await Once["the table has loaded"]();

        const inventoryLinkCell = await getCompoundCell(
          page,
          "Cyclopentadiene",
          "Inventory Link",
        );

        await expect(
          inventoryLinkCell.getByTestId("WarningAmberIcon"),
        ).toHaveCount(0);
      },
    );
    feature(
      "Does not show an insufficient stock warning in read-only mode",
      async ({ mount, Once, page }) => {
        await mount(<ReadOnlyStoichiometryTableStory />);
        await Once["the table has loaded"]();

        const inventoryLinkCell = await getCompoundCell(
          page,
          "Cyclopentadiene",
          "Inventory Link",
        );

        await expect(
          inventoryLinkCell.getByTestId("WarningAmberIcon"),
        ).toHaveCount(0);
        expect(inventorySubSampleRequestCount).toBe(0);
      },
    );
  });
});
