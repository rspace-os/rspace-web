import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { page } from "vitest/browser";
import { worker } from "@/__tests__/browserSetup";
import { emulateHighContrast, expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import i18n from "@/modules/common/i18n";
import {
  createDefaultSubSampleResponses,
  createMockStoichiometryResponse,
  galleryFilesHandler,
  galleryPickerSupportHandlers,
  type MockRouteResponse,
  moleculeInfoHandler,
  oauthTokenHandler,
  pubchemSearchHandler,
} from "./mocks/stoichiometryMocks";
import { InventoryUpdateDialogPage, StoichiometryTablePage } from "./pageObjects/StoichiometryTablePage";
import { ReadOnlyStoichiometryTableStory, StoichiometryTableWithDataStory } from "./StoichiometryTable.story";

type StoichiometryMocks = {
  response: ReturnType<typeof createMockStoichiometryResponse>;
  subSampleResponses: Map<number, MockRouteResponse>;
  inventorySubSampleRequestCount: number;
};

let mocks: StoichiometryMocks;

const table = new StoichiometryTablePage();
const inventoryUpdateDialog = new InventoryUpdateDialogPage();
const tableHeaders = [
  "common:stoichiometry.table.columns.name",
  "common:stoichiometry.table.columns.inventoryLink",
  "common:stoichiometry.table.columns.type",
  "common:stoichiometry.table.columns.limitingReagent",
  "common:stoichiometry.table.columns.equivalent",
  "common:stoichiometry.table.columns.molecularWeight",
  "common:stoichiometry.table.columns.mass",
  "common:stoichiometry.table.columns.moles",
  "common:stoichiometry.table.columns.notes",
] as const;
const inventoryUpdateHeaders = {
  molecule: "common:stoichiometry.inventoryUpdate.molecule",
  inStock: "common:stoichiometry.inventoryUpdate.inStock",
  willUse: "common:stoichiometry.inventoryUpdate.willUse",
  remaining: "common:stoichiometry.inventoryUpdate.remaining",
};
const columns = {
  actualMass: "common:stoichiometry.table.columns.actualMass",
  actualMoles: "common:stoichiometry.table.columns.actualMoles",
  equivalent: "common:stoichiometry.table.columns.equivalent",
  mass: "common:stoichiometry.table.columns.mass",
  moles: "common:stoichiometry.table.columns.moles",
  yieldExcess: "common:stoichiometry.table.columns.yieldExcess",
};

beforeEach(async () => {
  i18n.options.appendNamespaceToCIMode = true;
  await i18n.changeLanguage("cimode");
  mocks = {
    response: createMockStoichiometryResponse(),
    subSampleResponses: createDefaultSubSampleResponses(),
    inventorySubSampleRequestCount: 0,
  };
  worker.use(
    oauthTokenHandler(),
    http.get("/api/v1/stoichiometry", () => HttpResponse.json(mocks.response)),
    http.get("/api/inventory/v1/subSamples/:id", ({ params }) => {
      mocks.inventorySubSampleRequestCount += 1;
      const id = Number(params.id);
      const mockResponse = mocks.subSampleResponses.get(id);
      if (!mockResponse) {
        return HttpResponse.json({ message: "Not Found" }, { status: 404 });
      }
      return HttpResponse.json(mockResponse.body as Record<string, unknown>, {
        status: mockResponse.status ?? 200,
      });
    }),
    moleculeInfoHandler(),
    pubchemSearchHandler(),
    galleryFilesHandler(),
    ...galleryPickerSupportHandlers(),
  );
});

afterEach(() => {
  cleanup();
});

describe("Stoichiometry Table", () => {
  test("Has no accessibility violations", async () => {
    render(<StoichiometryTableWithDataStory />);
    await table.waitForLoad();
    await expectNoAxeViolations();
  });

  test("supports high-contrast mode", async () => {
    await emulateHighContrast();
    render(<StoichiometryTableWithDataStory />);
    await table.waitForLoad();
    await expectNoAxeViolations();
  });

  test("Renders and displays data correctly", async () => {
    render(<StoichiometryTableWithDataStory />);
    await table.waitForLoad();
    await expect.element(table.table).toBeVisible();
    // At least header + 1 data row.
    await expect.poll(() => table.table.getByRole("row").elements().length).toBeGreaterThan(1);
  });

  test("Displays expected column headers", async () => {
    render(<StoichiometryTableWithDataStory />);
    await table.waitForLoad();
    const headers = table.table
      .getByRole("columnheader")
      .elements()
      .map((el) => el.textContent ?? "");
    for (const header of tableHeaders) {
      expect(headers).toContain(header);
    }
  });

  test("Shows inventory link controls for both linked and unlinked molecules", async () => {
    render(<StoichiometryTableWithDataStory />);
    await table.waitForLoad();
    await expect.element(table.removeInventoryLinkButton("Cyclopentadiene")).toBeVisible();
    await expect.element(table.addInventoryLinkButton("Benzene")).toBeVisible();
  });

  test("Opens and closes the inventory picker from an unlinked molecule", async () => {
    render(<StoichiometryTableWithDataStory />);
    await table.waitForLoad();
    await table.openInventoryPickerFor("Benzene");
    await expect.element(table.inventoryPickerDialog("Benzene")).toBeVisible();
    await table.closeInventoryPicker();
    await expect.element(table.inventoryPickerDialog()).not.toBeInTheDocument();
  });

  test("Sets first reactant as default limiting reagent when none is selected", async () => {
    render(<StoichiometryTableWithDataStory />);
    await table.waitForLoad();
    await expect.element(table.dataRows().first()).toHaveTextContent("Benzene");
    await expect.element(table.limitingReagentRadio("Benzene")).toBeChecked();
  });

  test("There should be a menu for exporting the stoichiometry table to CSV", async () => {
    render(<StoichiometryTableWithDataStory />);
    await table.openExportMenu();
    // Opening the menu marks the rest of the page aria-hidden, so assert on the
    // menu item that appears rather than the (now hidden) Export button.
    await expect
      .element(page.getByRole("menuitem", { name: "common:stoichiometry.tableToolbar.exportToCsv" }))
      .toBeVisible();
  });

  test("When exporting to CSV, all molecule rows should be included", async () => {
    render(<StoichiometryTableWithDataStory />);
    await table.waitForLoad();
    const csv = await table.exportToCsv();
    const lines = csv.split("\n");
    expect(lines.length).toBe(4 + 1); // 4 molecules + header row
  });

  test("When exporting to CSV, role strings should be transformed correctly", async () => {
    render(<StoichiometryTableWithDataStory />);
    await table.waitForLoad();
    const csv = await table.exportToCsv();
    expect(csv).toContain("common:stoichiometry.table.roles.reactant");
    expect(csv).toContain("common:stoichiometry.table.roles.product");
    expect(csv).toContain("common:stoichiometry.table.roles.reagent");
    expect(csv).not.toContain("REACTANT");
    expect(csv).not.toContain("PRODUCT");
    expect(csv).not.toContain("AGENT");
  });

  test("When exporting to CSV, inventory links should be exported using their global IDs", async () => {
    render(<StoichiometryTableWithDataStory />);
    await table.waitForLoad();
    const csv = await table.exportToCsv();
    expect(csv).toContain("SS123");
    expect(csv).toContain("SS124");
    expect(csv).toContain("SS125");
  });

  test("Given the first row is the limiting reagent, the first row has no yield and the second row does", async () => {
    render(<StoichiometryTableWithDataStory />);
    await table.waitForLoad();
    await expect.element(table.yieldCell(0)).toHaveTextContent("—");
    await expect.element(table.yieldCell(1)).toHaveTextContent(/—|\d+%/);
  });

  test("Changing the limiting reagent to the second row swaps which rows have a yield", async () => {
    render(<StoichiometryTableWithDataStory />);
    await table.waitForLoad();
    await expect.element(table.yieldCell(0)).toHaveTextContent("—");
    await expect.element(table.yieldCell(1)).toHaveTextContent(/—|\d+%/);
    await table.selectLimitingReagent("Cyclopentadiene");
    await expect.element(table.yieldCell(0)).toHaveTextContent(/—|\d+%/);
    await expect.element(table.yieldCell(1)).toHaveTextContent("—");
  });

  test("The toolbar can open the inventory stock update dialog", async () => {
    render(<StoichiometryTableWithDataStory />);
    await table.waitForLoad();
    await table.clickUpdateInventoryStock();
    await expect.element(inventoryUpdateDialog.dialog).toBeVisible();
    for (const name of ["Benzene", "Cyclopentadiene", "Cyclopentane", "Ethanol"]) {
      await expect.element(inventoryUpdateDialog.dialog).toHaveTextContent(name);
    }
  });

  test("The inventory stock update dialog only preselects eligible molecules and explains disabled ones", async () => {
    render(<StoichiometryTableWithDataStory />);
    await table.waitForLoad();
    await table.clickUpdateInventoryStock();

    const dialog = inventoryUpdateDialog.dialog;
    const metric = (cardName: string, metricName: string) => inventoryUpdateDialog.metric(cardName, metricName);

    await expect.element(inventoryUpdateDialog.columnHeader(inventoryUpdateHeaders.molecule)).toBeVisible();
    await expect.element(inventoryUpdateDialog.columnHeader(inventoryUpdateHeaders.inStock)).toBeVisible();
    await expect.element(inventoryUpdateDialog.columnHeader(inventoryUpdateHeaders.willUse)).toBeVisible();
    await expect.element(inventoryUpdateDialog.columnHeader(inventoryUpdateHeaders.remaining)).toBeVisible();

    await expect.element(inventoryUpdateDialog.checkbox("Cyclopentane")).toBeChecked();
    await expect.element(inventoryUpdateDialog.checkbox("Cyclopentane")).toBeEnabled();
    await expect.element(metric("Cyclopentane", inventoryUpdateHeaders.inStock)).toHaveTextContent("10.0 g");
    await expect.element(metric("Cyclopentane", inventoryUpdateHeaders.willUse)).toHaveTextContent("5.0 g");
    await expect.element(metric("Cyclopentane", inventoryUpdateHeaders.remaining)).toHaveTextContent("5.0 g");
    await expect
      .element(metric("Cyclopentane", inventoryUpdateHeaders.remaining))
      .toHaveAttribute("data-status", "positive");

    await expect.element(inventoryUpdateDialog.checkbox("Benzene")).toBeDisabled();
    await expect.element(dialog.getByText("common:stoichiometry.inventoryUpdate.linkRequired")).toBeVisible();
    await expect.element(metric("Benzene", inventoryUpdateHeaders.inStock)).toHaveTextContent("—");

    await expect.element(inventoryUpdateDialog.checkbox("Cyclopentadiene")).toBeDisabled();
    await expect.element(metric("Cyclopentadiene", inventoryUpdateHeaders.inStock)).toHaveTextContent("4.0 g");
    await expect.element(metric("Cyclopentadiene", inventoryUpdateHeaders.willUse)).toHaveTextContent("5.0 g");
    await expect.element(metric("Cyclopentadiene", inventoryUpdateHeaders.remaining)).toHaveTextContent("-1.0 g");
    await expect
      .element(metric("Cyclopentadiene", inventoryUpdateHeaders.remaining))
      .toHaveAttribute("data-status", "negative");
    await expect.element(dialog.getByText("common:stoichiometry.inventoryLink.insufficientStock")).toBeVisible();

    await expect.element(inventoryUpdateDialog.checkbox("Ethanol")).toBeDisabled();
    await expect
      .element(dialog.getByText("common:stoichiometry.inventoryUpdate.nonMassInventoryQuantity"))
      .toBeVisible();
    await expect.element(metric("Ethanol", inventoryUpdateHeaders.inStock)).toHaveTextContent("25.0 mL");
    await expect.element(metric("Ethanol", inventoryUpdateHeaders.willUse)).toHaveTextContent("—");
    await expect.element(metric("Ethanol", inventoryUpdateHeaders.remaining)).toHaveTextContent("—");
  });

  test("The inventory stock update dialog warns when stock has already been deducted while still allowing reselection", async () => {
    const cyclopentane = mocks.response.molecules.find(({ name }) => name === "Cyclopentane");
    if (!cyclopentane?.inventoryLink) {
      throw new Error("Cyclopentane inventory link is missing from mock data");
    }
    cyclopentane.inventoryLink.stockDeducted = true;

    render(<StoichiometryTableWithDataStory />);
    await table.waitForLoad();
    await table.clickUpdateInventoryStock();

    await expect.element(inventoryUpdateDialog.checkbox("Cyclopentane")).toBeEnabled();
    await expect.element(inventoryUpdateDialog.checkbox("Cyclopentane")).not.toBeChecked();
    await expect
      .element(inventoryUpdateDialog.dialog.getByText("common:stoichiometry.inventoryUpdate.stockDeductedWarning"))
      .toBeVisible();
  });

  test("The inventory stock update dialog disables molecules when fetching linked stock fails", async () => {
    mocks.subSampleResponses.set(124, {
      status: 404,
      body: { message: "Not Found" },
    });

    render(<StoichiometryTableWithDataStory />);
    await table.waitForLoad();
    await table.clickUpdateInventoryStock();

    await expect.element(inventoryUpdateDialog.checkbox("Cyclopentane")).toBeDisabled();
    await expect
      .element(inventoryUpdateDialog.dialog.getByText("common:stoichiometry.inventoryUpdate.linkedStockUnavailable"))
      .toBeVisible();
  });

  test("The inventory stock update action is disabled while the table has unsaved changes", async () => {
    render(<StoichiometryTableWithDataStory />);
    await table.waitForLoad();
    await table.editCell({ row: 0, column: "common:stoichiometry.table.columns.mass", value: "12" });
    await expect.element(table.updateInventoryStockButton).toBeDisabled();
    await expect.element(page.getByLabelText("common:stoichiometry.inventoryUpdate.saveBeforeUpdate")).toBeVisible();
  });

  describe("Adding reagants", () => {
    test("User can access the Add Chemical menu", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      await table.openAddChemicalMenu();
      await expect.element(table.pubChemMenuItem).toBeVisible();
      await expect.element(table.galleryMenuItem).toBeVisible();
      await expect.element(table.manualEntryMenuItem).toBeVisible();
    });

    test("User can add a reagent from PubChem", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      await table.openAddChemicalMenu();
      table.openPubChemSource();

      await expect.element(table.pubChemDialog).toBeVisible();
      await table.searchPubChem("caffeine");

      await expect.element(page.getByText("Caffeine")).toBeVisible();
      await expect.element(page.getByText("C8H10N4O2")).toBeVisible();
      await table.insertPubChemResult();

      await expect.element(table.moleculeInfoLoadingDialog).not.toBeInTheDocument();
      await expect.element(table.table).toHaveTextContent("Caffeine");
      await expect.element(table.table).toHaveTextContent("194.19");
      await expect.element(table.table).toHaveTextContent("common:stoichiometry.table.roles.reagent");
      await expect.poll(() => table.dataRows().elements().length).toBe(5);
    });

    test("User can add a reagent manually using SMILES", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      await table.openAddChemicalMenu();
      table.openManualSource();

      await expect.element(table.manualSmilesDialog).toBeVisible();
      await table.enterManualSmiles({ smiles: "CCO", name: "Ethanol" });
      await table.addManualReagent();

      await expect.element(table.moleculeInfoLoadingDialog).not.toBeInTheDocument();
      await expect.element(table.table).toHaveTextContent("Ethanol");
      await expect.element(table.table).toHaveTextContent("46.07");
      await expect.element(table.table).toHaveTextContent("common:stoichiometry.table.roles.reagent");
      await expect.poll(() => table.dataRows().elements().length).toBe(5);
    });

    test("User can add a reagent from Gallery", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      await table.openAddChemicalMenu();
      table.openGallerySource();

      await expect.element(table.galleryDialog).toBeVisible();
      await table.selectGalleryFile(/ethanol\.mol/i);
      await table.addSelectedGalleryFiles();

      await expect.element(table.moleculeInfoLoadingDialog).not.toBeInTheDocument();
      await expect.element(table.table).toHaveTextContent("ethanol");
      await expect.element(table.table).toHaveTextContent("46.07");
      await expect.element(table.table).toHaveTextContent("common:stoichiometry.table.roles.reagent");
      await expect.poll(() => table.dataRows().elements().length).toBe(5);
    });

    test("Multiple reagents can be added sequentially", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      // Add first reagent (Ethanol) manually.
      await table.openAddChemicalMenu();
      table.openManualSource();
      await table.enterManualSmiles({ smiles: "CCO", name: "Ethanol" });
      await table.addManualReagent();
      await expect.element(table.moleculeInfoLoadingDialog).not.toBeInTheDocument();
      await expect.poll(() => table.dataRows().elements().length).toBe(5);

      // Add second reagent (Caffeine via PubChem).
      await table.openAddChemicalMenu();
      table.openPubChemSource();
      await table.searchPubChem("caffeine");
      await table.insertPubChemResult();

      await expect.element(table.moleculeInfoLoadingDialog).not.toBeInTheDocument();
      await expect.element(table.table).toHaveTextContent("Ethanol");
      await expect.element(table.table).toHaveTextContent("Caffeine");
      await expect.poll(() => table.dataRows().elements().length).toBe(6);
    });
  });

  describe("Calculation Logic", () => {
    test("Editing actual mass updates actual moles correctly", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      await table.editCell({ row: 1, column: columns.actualMass, value: "8" });
      await expect.element(table.cell("Cyclopentadiene", columns.actualMoles)).toHaveTextContent("8");
    });

    test("Editing actual moles updates actual mass correctly", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      await table.editCell({ row: 1, column: columns.actualMoles, value: "6" });
      await expect.element(table.cell("Cyclopentadiene", columns.actualMass)).toHaveTextContent("6");
    });

    test("Editing actual mass updates yield correctly for non-limiting reactants", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      await table.editCell({ row: 1, column: columns.actualMass, value: "10" });
      await expect.element(table.cell("Cyclopentadiene", columns.actualMoles)).toHaveTextContent("10");
      await expect.element(table.cell("Cyclopentadiene", columns.yieldExcess)).toHaveTextContent("150%");
    });

    test("Multiple actual mass edits work correctly", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      await table.editCell({ row: 2, column: columns.actualMass, value: "7" });
      await expect.element(table.cell("Cyclopentane", columns.actualMoles)).toHaveTextContent("7");
    });

    test("Changing limiting reagent adjusts equivalent values", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      await expect.element(table.cell("Benzene", columns.equivalent)).toHaveTextContent("1");
      await expect.element(table.cell("Cyclopentadiene", columns.equivalent)).toHaveTextContent("2");
      await table.selectLimitingReagent("Cyclopentadiene");
      await expect.element(table.cell("Cyclopentadiene", columns.equivalent)).toHaveTextContent("1");
      await expect.element(table.cell("Benzene", columns.equivalent)).toHaveTextContent("0.5");
    });

    test("Changing limiting reagent updates yield calculations", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      await expect.element(table.cell("Benzene", columns.yieldExcess)).toHaveTextContent("—");
      await expect.element(table.cell("Cyclopentadiene", columns.yieldExcess)).toHaveTextContent("500%");
      await table.selectLimitingReagent("Cyclopentadiene");
      await expect.element(table.cell("Benzene", columns.yieldExcess)).toHaveTextContent("20%");
      await expect.element(table.cell("Cyclopentadiene", columns.yieldExcess)).toHaveTextContent("—");
      await expect.element(table.cell("Cyclopentane", columns.yieldExcess)).toHaveTextContent("66.7%");
    });

    test("Changing limiting reagent updates mass/moles editability", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      // Benzene is the limiting reagent, so its mass is editable.
      await table.editCell({ row: 0, column: columns.mass, value: "15" });
      await expect.element(table.cell("Benzene", columns.mass)).toHaveTextContent("15");
      // Switch the limiting reagent to Cyclopentadiene and edit its mass.
      await table.selectLimitingReagent("Cyclopentadiene");
      await table.editCell({ row: 1, column: columns.mass, value: "25" });
      await expect.element(table.cell("Cyclopentadiene", columns.mass)).toHaveTextContent("25");
      await expect.element(table.cell("Cyclopentadiene", columns.moles)).toHaveTextContent("25");
    });

    test("Yield is computed correctly for products", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      await expect.element(table.cell("Cyclopentane", columns.yieldExcess)).toHaveTextContent("500%");
    });

    test("Editing equivalent normalizes all coefficients correctly", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      await table.editCell({ row: 1, column: columns.equivalent, value: "4" });
      await expect.element(table.cell("Benzene", columns.equivalent)).toHaveTextContent("1");
      await expect.element(table.cell("Cyclopentadiene", columns.equivalent)).toHaveTextContent("4");
      await expect.element(table.cell("Cyclopentane", columns.equivalent)).toHaveTextContent("3");
    });

    test("Editing negative equivalent value reverts to original value", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      await expect.element(table.cell("Cyclopentadiene", columns.equivalent)).toHaveTextContent("2");
      await table.editCell({ row: 1, column: columns.equivalent, value: "-1" });
      await expect.element(table.cell("Cyclopentadiene", columns.equivalent)).toHaveTextContent("2");
    });

    test("Shows an insufficient stock warning when actual mass exceeds linked stock", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      await expect.element(table.insufficientStockIcon(table.inventoryLinkCell("Cyclopentadiene"))).toBeVisible();
    });

    test("Does not show an insufficient stock warning when stock is sufficient", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      await expect
        .element(table.insufficientStockIcon(table.inventoryLinkCell("Cyclopentane")))
        .not.toBeInTheDocument();
    });

    test("Does not show an insufficient stock warning for non-mass inventory quantities", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      await expect.element(table.insufficientStockIcon(table.inventoryLinkCell("Ethanol"))).not.toBeInTheDocument();
    });

    test("Does not show an insufficient stock warning when no inventory link exists", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      await expect.element(table.insufficientStockIcon(table.inventoryLinkCell("Benzene"))).not.toBeInTheDocument();
    });

    test("Does not show an insufficient stock warning when fetching linked stock fails", async () => {
      mocks.subSampleResponses.set(123, {
        status: 404,
        body: { message: "Not Found" },
      });

      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      await expect
        .element(table.insufficientStockIcon(table.inventoryLinkCell("Cyclopentadiene")))
        .not.toBeInTheDocument();
    });

    test("Does not show an insufficient stock warning in read-only mode", async () => {
      // Vitest browser mode shares one page across tests, and MSW intercepts
      // requests asynchronously, so a previous test's subsample requests can
      // land just after this test's beforeEach. Let those stragglers drain,
      // then zero the counter so we measure only the read-only render (which
      // must make no inventory-stock requests).
      await new Promise((resolve) => setTimeout(resolve, 100));
      mocks.inventorySubSampleRequestCount = 0;

      render(<ReadOnlyStoichiometryTableStory />);
      await table.waitForLoad();
      await expect
        .element(table.insufficientStockIcon(table.inventoryLinkCell("Cyclopentadiene")))
        .not.toBeInTheDocument();
      expect(mocks.inventorySubSampleRequestCount).toBe(0);
    });
  });
});
