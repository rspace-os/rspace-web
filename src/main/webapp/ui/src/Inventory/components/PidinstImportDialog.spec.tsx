import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, onTestFinished, test } from "vitest";
import { page } from "vitest/browser";
import { worker } from "@/__tests__/browserSetup";
import { clickWhenInViewport, moveToastStackIntoViewport } from "@/__tests__/pageObjects/viewport";
import { PidinstImportDialogStory } from "./PidinstImportDialog.story";
import { PidinstImportDialogPage } from "./pageObjects/PidinstImportDialogPage";

/*
 * Only what needs real layout: the toast stack sits off-viewport until moved, the success link
 * hides behind a sub-message toggle, the invalid-Import reason is a Popover anchored to the
 * button, and the grid's pager is absent from the jsdom DataGrid stub. Everything else is in
 * PidinstImportDialog.test.tsx.
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
    measurementTechniques: ["https://other.researchspace.com/globalId/IC65536"],
    calibrations: ["10.1000/calibration-certificate"],
    alreadyLinked: false,
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
    measurementTechniques: [],
    calibrations: [],
    alreadyLinked: true,
    linkedInstrumentGlobalId: "IN52",
  },
];

const CREATED_INSTRUMENT = {
  id: 77,
  globalId: "IN77",
  name: "Confocal Microscope",
  identifiers: [],
};

const SEARCH_RESULT = {
  providers: ["PIDINST_DATACITE", "PIDINST_B2INST"],
  pageNumber: 0,
  pageSize: 50,
  totalHits: 2,
  totalsByProvider: { PIDINST_B2INST: 2, PIDINST_DATACITE: 0 },
  hits: HITS,
};

const searchRequests: Array<URLSearchParams> = [];

const searchHandler = (overrides: Partial<typeof SEARCH_RESULT> = {}) =>
  http.get(SEARCH_URL, ({ request }) => {
    searchRequests.push(new URL(request.url).searchParams);
    return HttpResponse.json({ ...SEARCH_RESULT, ...overrides });
  });

const importSuccessHandler = () => http.post(IMPORT_URL, () => HttpResponse.json(CREATED_INSTRUMENT, { status: 201 }));

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
  searchRequests.length = 0;
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

  test("requests the next page from the grid's pager and keeps the query", async () => {
    worker.use(searchHandler({ totalHits: 120 }));
    await openAndSearch();

    await dialog.goToNextPage();

    await expect.poll(() => searchRequests.at(-1)?.get("pageNumber")).toBe("1");
    expect(Object.fromEntries(searchRequests.at(-1) ?? [])).toEqual({
      query: "microscope",
      providers: "PIDINST_DATACITE,PIDINST_B2INST",
      pageNumber: "1",
    });
  });

  test("leaves the picked record out of the grid footer, which holds only the pager", async () => {
    await openAndSearch();

    await dialog.selectRecord("Confocal Microscope");

    await expect.element(dialog.recordRadio("Confocal Microscope")).toBeChecked();
    await expect.element(page.getByText(/row selected/)).not.toBeInTheDocument();
  });

  test("disables the pager while the search box or the registries differ from the search on screen", async () => {
    worker.use(searchHandler({ totalHits: 120 }));
    await openAndSearch();
    await expect.element(dialog.nextPageButton).toBeEnabled();

    await dialog.searchField.fill("spectrometer");
    await expect.element(dialog.nextPageButton).toBeDisabled();

    await dialog.searchField.fill("microscope");
    await expect.element(dialog.nextPageButton).toBeEnabled();

    await dialog.registryCheckbox("DataCite").click();
    await expect.element(dialog.nextPageButton).toBeDisabled();
  });

  test("keeps the pager in view on a short screen", async () => {
    // the viewport belongs to the shared browser, so it would leak into the next spec file
    const { innerWidth, innerHeight } = window;
    onTestFinished(() => page.viewport(innerWidth, innerHeight));
    await page.viewport(1280, 880);
    await openAndSearch();

    await expect.element(dialog.nextPageButton).toBeInViewport({ ratio: 1 });
  });
});
