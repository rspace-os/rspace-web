import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { worker } from "@/__tests__/browserSetup";
import { clickWhenInViewport, moveToastStackIntoViewport } from "@/__tests__/pageObjects/viewport";
import { PidinstImportDialogStory } from "./PidinstImportDialog.story";
import { PidinstImportDialogPage } from "./pageObjects/PidinstImportDialogPage";

/*
 * Only what needs real layout: the toast stack sits off-viewport until moved, the success link
 * hides behind a sub-message toggle, and the invalid-Import reason is a Popover anchored to the
 * button. Everything else is in PidinstImportDialog.test.tsx.
 */

const SEARCH_URL = "/api/inventory/v1/pidinst/search";
const IMPORT_URL = "/api/inventory/v1/instruments/importPidinst";

const HITS = [
  {
    pid: "21.T11975/aaaaa-11111",
    provider: "PIDINST_B2INST",
    providerRecordUrl: "https://b2inst-test.gwdg.de/records/aaaaa-11111",
    publicUrl: "https://hdl.handle.net/21.T11975/aaaaa-11111",
    state: "accepted",
    name: "Confocal Microscope",
    description: "A confocal laser scanning microscope.",
    owners: ["Leibniz Institute"],
    manufacturers: ["Carl Zeiss"],
    model: "LSM 980",
    instrumentTypes: ["Confocal microscope"],
    measuredVariables: ["Fluorescence intensity"],
    commissioned: "2021-03-01",
    landingPage: "https://example.org/lsm980",
  },
  {
    pid: "21.T11975/bbbbb-22222",
    provider: "PIDINST_B2INST",
    publicUrl: "https://hdl.handle.net/21.T11975/bbbbb-22222",
    state: "accepted",
    name: "Linked Spectrometer",
    owners: [],
    manufacturers: ["Bruker"],
    instrumentTypes: [],
    measuredVariables: [],
    linkedInstrumentGlobalId: "IN52",
  },
];

const CREATED_INSTRUMENT = {
  id: 77,
  globalId: "IN77",
  name: "Confocal Microscope",
  identifiers: [],
};

const searchHandler = () =>
  http.get(SEARCH_URL, () => HttpResponse.json({ provider: "PIDINST_B2INST", total: 2, hits: HITS }));

const importSuccessHandler = () => http.post(IMPORT_URL, () => HttpResponse.json(CREATED_INSTRUMENT, { status: 201 }));

/** The ApiError body the server sends for a PID an instrument already links (409). */
const importConflictHandler = () =>
  http.post(IMPORT_URL, () =>
    HttpResponse.json(
      {
        status: "CONFLICT",
        httpCode: 409,
        internalCode: 40903,
        message: "This PID is already linked to instrument IN52.",
        messageCode: null,
        errors: [],
        iso8601Timestamp: "2026-09-15T12:00:00.000Z",
        data: null,
      },
      { status: 409 },
    ),
  );

const dialog = new PidinstImportDialogPage();

function bringToastsIntoView(): void {
  const toastsEl = document.querySelector('[data-testid="Toasts"]');
  if (toastsEl instanceof HTMLElement) moveToastStackIntoViewport(toastsEl);
}

async function openAndSearch(): Promise<void> {
  render(<PidinstImportDialogStory />);
  await expect.element(dialog.dialog).toBeVisible();
  await dialog.search("microscope");
  await expect.element(dialog.recordRadio("Confocal Microscope")).toBeVisible();
}

beforeEach(() => {
  worker.use(searchHandler(), importSuccessHandler());
});

afterEach(() => {
  cleanup();
});

describe("PidinstImportDialog", () => {
  test("shows a success toast whose sub-message links to the imported instrument", async () => {
    await openAndSearch();

    await dialog.selectRecord("Confocal Microscope");
    await dialog.clickImport();

    bringToastsIntoView();
    const successAlert = dialog.successAlert();
    await expect.element(successAlert).toBeVisible();

    await clickWhenInViewport(dialog.subMessageToggle(1));
    const link = successAlert
      .getByRole("alert")
      .filter({ hasText: "Confocal Microscope" })
      .getByRole("link", { name: "IN77" });
    await expect.element(link).toBeVisible();
    await expect.element(link).toHaveAttribute("href", "/globalId/IN77");
  });

  test("shows the server's message when the import is refused", async () => {
    worker.use(importConflictHandler());
    await openAndSearch();

    await dialog.selectRecord("Confocal Microscope");
    await dialog.clickImport();

    bringToastsIntoView();
    const errorAlert = dialog.errorAlert();
    await expect.element(errorAlert).toBeVisible();
    await expect.element(errorAlert).toHaveTextContent("This PID is already linked to instrument IN52.");
  });

  test("refuses to import an already-linked record and names the instrument that links it", async () => {
    await openAndSearch();

    await dialog.selectRecord("Linked Spectrometer");
    await dialog.clickImport();

    await expect.element(dialog.validationAlert("This PID is already linked to instrument IN52.")).toBeVisible();
    await expect.element(dialog.successAlert()).not.toBeInTheDocument();
  });
});
