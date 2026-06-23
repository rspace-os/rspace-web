import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { userEvent } from "vitest/browser";
import { worker } from "@/__tests__/browserSetup";
import { oauthTokenHandler } from "@/__tests__/mocks/inventoryMocks";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import { ImportDialogStory } from "./ImportDialog.story";
import { PubchemImportDialogPage } from "./pageObjects/PubchemImportDialogPage";

/*
 * Search results returned by the mock /api/v1/pubchem/search endpoint.
 * Mirrored from the Playwright spec so the same compound data (names, URLs,
 * pubchemIds) is available to all tests.
 */
const ASPIRIN_RESULT = {
  name: "Aspirin",
  pngImage: "https://pubchem.ncbi.nlm.nih.gov/image/imgsrv.fcgi?cid=2244&t=l",
  smiles: "CC(=O)OC1=CC=CC=C1C(=O)O",
  formula: "C9H8O4",
  pubchemId: "2244",
  pubchemUrl: "https://pubchem.ncbi.nlm.nih.gov/compound/2244",
  cas: "50-78-2",
};

const PARACETAMOL_RESULT = {
  name: "Paracetamol",
  pngImage: "https://pubchem.ncbi.nlm.nih.gov/image/imgsrv.fcgi?cid=1983&t=l",
  smiles: "CC(=O)NC1=CC=C(O)C=C1",
  formula: "C8H9NO2",
  pubchemId: "1983",
  pubchemUrl: "https://pubchem.ncbi.nlm.nih.gov/compound/1983",
  cas: "103-90-2",
};

/**
 * MSW handler for /api/v1/pubchem/search.
 *
 * Mirrors the Playwright `router.route` conditional logic exactly:
 * - searchTerm === "multiple"  → returns [Aspirin, Paracetamol]
 * - any other term             → returns [Aspirin] (single result, auto-selected)
 */
const pubchemSearchHandler = () =>
  http.post("/api/v1/pubchem/search", async ({ request }) => {
    const body = (await request.json()) as { searchTerm?: string };
    if (body.searchTerm === "multiple") {
      return HttpResponse.json([ASPIRIN_RESULT, PARACETAMOL_RESULT]);
    }
    return HttpResponse.json([ASPIRIN_RESULT]);
  });

const dialog = new PubchemImportDialogPage();

beforeEach(() => {
  worker.use(oauthTokenHandler(), pubchemSearchHandler());
});

afterEach(() => {
  worker.events.removeAllListeners();
  cleanup();
});

describe("ImportDialog", () => {
  test("Should have no axe violations.", async () => {
    render(<ImportDialogStory />);
    await expect.element(dialog.searchInput).toBeVisible();
    await expectNoAxeViolations();
  });

  test("Should allow keyboard selection of compounds by pressing enter on the card", async () => {
    render(<ImportDialogStory />);
    await dialog.performSearch("multiple");
    await expect.element(dialog.compoundCard("Aspirin")).toBeVisible();
    dialog.focusAspirinCardButton();
    // The CardActionArea button is now focused; pressing Enter activates it.
    await userEvent.keyboard("{Enter}");
    // Pressing Enter on the card button toggles selection via the CardActionArea.
    // The checkbox reflects the selected state.
    await expect.element(dialog.compoundCardCheckbox("Aspirin")).toBeChecked();
  });

  test("Should allow keyboard selection of compounds by pressing space on the checkbox", async () => {
    render(<ImportDialogStory />);
    await dialog.performSearch("multiple");
    await expect.element(dialog.compoundCard("Aspirin")).toBeVisible();
    await dialog.tabToAspirinCardCheckbox();
    // The checkbox is now focused; pressing Space toggles it.
    await userEvent.keyboard(" ");
    await expect.element(dialog.compoundCardCheckbox("Aspirin")).toBeChecked();
  });

  test("Should not toggle selection when clicking on external links", async () => {
    /*
     * NEW-TAB ADAPTATION: The original Playwright test used
     * `page.context().waitForEvent('page')` to intercept the new browser tab
     * opened by the `target="_blank"` link. Vitest browser mode does not
     * provide per-context tab lifecycle events, so we cannot intercept or wait
     * for a new tab.
     *
     * Instead we assert the INTENT of the test structurally:
     *   1. The "View on PubChem" link carries `target="_blank"` (it will open
     *      in a new tab in a real browser session).
     *   2. The link's `href` points to the expected PubChem URL.
     *   3. Clicking the link does NOT change the compound's selected state
     *      (the original test's core assertion — the link's stopPropagation
     *      prevents the card's onClick from toggling the selection).
     */
    render(<ImportDialogStory />);
    await dialog.performSearch("multiple");
    await expect.element(dialog.compoundCard("Aspirin")).toBeVisible();

    // First select the Aspirin compound via the checkbox.
    dialog.clickFirstCompoundCheckbox();
    await expect.element(dialog.firstCompoundCheckbox).toBeChecked();

    // Verify the external link has the correct target and href.
    await expect.element(dialog.firstViewOnPubchemLink).toHaveAttribute("target", "_blank");
    await expect.element(dialog.firstViewOnPubchemLink).toHaveAttribute("href", ASPIRIN_RESULT.pubchemUrl);

    // Click the external link. Its onClick calls stopPropagation so the card's
    // selection toggle must not fire.
    await dialog.firstViewOnPubchemLink.click();

    // The compound must still be selected after the link click.
    await expect.element(dialog.firstCompoundCheckbox).toBeChecked();
  });
});
