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
    linked: false,
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
    linked: true,
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

/**
 * A confirm dialog that has been answered stays mounted under the transition mock, so MUI keeps
 * `aria-hidden` on the import dialog behind it. Tests that carry on afterwards pass `hidden` to
 * reach it; nothing about the component depends on this.
 */
async function search(user: ReturnType<typeof userEvent.setup>, query: string, hidden = false) {
  await user.type(screen.getByRole("textbox", { name: "inventory:pidinstImport.search.label", hidden }), query);
  await user.click(screen.getByRole("button", { name: "common:actions.search", hidden }));
  await waitFor(() => {
    expect(screen.getByRole("gridcell", { name: HITS[0].name, hidden })).toBeInTheDocument();
  });
}

/** The row's radio, found through the name cell because every radio label is the same i18n key in cimode. */
function radioFor(name: string, hidden = false): HTMLElement {
  const row = screen.getByRole("gridcell", { name, hidden }).closest('[role="row"]') as HTMLElement;
  return within(row).getByRole("radio", { hidden });
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

  test("refuses a search shorter than the minimum without calling the server", async () => {
    const user = userEvent.setup();
    await renderOpenDialog();
    const searchButton = () => screen.getByRole("button", { name: "common:actions.search" });
    const field = screen.getByRole("textbox", { name: "inventory:pidinstImport.search.label" });
    const tooShort = "inventory:pidinstImport.search.validation.tooShort";

    expect(searchButton()).toBeDisabled();
    expect(screen.queryByText(tooShort)).toBeNull();

    await user.type(field, "   ");
    expect(searchButton()).toBeDisabled();

    // the same trimming the server applies, so the button and the endpoint agree on the length
    await user.clear(field);
    await user.type(field, "  qvt  ");
    expect(searchButton()).toBeDisabled();
    expect(screen.getByText(tooShort)).toBeVisible();

    await user.clear(field);
    await user.type(field, "qvtb");
    expect(searchButton()).toBeEnabled();
    expect(screen.queryByText(tooShort)).toBeNull();

    expect(mockAxios.history.get.filter((r) => r.url === SEARCH_URL)).toHaveLength(0);
  });

  test("shows an error toast when the search fails", async () => {
    const user = userEvent.setup();
    const restoreConsole = silenceConsole(["error"], ["PIDINST search failed"]);
    try {
      stubEndpoints({ searchReply: [500, { message: "B2INST did not answer." }] });
      await renderOpenDialog();

      await user.type(screen.getByRole("textbox", { name: "inventory:pidinstImport.search.label" }), "microscope");
      await user.click(screen.getByRole("button", { name: "common:actions.search" }));

      const toast = (await screen.findByText("inventory:pidinstImport.searchError")).closest('[role="group"]');
      expect(toast).toHaveTextContent("B2INST did not answer.");
    } finally {
      // restored here so a failed assertion does not leave the console silenced for the rest of the file
      restoreConsole();
    }
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

  test("marks a hit linked to an instrument the user cannot see, names nothing, and refuses to import it", async () => {
    const user = userEvent.setup();
    // the server omits the Global ID when the caller may not read the linking instrument
    const { linkedInstrumentGlobalId: _hiddenFromThisUser, ...hiddenLink } = HITS[1];
    stubEndpoints({ searchReply: [200, { ...SEARCH_RESULT, hits: [HITS[0], hiddenLink] }] });
    await renderOpenDialog();
    await search(user, "spectrometer");

    const linkedRow = screen
      .getByRole("gridcell", { name: "Linked Spectrometer" })
      .closest('[role="row"]') as HTMLElement;
    expect(within(linkedRow).getByText("inventory:pidinstImport.linkedTo.noAccess")).toBeVisible();
    expect(within(linkedRow).queryByRole("link", { name: "IN52" })).not.toBeInTheDocument();

    await user.click(radioFor("Linked Spectrometer"));
    const preview = screen.getByRole("region", { name: "inventory:pidinstImport.preview.title" });
    expect(within(preview).getByText("inventory:pidinstImport.preview.alreadyLinkedNoAccess")).toBeVisible();
    expect(screen.queryByText("IN52")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "common:actions.import" }));

    expect(await screen.findByText("inventory:pidinstImport.validation.alreadyLinkedNoAccess")).toBeVisible();
    expect(mockAxios.history.post).toHaveLength(0);
  });

  test("makes the no-access cell readable on hover, since the grid clips it", async () => {
    const user = userEvent.setup();
    const { linkedInstrumentGlobalId: _hidden, ...hiddenLink } = HITS[1];
    stubEndpoints({ searchReply: [200, { ...SEARCH_RESULT, hits: [HITS[0], hiddenLink] }] });
    await renderOpenDialog();
    await search(user, "spectrometer");

    await user.hover(screen.getByText("inventory:pidinstImport.linkedTo.noAccess"));

    expect(await screen.findByRole("tooltip", { name: "inventory:pidinstImport.linkedTo.noAccess" })).toBeVisible();
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

  test("clears the previous hits when a search fails", async () => {
    const user = userEvent.setup();
    const restoreConsole = silenceConsole(["error"], ["PIDINST search failed"]);
    try {
      await renderOpenDialog();
      await search(user, "microscope");

      stubEndpoints({ searchReply: [500, { message: "B2INST did not answer." }] });
      await user.clear(screen.getByRole("textbox", { name: "inventory:pidinstImport.search.label" }));
      await user.type(screen.getByRole("textbox", { name: "inventory:pidinstImport.search.label" }), "telescope");
      await user.click(screen.getByRole("button", { name: "common:actions.search" }));

      // the rows of the previous search must not sit under the new query
      await waitFor(() => {
        expect(screen.queryByRole("gridcell", { name: HITS[0].name })).toBeNull();
      });
      expect(screen.queryByRole("gridcell", { name: HITS[1].name })).toBeNull();
    } finally {
      restoreConsole();
    }
  });

  test("announces the result summary in a live region that exists before the search", async () => {
    const user = userEvent.setup();
    await renderOpenDialog();

    // present while empty, because a live region inserted along with its text is not announced
    const liveRegion = screen.getByRole("status");
    expect(liveRegion).toBeInTheDocument();
    expect(liveRegion).toHaveTextContent("");

    await search(user, "microscope");

    expect(screen.getByRole("status")).toHaveTextContent("inventory:pidinstImport.results.summary");
  });

  test("keeps the summary grammatical when a single record is found", async () => {
    const user = userEvent.setup();
    stubEndpoints({ searchReply: [200, { provider: "PIDINST_B2INST", total: 1, hits: [HITS[0]] }] });
    await renderOpenDialog(
      await wrapWithRealI18n(<PidinstImportDialogStory />, {
        resources: { common: commonEn, inventory: inventoryEn },
        defaultNS: "inventory",
      }),
    );

    await user.type(screen.getByRole("textbox", { name: "Search the registry" }), "21.T11975/aaaaa-11111");
    await user.click(screen.getByRole("button", { name: "Search" }));

    // a direct PID lookup always returns one hit, so "1 of 1 records" is the common case
    expect(await screen.findByText("1 of 1 record found at B2INST.")).toBeVisible();
  });

  test("says nothing about totals when the search matched no importable records", async () => {
    const user = userEvent.setup();
    // DataCite reports what its index matched; the hits are what survives filtering to published
    // instruments, so "0 of 128" would sit directly above "no records match"
    stubEndpoints({ searchReply: [200, { provider: "PIDINST_DATACITE", total: 128, hits: [] }] });
    await renderOpenDialog(
      await wrapWithRealI18n(<PidinstImportDialogStory />, {
        resources: { common: commonEn, inventory: inventoryEn },
        defaultNS: "inventory",
      }),
    );

    await user.type(screen.getByRole("textbox", { name: "Search the registry" }), "microscope");
    await user.click(screen.getByRole("button", { name: "Search" }));

    // once in the grid's empty overlay, and once in the live region, which is the only one of the
    // two that a screen reader announces
    expect(await screen.findAllByText("No published instrument records match this search.")).toHaveLength(2);
    expect(screen.getByRole("status")).toHaveTextContent("No published instrument records match this search.");
    expect(screen.queryByText(/records found at/)).toBeNull();
  });

  test("does not navigate away when the dialog is closed mid-import", async () => {
    const user = userEvent.setup();
    const onImported = vi.fn();
    const onClose = vi.fn();
    let finishImport: (() => void) | undefined;
    stubEndpoints({ importReply: [201, CREATED_INSTRUMENT] });
    mockAxios.onPost(IMPORT_URL).reply(
      () =>
        new Promise((resolve) => {
          finishImport = () => {
            resolve([201, CREATED_INSTRUMENT]);
          };
        }),
    );
    await renderOpenDialog(<PidinstImportDialogStory onImported={onImported} onClose={onClose} />);
    await search(user, "microscope");
    await user.click(radioFor("Confocal Microscope"));
    await user.click(screen.getByRole("button", { name: "common:actions.import" }));

    await user.click(screen.getByRole("button", { name: "common:actions.close" }));
    expect(await screen.findByText("inventory:pidinstImport.closeConfirm.message")).toBeVisible();
    await user.click(screen.getByRole("button", { name: "inventory:pidinstImport.closeConfirm.confirm" }));
    expect(onClose).toHaveBeenCalled();

    finishImport?.();

    // the confirm promised only that the result would not be shown here, not a change of page
    await waitFor(() => {
      expect(screen.getByText("inventory:pidinstImport.importSuccess")).toBeVisible();
    });
    expect(onImported).not.toHaveBeenCalled();
  });

  test("calls onClose once when an import finishes after the dialog was closed", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    let finishImport: (() => void) | undefined;
    stubEndpoints();
    mockAxios.onPost(IMPORT_URL).reply(
      () =>
        new Promise((resolve) => {
          finishImport = () => {
            resolve([201, CREATED_INSTRUMENT]);
          };
        }),
    );
    await renderOpenDialog(<PidinstImportDialogStory onClose={onClose} />);
    await search(user, "microscope");
    await user.click(radioFor("Confocal Microscope"));
    await user.click(screen.getByRole("button", { name: "common:actions.import" }));
    await user.click(screen.getByRole("button", { name: "common:actions.close" }));
    await user.click(screen.getByRole("button", { name: "inventory:pidinstImport.closeConfirm.confirm" }));
    expect(onClose).toHaveBeenCalledTimes(1);

    finishImport?.();

    await waitFor(() => {
      expect(screen.getByText("inventory:pidinstImport.importSuccess")).toBeVisible();
    });
    // the close the user confirmed already ran; closing again would shut a dialog they reopened
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  test("does not navigate away when an import from a closed session finishes during a later one", async () => {
    const user = userEvent.setup();
    const onImported = vi.fn();
    stubEndpoints();
    const finishers: Array<(value: [number, unknown]) => void> = [];
    mockAxios.onPost(IMPORT_URL).reply(
      () =>
        new Promise((resolve) => {
          finishers.push(resolve);
        }),
    );
    await renderOpenDialog(<PidinstImportDialogStory onImported={onImported} />);
    await search(user, "microscope");
    await user.click(radioFor("Confocal Microscope"));
    await user.click(screen.getByRole("button", { name: "common:actions.import" }));
    await user.click(screen.getByRole("button", { name: "common:actions.close" }));
    await user.click(screen.getByRole("button", { name: "inventory:pidinstImport.closeConfirm.confirm" }));

    // a fresh session in the same mounted dialog, exactly as reopening it from the Create menu gives
    await search(user, "microscope", true);
    await user.click(radioFor("Confocal Microscope", true));
    await user.click(screen.getByRole("button", { name: "common:actions.import", hidden: true }));

    // the abandoned import lands while the second one is still running
    finishers[0]?.([201, CREATED_INSTRUMENT]);

    await waitFor(() => {
      expect(screen.getByText("inventory:pidinstImport.importSuccess")).toBeVisible();
    });
    expect(onImported).not.toHaveBeenCalled();
  });

  test("ignores a search that resolves after the dialog was closed", async () => {
    const user = userEvent.setup();
    stubEndpoints();
    let finishSearch: ((value: [number, unknown]) => void) | undefined;
    mockAxios.onGet(SEARCH_URL).reply(
      () =>
        new Promise((resolve) => {
          finishSearch = resolve;
        }),
    );
    const { rerender } = await renderOpenDialog();
    await user.type(screen.getByRole("textbox", { name: "inventory:pidinstImport.search.label" }), "microscope");
    await user.click(screen.getByRole("button", { name: "common:actions.search" }));

    await user.click(screen.getByRole("button", { name: "common:actions.close" }));
    rerender(<PidinstImportDialogStory open={false} />);
    rerender(<PidinstImportDialogStory open={true} />);

    finishSearch?.([200, SEARCH_RESULT]);

    await waitFor(() => {
      expect(screen.getByRole("textbox", { name: "inventory:pidinstImport.search.label" })).toHaveValue("");
    });
    expect(screen.queryByRole("gridcell", { name: "Confocal Microscope" })).toBeNull();
    expect(screen.getByRole("status")).toHaveTextContent("");
  });

  test("names DataCite as the registry a hit came from", async () => {
    const user = userEvent.setup();
    stubEndpoints({
      searchReply: [
        200,
        {
          provider: "PIDINST_DATACITE",
          total: 1,
          hits: [{ ...HITS[0], pid: "10.82316/qvtb-aw74", provider: "PIDINST_DATACITE" }],
        },
      ],
    });
    await renderOpenDialog(
      await wrapWithRealI18n(<PidinstImportDialogStory />, {
        resources: { common: commonEn, inventory: inventoryEn },
        defaultNS: "inventory",
      }),
    );

    await user.type(screen.getByRole("textbox", { name: "Search the registry" }), "microscope");
    await user.click(screen.getByRole("button", { name: "Search" }));

    expect(await screen.findByText("1 of 1 record found at DataCite.")).toBeVisible();
  });

  test("names a provider it has no label for rather than calling it DataCite", async () => {
    const user = userEvent.setup();
    stubEndpoints({
      searchReply: [200, { provider: "PIDINST_SOMETHING_NEW", total: 1, hits: [HITS[0]] }],
    });
    await renderOpenDialog(
      await wrapWithRealI18n(<PidinstImportDialogStory />, {
        resources: { common: commonEn, inventory: inventoryEn },
        defaultNS: "inventory",
      }),
    );

    await user.type(screen.getByRole("textbox", { name: "Search the registry" }), "microscope");
    await user.click(screen.getByRole("button", { name: "Search" }));

    // every non-B2INST value used to be labelled DataCite, so a third registry would have been
    // named wrongly to every user with nothing failing
    expect(await screen.findByText("1 of 1 record found at PIDINST_SOMETHING_NEW.")).toBeVisible();
  });

  test("clears the previous result while the next search is running", async () => {
    const user = userEvent.setup();
    stubEndpoints();
    await renderOpenDialog();
    await search(user, "microscope");
    expect(screen.getByRole("status")).not.toHaveTextContent("");

    let finishSearch: ((value: [number, unknown]) => void) | undefined;
    mockAxios.onGet(SEARCH_URL).reply(
      () =>
        new Promise((resolve) => {
          finishSearch = resolve;
        }),
    );
    await user.clear(screen.getByRole("textbox", { name: "inventory:pidinstImport.search.label" }));
    await user.type(screen.getByRole("textbox", { name: "inventory:pidinstImport.search.label" }), "spectrometer");
    await user.click(screen.getByRole("button", { name: "common:actions.search" }));

    // the old count would otherwise sit above the rows the overlay is covering
    await waitFor(() => {
      expect(screen.getByRole("status")).toHaveTextContent("");
    });
    finishSearch?.([200, SEARCH_RESULT]);
  });
});
