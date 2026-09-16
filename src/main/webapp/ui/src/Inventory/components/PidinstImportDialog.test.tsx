import "@/__tests__/__mocks__/muiTransitions";
import "@/__tests__/__mocks__/matchMedia";
import "@/__tests__/__mocks__/useOauthToken";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { stubAppChrome } from "@/__tests__/helpers/appChrome";
import { wrapWithRealI18n } from "@/__tests__/helpers/realI18n";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import axios from "@/common/axios";
import commonEn from "@/modules/common/i18n/locales/en-US/common.json";
import inventoryEn from "@/modules/common/i18n/locales/en-US/inventory.json";
import { PidinstImportDialogStory } from "./PidinstImportDialog.story";

const mockAxios = new MockAdapter(axios);

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
    manufacturers: ["Carl Zeiss", "Zeiss Optics"],
    model: "LSM 980",
    instrumentTypes: ["Confocal microscope"],
    measuredVariables: ["Fluorescence intensity"],
    commissioned: "2021-03-01",
    landingPage: "https://example.org/lsm980",
    alternateIdentifier: "INV-0042",
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

const SEARCH_RESULT = { provider: "PIDINST_B2INST", total: 2, hits: HITS };

const CREATED_INSTRUMENT = {
  id: 77,
  globalId: "IN77",
  name: "Confocal Microscope",
  identifiers: [],
};

function stubEndpoints({
  searchReply = [200, SEARCH_RESULT] as [number, unknown],
  importReply = [201, CREATED_INSTRUMENT] as [number, unknown],
} = {}) {
  stubAppChrome(mockAxios);
  mockAxios.onGet(SEARCH_URL).reply(() => searchReply);
  mockAxios.onPost(IMPORT_URL).reply(() => importReply);
}

async function renderOpenDialog(ui = <PidinstImportDialogStory />) {
  const result = render(ui);
  expect(await screen.findByRole("dialog")).toBeVisible();
  return result;
}

async function search(user: ReturnType<typeof userEvent.setup>, query: string) {
  await user.type(screen.getByRole("textbox", { name: "inventory:pidinstImport.search.label" }), query);
  await user.click(screen.getByRole("button", { name: "common:actions.search" }));
  await waitFor(() => {
    expect(screen.getByRole("gridcell", { name: HITS[0].name })).toBeVisible();
  });
}

/** The row's radio, found through the name cell because every radio label is the same i18n key in cimode. */
function radioFor(name: string): HTMLElement {
  const row = screen.getByRole("gridcell", { name }).closest('[role="row"]') as HTMLElement;
  return within(row).getByRole("radio");
}

