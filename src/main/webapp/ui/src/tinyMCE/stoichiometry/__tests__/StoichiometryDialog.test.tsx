import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { server } from "@/__tests__/mswServer";
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
 * inventory OAuth token) goes through `fetch`, which MSW handles for us. We
 * stub the integration hook directly so we
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
 * Wire up MSW for every endpoint the dialog reaches. By
 * default the stoichiometry endpoint succeeds for GET/POST/PUT and reports
 * success for DELETE. Individual tests can override handlers after this helper.
 */
function mockNetwork() {
  const recordRequest = async (request: Request): Promise<void> => {
    recordedRequests.push({
      url: new URL(request.url),
      method: request.method.toUpperCase(),
      body: request.method === "GET" ? null : await request.clone().text(),
    });
  };
  server.use(
    http.get("/userform/ajax/inventoryOauthToken", () => HttpResponse.json({ data: "test-token" })),
    http.get("/api/v1/stoichiometry", async ({ request }) => {
      await recordRequest(request);
      return HttpResponse.json(mockResponse);
    }),
    http.post("/api/v1/stoichiometry", async ({ request }) => {
      await recordRequest(request);
      return HttpResponse.json(mockResponse);
    }),
    http.put("/api/v1/stoichiometry", async ({ request }) => {
      await recordRequest(request);
      return HttpResponse.json(mockResponse);
    }),
    http.delete("/api/v1/stoichiometry", async ({ request }) => {
      await recordRequest(request);
      return HttpResponse.json({ success: true });
    }),
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  recordedRequests = [];
  mockNetwork();
  mockAxios.reset();
  mockAxios.onAny().reply(200, { data: {} });
});

afterEach(() => {
  mockAxios.reset();
});

describe("StoichiometryDialog", () => {
  it("shows calculate button when no table is present", async () => {
    render(<StoichiometryDialogWithCalculateButtonStory />);

    expect(await screen.findByRole("button", { name: "common:stoichiometry.dialog.calculate" })).toBeVisible();
  });

  it("calculate dialog has no accessibility violations", async () => {
    const { baseElement } = render(<StoichiometryDialogWithCalculateButtonStory />);

    await screen.findByRole("button", { name: "common:stoichiometry.dialog.calculate" });

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

    await user.click(await screen.findByRole("button", { name: "common:stoichiometry.dialog.calculate" }));

    expect(await screen.findByRole("grid")).toBeVisible();
    expect(onTableCreated).toHaveBeenCalled();
  });

  it("makes a POST API call when creating a new table", async () => {
    const user = userEvent.setup();

    render(<StoichiometryDialogWithCalculateButtonStory />);

    await user.click(await screen.findByRole("button", { name: "common:stoichiometry.dialog.calculate" }));

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

    server.use(
      http.post("/api/v1/stoichiometry", () =>
        HttpResponse.json(
          {
            status: "error",
            httpCode: 500,
            internalCode: 500,
            message: createErrorMessage,
            messageCode: null,
            errors: [],
            iso8601Timestamp: "2026-04-07T00:00:00Z",
            data: null,
          },
          { status: 500, statusText: "Internal Server Error" },
        ),
      ),
    );

    render(<StoichiometryDialogWithCalculateButtonStory />);

    await user.click(await screen.findByRole("button", { name: "common:stoichiometry.dialog.calculate" }));

    // The calculate button stays visible because the table was never created.
    expect(await screen.findByRole("button", { name: "common:stoichiometry.dialog.calculate" })).toBeVisible();
    const errorAlert = await screen.findByRole("alert");
    expect(errorAlert).toHaveTextContent(createErrorMessage);
  });

  it("does not show save button when table has not been modified", async () => {
    render(<StoichiometryDialogWithTableStory />);

    await screen.findByRole("grid");

    expect(screen.queryByRole("button", { name: "common:stoichiometry.dialog.saveChanges" })).not.toBeInTheDocument();
  });

  it("shows save button when limiting reagent is changed", async () => {
    const user = userEvent.setup();

    render(<StoichiometryDialogWithTableStory />);

    await screen.findByRole("grid");

    const radios = screen.getAllByRole("radio", {
      name: "common:stoichiometry.table.label.selectLimitingReagent",
    });
    await user.click(radios[1]);

    expect(await screen.findByRole("button", { name: "common:stoichiometry.dialog.saveChanges" })).toBeVisible();
  });

  it("hides save button after saving changes", async () => {
    const user = userEvent.setup();

    render(<StoichiometryDialogWithTableStory />);

    await screen.findByRole("grid");

    const radios = screen.getAllByRole("radio", {
      name: "common:stoichiometry.table.label.selectLimitingReagent",
    });
    await user.click(radios[1]);

    await user.click(await screen.findByRole("button", { name: "common:stoichiometry.dialog.saveChanges" }));

    await waitFor(() => {
      expect(screen.queryByRole("button", { name: "common:stoichiometry.dialog.saveChanges" })).not.toBeInTheDocument();
    });
  });

  it("makes a PUT API call when saving changes to the table", async () => {
    const user = userEvent.setup();

    render(<StoichiometryDialogWithTableStory />);

    await screen.findByRole("grid");

    const radios = screen.getAllByRole("radio", {
      name: "common:stoichiometry.table.label.selectLimitingReagent",
    });
    await user.click(radios[1]);

    await user.click(await screen.findByRole("button", { name: "common:stoichiometry.dialog.saveChanges" }));

    await waitFor(() => {
      expect(hasStoichiometryRequest("PUT")).toBe(true);
    });
  });

  it("shows delete button when table is present", async () => {
    render(<StoichiometryDialogWithTableStory />);

    await screen.findByRole("grid");

    expect(screen.getByRole("button", { name: "common:actions.delete" })).toBeVisible();
  });

  it("shows confirmation dialog when delete button is clicked", async () => {
    const user = userEvent.setup();

    render(<StoichiometryDialogWithTableStory />);

    await screen.findByRole("grid");

    await user.click(screen.getByRole("button", { name: "common:actions.delete" }));

    expect(
      await screen.findByRole("dialog", {
        name: "common:stoichiometry.dialog.deleteTitle",
      }),
    ).toBeVisible();
  });

  it("makes a DELETE API call and hides table when deletion is confirmed", async () => {
    const user = userEvent.setup();

    render(<StoichiometryDialogWithTableStory />);

    await screen.findByRole("grid");

    await user.click(screen.getByRole("button", { name: "common:actions.delete" }));

    const confirmDialog = await screen.findByRole("dialog", {
      name: "common:stoichiometry.dialog.deleteTitle",
    });

    await user.click(
      within(confirmDialog).getByRole("button", {
        name: "common:actions.delete",
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
