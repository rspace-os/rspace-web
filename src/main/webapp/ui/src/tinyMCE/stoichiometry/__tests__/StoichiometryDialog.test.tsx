import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/customQueries";
import axios from "@/common/axios";
// eslint-disable-next-line vitest/no-mocks-import
import "@/__tests__/__mocks__/matchMedia";
// eslint-disable-next-line vitest/no-mocks-import
import "@/__tests__/__mocks__/useOauthToken";
import StoichiometryDialogEntrypoint from "../StoichiometryDialogEntrypoint";
import {
  StoichiometryDialogWithCalculateButtonStory,
  StoichiometryDialogWithTableStory,
} from "./StoichiometryDialog.story";

/*
 * The chemistry integration check uses `@/common/axios`; every other network
 * call this dialog makes (calculate / get / update / delete stoichiometry, the
 * inventory OAuth token) goes through the global `fetch`, which
 * `vitest-fetch-mock` mocks for us. We stub the integration hook directly so we
 * do not depend on the axios call resolving, mirroring the existing test in
 * this area.
 */
vi.mock("@/hooks/api/integrationHelpers", () => ({
  useIntegrationIsAllowedAndEnabled: () => ({ tag: "success", value: true }),
}));

const mockAxios = new MockAdapter(axios);

const mockResponse = {
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
      coefficient: 1.0,
      molecularWeight: 78.11,
      mass: null,
      moles: null,
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
      actualAmount: null,
      actualYield: null,
      limitingReagent: false,
      notes: null,
    },
  ],
};

type RecordedRequest = { url: URL; method: string; body: string | null };

let recordedRequests: Array<RecordedRequest> = [];

const hasStoichiometryRequest = (method: "POST" | "PUT" | "DELETE"): boolean =>
  recordedRequests.some(
    (request) => request.url.pathname.includes("/api/v1/stoichiometry") && request.method === method,
  );

/**
 * Wire up the global `fetch` mock for every endpoint the dialog reaches. By
 * default the stoichiometry endpoint succeeds for GET/POST/PUT and reports
 * success for DELETE. Individual tests can override behaviour by inspecting or
 * replacing `fetchMock` after calling this helper.
 */
function mockNetwork() {
  fetchMock.mockResponse((request) => {
    const url = new URL(request.url, "http://localhost");
    const method = request.method.toUpperCase();

    if (url.pathname === "/userform/ajax/inventoryOauthToken") {
      return Promise.resolve(JSON.stringify({ data: "test-token" }));
    }

    if (url.pathname === "/api/v1/stoichiometry") {
      if (method === "DELETE") {
        return Promise.resolve(JSON.stringify({ success: true }));
      }
      return Promise.resolve(JSON.stringify(mockResponse));
    }

    return Promise.resolve(JSON.stringify({}));
  });
}

beforeEach(() => {
  vi.clearAllMocks();
  recordedRequests = [];
  fetchMock.resetMocks();
  // Record every fetch the dialog makes so we can assert on POST/PUT/DELETE.
  const originalFetch = fetchMock.bind(fetchMock);
  vi.stubGlobal("fetch", (input: RequestInfo | URL, init?: RequestInit) => {
    const rawUrl = typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url;
    recordedRequests.push({
      url: new URL(rawUrl, "http://localhost"),
      method: (init?.method ?? "GET").toUpperCase(),
      body: typeof init?.body === "string" ? init.body : null,
    });
    return originalFetch(input, init);
  });
  mockNetwork();
  mockAxios.reset();
  mockAxios.onAny().reply(200, { data: {} });
});

afterEach(() => {
  vi.unstubAllGlobals();
  mockAxios.reset();
});