describe("PidinstImportDialog", () => {
  beforeEach(() => {
    mockAxios.reset();
    stubEndpoints();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  test("has no accessibility violations before and after a search", async () => {
    const user = userEvent.setup();
    const { baseElement } = await renderOpenDialog();
    await expectAccessible(baseElement);
    await search(user, "microscope");
    await expectAccessible(baseElement);
  });

  test("calls the search endpoint with the typed query and lists the hits", async () => {
    const user = userEvent.setup();
    await renderOpenDialog();

    await search(user, "microscope");

    const request = mockAxios.history.get.find((r) => r.url === SEARCH_URL);
    expect(request?.params).toEqual({ query: "microscope" });
    expect(screen.getByRole("gridcell", { name: "Linked Spectrometer" })).toBeVisible();
    expect(screen.getByRole("link", { name: HITS[0].pid })).toHaveAttribute("href", HITS[0].publicUrl);
    expect(screen.getByRole("gridcell", { name: "Carl Zeiss; Zeiss Optics" })).toBeVisible();
  });

  test("shows the result summary with the provider's total", async () => {
    const user = userEvent.setup();
    stubEndpoints({ searchReply: [200, { ...SEARCH_RESULT, total: 128 }] });
    await renderOpenDialog(
      await wrapWithRealI18n(<PidinstImportDialogStory />, {
        resources: { common: commonEn, inventory: inventoryEn },
        defaultNS: "inventory",
      }),
    );

    await user.type(screen.getByRole("textbox", { name: "Search the registry" }), "microscope");
    await user.click(screen.getByRole("button", { name: "Search" }));

    expect(await screen.findByText("2 of 128 records found at B2INST.")).toBeVisible();
    expect(screen.getByText(/Only the first 2 records are shown/)).toBeVisible();
  });

  test("refuses a blank search without calling the server", async () => {
    const user = userEvent.setup();
    await renderOpenDialog();

    expect(screen.getByRole("button", { name: "common:actions.search" })).toBeDisabled();
    await user.type(screen.getByRole("textbox", { name: "inventory:pidinstImport.search.label" }), "   ");
    expect(screen.getByRole("button", { name: "common:actions.search" })).toBeDisabled();

    expect(mockAxios.history.get.filter((r) => r.url === SEARCH_URL)).toHaveLength(0);
  });

  test("shows an error toast when the search fails", async () => {
    const user = userEvent.setup();
    const restoreConsole = silenceConsole(["error"], ["status code 500"]);
    stubEndpoints({ searchReply: [500, { message: "B2INST did not answer." }] });
    await renderOpenDialog();

    await user.type(screen.getByRole("textbox", { name: "inventory:pidinstImport.search.label" }), "microscope");
    await user.click(screen.getByRole("button", { name: "common:actions.search" }));

    const toast = (await screen.findByText("inventory:pidinstImport.searchError")).closest('[role="group"]');
    expect(toast).toHaveTextContent("B2INST did not answer.");
    restoreConsole();
  });

  test("offers the hidden columns through the grid's Columns toolbar button", async () => {
    const user = userEvent.setup();
    await renderOpenDialog();
    await search(user, "microscope");

    expect(screen.getByRole("button", { name: "Select columns" })).toBeVisible();
    // "Registry state" is hidden by default, so reaching it at all proves the toolbar renders
    expect(screen.getByRole("checkbox", { name: "inventory:pidinstImport.columns.state" })).toBeInTheDocument();
  });

  test("previews the selected record's non-empty fields", async () => {
    const user = userEvent.setup();
    await renderOpenDialog();
    await search(user, "microscope");

    await user.click(radioFor("Confocal Microscope"));

    const preview = screen.getByRole("region", { name: "inventory:pidinstImport.preview.title" });
    expect(within(preview).getByText("Carl Zeiss; Zeiss Optics")).toBeVisible();
    expect(within(preview).getByText("A confocal laser scanning microscope.")).toBeVisible();
    expect(within(preview).getByRole("link", { name: "https://example.org/lsm980" })).toBeVisible();
    expect(within(preview).queryByText("inventory:pidinstImport.preview.decommissioned")).not.toBeInTheDocument();
  });

  test("refuses to import without a selection", async () => {
    const user = userEvent.setup();
    await renderOpenDialog();
    await search(user, "microscope");

    await user.click(screen.getByRole("button", { name: "common:actions.import" }));

    expect(await screen.findByText("inventory:pidinstImport.validation.noSelection")).toBeVisible();
    expect(mockAxios.history.post).toHaveLength(0);
  });

  test("links an already-linked hit to its instrument and refuses to import it", async () => {
    const user = userEvent.setup();
    await renderOpenDialog();
    await search(user, "spectrometer");

    const linkedRow = screen
      .getByRole("gridcell", { name: "Linked Spectrometer" })
      .closest('[role="row"]') as HTMLElement;
    expect(within(linkedRow).getByRole("link", { name: "IN52" })).toHaveAttribute("href", "/globalId/IN52");

    await user.click(radioFor("Linked Spectrometer"));
    await user.click(screen.getByRole("button", { name: "common:actions.import" }));

    expect(await screen.findByText("inventory:pidinstImport.validation.alreadyLinked")).toBeVisible();
    expect(mockAxios.history.post).toHaveLength(0);
  });

  test("imports the selected PID, toasts a link to the new instrument, and reports it", async () => {
    const user = userEvent.setup();
    const onImported = vi.fn();
    const onClose = vi.fn();
    await renderOpenDialog(<PidinstImportDialogStory onImported={onImported} onClose={onClose} />);
    await search(user, "microscope");

    await user.click(radioFor("Confocal Microscope"));
    await user.click(screen.getByRole("button", { name: "common:actions.import" }));

    await waitFor(() => {
      expect(mockAxios.history.post.find((r) => r.url === IMPORT_URL)?.data).toBe(
        JSON.stringify({ pid: "21.T11975/aaaaa-11111" }),
      );
    });
    expect(await screen.findByText("inventory:pidinstImport.importSuccess")).toBeVisible();
    expect(onImported).toHaveBeenCalledWith({ id: 77, globalId: "IN77" });
    expect(onClose).toHaveBeenCalled();
  });
});
