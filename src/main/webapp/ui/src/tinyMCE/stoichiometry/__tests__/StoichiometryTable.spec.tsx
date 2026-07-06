import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { page } from "vitest/browser";
import { worker } from "@/__tests__/browserSetup";
import { emulateHighContrast, expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import {
  createDefaultSubSampleResponses,
  createMockStoichiometryResponse,
  galleryFilesHandler,
  galleryPickerSupportHandlers,
  type MockRouteResponse,
  moleculeInfoHandler,
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

beforeEach(() => {
  mocks = {
    response: createMockStoichiometryResponse(),
    subSampleResponses: createDefaultSubSampleResponses(),
    inventorySubSampleRequestCount: 0,
  };
  worker.use(
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
    for (const header of [
      "Name",
      "Inventory Link",
      "Type",
      "Limiting Reagent",
      "Equivalent",
      "Molecular Weight (g/mol)",
      "Mass (g)",
      "Moles (mol)",
      "Notes",
    ]) {
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
    await expect.element(page.getByRole("menuitem", { name: /Export to CSV/ })).toBeVisible();
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
    expect(csv).toContain("Reactant");
    expect(csv).toContain("Product");
    expect(csv).toContain("Reagent");
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

    await expect.element(inventoryUpdateDialog.columnHeader("Molecule")).toBeVisible();
    await expect.element(inventoryUpdateDialog.columnHeader("In Stock")).toBeVisible();
    await expect.element(inventoryUpdateDialog.columnHeader("Will Use")).toBeVisible();
    await expect.element(inventoryUpdateDialog.columnHeader("Remaining")).toBeVisible();

    await expect.element(inventoryUpdateDialog.checkbox("Cyclopentane")).toBeChecked();
    await expect.element(inventoryUpdateDialog.checkbox("Cyclopentane")).toBeEnabled();
    await expect.element(metric("Cyclopentane", "In Stock")).toHaveTextContent("10.0 g");
    await expect.element(metric("Cyclopentane", "Will Use")).toHaveTextContent("5.0 g");
    await expect.element(metric("Cyclopentane", "Remaining")).toHaveTextContent("5.0 g");
    await expect.element(metric("Cyclopentane", "Remaining")).toHaveAttribute("data-status", "positive");

    await expect.element(inventoryUpdateDialog.checkbox("Benzene")).toBeDisabled();
    await expect.element(dialog.getByText("Link an inventory item before updating stock.")).toBeVisible();
    await expect.element(metric("Benzene", "In Stock")).toHaveTextContent("—");

    await expect.element(inventoryUpdateDialog.checkbox("Cyclopentadiene")).toBeDisabled();
    await expect.element(metric("Cyclopentadiene", "In Stock")).toHaveTextContent("4.0 g");
    await expect.element(metric("Cyclopentadiene", "Will Use")).toHaveTextContent("5.0 g");
    await expect.element(metric("Cyclopentadiene", "Remaining")).toHaveTextContent("-1.0 g");
    await expect.element(metric("Cyclopentadiene", "Remaining")).toHaveAttribute("data-status", "negative");
    await expect.element(dialog.getByText("Insufficient Stock")).toBeVisible();

    await expect.element(inventoryUpdateDialog.checkbox("Ethanol")).toBeDisabled();
    await expect
      .element(
        dialog.getByText(
          "Inventory stock updates are currently only supported for item quantities expressed in mass (e.g. grams). Volumetric quantities (e.g. mL) are not yet supported.",
        ),
      )
      .toBeVisible();
    await expect.element(metric("Ethanol", "In Stock")).toHaveTextContent("25.0 mL");
    await expect.element(metric("Ethanol", "Will Use")).toHaveTextContent("—");
    await expect.element(metric("Ethanol", "Remaining")).toHaveTextContent("—");
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
      .element(
        inventoryUpdateDialog.dialog.getByText(
          "Stock has already been deducted for this molecule. To reduce the stock again, select this molecule.",
        ),
      )
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
      .element(
        inventoryUpdateDialog.dialog.getByText(
          "Linked stock information is unavailable, so this molecule cannot be updated.",
        ),
      )
      .toBeVisible();
  });

  test("The inventory stock update action is disabled while the table has unsaved changes", async () => {
    render(<StoichiometryTableWithDataStory />);
    await table.waitForLoad();
    await table.editCell({ row: 0, column: "Mass (g)", value: "12" });
    await expect.element(table.updateInventoryStockButton).toBeDisabled();
    // MUI wraps a disabled button in a tooltip span that intercepts pointer
    // events, so hover the wrapper (whose aria-label carries the message)
    // rather than the button itself.
    await page.getByLabelText("Save the stoichiometry table before updating inventory stock.").hover();
    await expect
      .element(page.getByRole("tooltip"))
      .toHaveTextContent("Save the stoichiometry table before updating inventory stock.");
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
      await table.pubChemMenuItem.click();

      await expect.element(table.pubChemDialog).toBeVisible();
      await table.searchPubChem("caffeine");

      await expect.element(page.getByText("Caffeine")).toBeVisible();
      await expect.element(page.getByText("C8H10N4O2")).toBeVisible();
      await table.insertPubChemResult();

      await expect.element(table.moleculeInfoLoadingDialog).not.toBeInTheDocument();
      await expect.element(table.table).toHaveTextContent("Caffeine");
      await expect.element(table.table).toHaveTextContent("194.19");
      await expect.element(table.table).toHaveTextContent("agent");
      await expect.poll(() => table.dataRows().elements().length).toBe(5);
    });

    test("User can add a reagent manually using SMILES", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      await table.openAddChemicalMenu();
      await table.manualEntryMenuItem.click();

      await expect.element(table.manualSmilesDialog).toBeVisible();
      await table.enterManualSmiles({ smiles: "CCO", name: "Ethanol" });
      await table.addManualReagent();

      await expect.element(table.moleculeInfoLoadingDialog).not.toBeInTheDocument();
      await expect.element(table.table).toHaveTextContent("Ethanol");
      await expect.element(table.table).toHaveTextContent("46.07");
      await expect.element(table.table).toHaveTextContent("agent");
      await expect.poll(() => table.dataRows().elements().length).toBe(5);
    });

    test("User can add a reagent from Gallery", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      await table.openAddChemicalMenu();
      await table.galleryMenuItem.click();

      await expect.element(table.galleryDialog).toBeVisible();
      await table.selectGalleryFile(/ethanol\.mol/i);
      await table.addSelectedGalleryFiles();

      await expect.element(table.moleculeInfoLoadingDialog).not.toBeInTheDocument();
      await expect.element(table.table).toHaveTextContent("ethanol");
      await expect.element(table.table).toHaveTextContent("46.07");
      await expect.element(table.table).toHaveTextContent("agent");
      await expect.poll(() => table.dataRows().elements().length).toBe(5);
    });

    test("Multiple reagents can be added sequentially", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      // Add first reagent (Ethanol) manually.
      await table.openAddChemicalMenu();
      await table.manualEntryMenuItem.click();
      await table.enterManualSmiles({ smiles: "CCO", name: "Ethanol" });
      await table.addManualReagent();
      await expect.element(table.moleculeInfoLoadingDialog).not.toBeInTheDocument();
      await expect.poll(() => table.dataRows().elements().length).toBe(5);

      // Add second reagent (Caffeine via PubChem).
      await table.openAddChemicalMenu();
      await table.pubChemMenuItem.click();
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
      await table.editCell({ row: 1, column: "Actual Mass (g)", value: "8" });
      await expect.element(table.cell("Cyclopentadiene", "Actual Moles (mol)")).toHaveTextContent("8");
    });

    test("Editing actual moles updates actual mass correctly", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      await table.editCell({ row: 1, column: "Actual Moles (mol)", value: "6" });
      await expect.element(table.cell("Cyclopentadiene", "Actual Mass (g)")).toHaveTextContent("6");
    });

    test("Editing actual mass updates yield correctly for non-limiting reactants", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      await table.editCell({ row: 1, column: "Actual Mass (g)", value: "10" });
      await expect.element(table.cell("Cyclopentadiene", "Actual Moles (mol)")).toHaveTextContent("10");
      await expect.element(table.cell("Cyclopentadiene", "Yield/Excess (%)")).toHaveTextContent("150%");
    });

    test("Multiple actual mass edits work correctly", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      await table.editCell({ row: 2, column: "Actual Mass (g)", value: "7" });
      await expect.element(table.cell("Cyclopentane", "Actual Moles (mol)")).toHaveTextContent("7");
    });

    test("Changing limiting reagent adjusts equivalent values", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      await expect.element(table.cell("Benzene", "Equivalent")).toHaveTextContent("1");
      await expect.element(table.cell("Cyclopentadiene", "Equivalent")).toHaveTextContent("2");
      await table.selectLimitingReagent("Cyclopentadiene");
      await expect.element(table.cell("Cyclopentadiene", "Equivalent")).toHaveTextContent("1");
      await expect.element(table.cell("Benzene", "Equivalent")).toHaveTextContent("0.5");
    });

    test("Changing limiting reagent updates yield calculations", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      await expect.element(table.cell("Benzene", "Yield/Excess (%)")).toHaveTextContent("—");
      await expect.element(table.cell("Cyclopentadiene", "Yield/Excess (%)")).toHaveTextContent("500%");
      await table.selectLimitingReagent("Cyclopentadiene");
      await expect.element(table.cell("Benzene", "Yield/Excess (%)")).toHaveTextContent("20%");
      await expect.element(table.cell("Cyclopentadiene", "Yield/Excess (%)")).toHaveTextContent("—");
      await expect.element(table.cell("Cyclopentane", "Yield/Excess (%)")).toHaveTextContent("66.7%");
    });

    test("Changing limiting reagent updates mass/moles editability", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      // Benzene is the limiting reagent, so its mass is editable.
      await table.editCell({ row: 0, column: "Mass (g)", value: "15" });
      await expect.element(table.cell("Benzene", "Mass (g)")).toHaveTextContent("15");
      // Switch the limiting reagent to Cyclopentadiene and edit its mass.
      await table.selectLimitingReagent("Cyclopentadiene");
      await table.editCell({ row: 1, column: "Mass (g)", value: "25" });
      await expect.element(table.cell("Cyclopentadiene", "Mass (g)")).toHaveTextContent("25");
      await expect.element(table.cell("Cyclopentadiene", "Moles (mol)")).toHaveTextContent("25");
    });

    test("Yield is computed correctly for products", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      await expect.element(table.cell("Cyclopentane", "Yield/Excess (%)")).toHaveTextContent("500%");
    });

    test("Editing equivalent normalizes all coefficients correctly", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      await table.editCell({ row: 1, column: "Equivalent", value: "4" });
      await expect.element(table.cell("Benzene", "Equivalent")).toHaveTextContent("1");
      await expect.element(table.cell("Cyclopentadiene", "Equivalent")).toHaveTextContent("4");
      await expect.element(table.cell("Cyclopentane", "Equivalent")).toHaveTextContent("3");
    });

    test("Editing negative equivalent value reverts to original value", async () => {
      render(<StoichiometryTableWithDataStory />);
      await table.waitForLoad();
      await expect.element(table.cell("Cyclopentadiene", "Equivalent")).toHaveTextContent("2");
      await table.editCell({ row: 1, column: "Equivalent", value: "-1" });
      await expect.element(table.cell("Cyclopentadiene", "Equivalent")).toHaveTextContent("2");
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