describe("StoichiometryDialog", () => {
  it("shows calculate button when no table is present", async () => {
    render(<StoichiometryDialogWithCalculateButtonStory />);

    expect(await screen.findByRole("button", { name: "Calculate Stoichiometry" })).toBeVisible();
  });

  it("calculate dialog has no accessibility violations", async () => {
    const { baseElement } = render(<StoichiometryDialogWithCalculateButtonStory />);

    await screen.findByRole("button", { name: "Calculate Stoichiometry" });

    await expectAccessible(baseElement);
  });

  it("displays stoichiometry table when data is available", async () => {
    render(<StoichiometryDialogWithTableStory />);

    expect(await screen.findByRole("grid")).toBeVisible();
  });

  it("has no accessibility violations when the table is displayed", async () => {
    const { baseElement } = render(<StoichiometryDialogWithTableStory />);

    await screen.findByRole("grid");

    await expectAccessible(baseElement);
  });

  it("invokes callback when table is successfully created", async () => {
    const user = userEvent.setup();
    const onTableCreated = vi.fn();

    render(<StoichiometryDialogWithCalculateButtonStory onTableCreated={onTableCreated} />);

    await user.click(await screen.findByRole("button", { name: "Calculate Stoichiometry" }));

    expect(await screen.findByRole("grid")).toBeVisible();
    expect(onTableCreated).toHaveBeenCalled();
  });

  it("makes a POST API call when creating a new table", async () => {
    const user = userEvent.setup();

    render(<StoichiometryDialogWithCalculateButtonStory />);

    await user.click(await screen.findByRole("button", { name: "Calculate Stoichiometry" }));

    expect(await screen.findByRole("grid")).toBeVisible();
    expect(hasStoichiometryRequest("POST")).toBe(true);
  });

  it("auto-creates the stoichiometry table on open when requested", async () => {
    render(
      <StoichiometryDialogEntrypoint
        open
        onClose={() => {}}
        chemId={12345}
        autoCreateTableOnOpen
        recordId={1}
        stoichiometryId={undefined}
        stoichiometryRevision={undefined}
      />,
    );

    expect(await screen.findByRole("grid")).toBeVisible();
    expect(hasStoichiometryRequest("POST")).toBe(true);
  });

  it("shows an inline error when creating a new table fails", async () => {
    const user = userEvent.setup();
    const createErrorMessage = "Unable to create stoichiometry table.";

    fetchMock.mockResponse((request) => {
      const url = new URL(request.url, "http://localhost");
      const method = request.method.toUpperCase();

      if (url.pathname === "/userform/ajax/inventoryOauthToken") {
        return Promise.resolve(JSON.stringify({ data: "test-token" }));
      }

      if (url.pathname === "/api/v1/stoichiometry" && method === "POST") {
        return Promise.resolve({
          status: 500,
          body: JSON.stringify({
            status: "error",
            httpCode: 500,
            internalCode: 500,
            message: createErrorMessage,
            messageCode: null,
            errors: [],
            iso8601Timestamp: "2026-04-07T00:00:00Z",
            data: null,
          }),
        });
      }

      return Promise.resolve(JSON.stringify({}));
    });

    render(<StoichiometryDialogWithCalculateButtonStory />);

    await user.click(await screen.findByRole("button", { name: "Calculate Stoichiometry" }));

    // The calculate button stays visible because the table was never created.
    expect(await screen.findByRole("button", { name: "Calculate Stoichiometry" })).toBeVisible();
    const errorAlert = await screen.findByRole("alert");
    expect(errorAlert).toHaveTextContent(createErrorMessage);
  });

  it("does not show save button when table has not been modified", async () => {
    render(<StoichiometryDialogWithTableStory />);

    await screen.findByRole("grid");

    expect(screen.queryByRole("button", { name: "Save Changes" })).not.toBeInTheDocument();
  });

  it("shows save button when limiting reagent is changed", async () => {
    const user = userEvent.setup();

    render(<StoichiometryDialogWithTableStory />);

    await screen.findByRole("grid");

    await user.click(
      screen.getByRole("radio", {
        name: /Select Cyclopentadiene as limiting reagent/,
      }),
    );

    expect(await screen.findByRole("button", { name: "Save Changes" })).toBeVisible();
  });

  it("hides save button after saving changes", async () => {
    const user = userEvent.setup();

    render(<StoichiometryDialogWithTableStory />);

    await screen.findByRole("grid");

    await user.click(
      screen.getByRole("radio", {
        name: /Select Cyclopentadiene as limiting reagent/,
      }),
    );

    await user.click(await screen.findByRole("button", { name: "Save Changes" }));

    await waitFor(() => {
      expect(screen.queryByRole("button", { name: "Save Changes" })).not.toBeInTheDocument();
    });
  });

  it("makes a PUT API call when saving changes to the table", async () => {
    const user = userEvent.setup();

    render(<StoichiometryDialogWithTableStory />);

    await screen.findByRole("grid");

    await user.click(
      screen.getByRole("radio", {
        name: /Select Cyclopentadiene as limiting reagent/,
      }),
    );

    await user.click(await screen.findByRole("button", { name: "Save Changes" }));

    await waitFor(() => {
      expect(hasStoichiometryRequest("PUT")).toBe(true);
    });
  });

  it("shows delete button when table is present", async () => {
    render(<StoichiometryDialogWithTableStory />);

    await screen.findByRole("grid");

    expect(screen.getByRole("button", { name: "Delete" })).toBeVisible();
  });

  it("shows confirmation dialog when delete button is clicked", async () => {
    const user = userEvent.setup();

    render(<StoichiometryDialogWithTableStory />);

    await screen.findByRole("grid");

    await user.click(screen.getByRole("button", { name: "Delete" }));

    expect(
      await screen.findByRole("dialog", {
        name: /Delete Stoichiometry Table/,
      }),
    ).toBeVisible();
  });

  it("makes a DELETE API call and hides table when deletion is confirmed", async () => {
    const user = userEvent.setup();

    render(<StoichiometryDialogWithTableStory />);

    await screen.findByRole("grid");

    await user.click(screen.getByRole("button", { name: "Delete" }));

    const confirmDialog = await screen.findByRole("dialog", {
      name: /Delete Stoichiometry Table/,
    });

    await user.click(
      within(confirmDialog).getByRole("button", {
        name: "Delete",
      }),
    );

    await waitFor(() => {
      expect(hasStoichiometryRequest("DELETE")).toBe(true);
    });
    await waitFor(() => {
      expect(screen.queryByRole("grid")).not.toBeInTheDocument();
    });
  });
});
