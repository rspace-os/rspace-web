import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import "@/__tests__/__mocks__/useOauthToken";
import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import { expectAccessible, render } from "@/__tests__/customQueries";
import { wrapWithRealI18n } from "@/__tests__/helpers/realI18n";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import axios from "@/common/axios";
import commonEn from "@/modules/common/i18n/locales/en-US/common.json";
import inventoryEn from "@/modules/common/i18n/locales/en-US/inventory.json";
import { FieldmarkImportDialogStory } from "./FieldmarkImportDialog.story";

const mockAxios = new MockAdapter(axios);

const NOTEBOOKS = [
  {
    name: "Test Notebook 1",
    metadata: {
      project_id: "test-project-1",
      ispublic: false,
      pre_description: "A test notebook for development",
      project_lead: "Test User 1",
    },
    status: "draft",
  },
  {
    name: "Test Notebook 2",
    metadata: {
      project_id: "test-project-2",
      ispublic: true,
      pre_description: "Another test notebook",
      project_lead: "Test User 2",
    },
    status: "published",
  },
  {
    name: "Notebook No Identifiers",
    metadata: {
      project_id: "test-project-no-identifiers",
      ispublic: false,
      pre_description: "A notebook with no identifier columns",
      project_lead: "Test User 3",
    },
    status: "draft",
  },
  {
    name: "Notebook With Identifiers",
    metadata: {
      project_id: "test-project-with-identifiers",
      ispublic: false,
      pre_description: "A notebook with identifier columns",
      project_lead: "Test User 4",
    },
    status: "draft",
  },
  {
    name: "Notebook IGSN Error",
    metadata: {
      project_id: "test-project-igsn-error",
      ispublic: false,
      pre_description: "A notebook that triggers IGSN error",
      project_lead: "Test User 5",
    },
    status: "draft",
  },
  {
    name: "Notebook Detailed Error",
    metadata: {
      project_id: "test-project-detailed-error",
      ispublic: false,
      pre_description: "A notebook that triggers detailed import error",
      project_lead: "Test User 6",
    },
    status: "draft",
  },
];

const IGSN_CANDIDATE_FIELDS: Record<string, ReadonlyArray<string>> = {
  "test-project-1": ["sample_id", "batch_id"],
  "test-project-2": [],
  "test-project-no-identifiers": [],
  "test-project-with-identifiers": ["sample_id", "batch_id"],
  "test-project-detailed-error": ["identifier_field"],
};

function stubEndpoints({ importResponder }: { importResponder?: (body: unknown) => [number, unknown] } = {}) {
  mockAxios.onGet("/api/inventory/v1/fieldmark/notebooks").reply(200, NOTEBOOKS);

  mockAxios.onGet(/\/api\/inventory\/v1\/fieldmark\/notebooks\/igsnCandidateFields/).reply((config) => {
    const notebookId = new URLSearchParams((config.url ?? "").split("?")[1] ?? "").get("notebookId");
    if (notebookId === "test-project-igsn-error") {
      return [
        400,
        {
          data: {
            validationErrors: [
              {
                message: "IGSN integration is not enabled",
              },
            ],
          },
        },
      ];
    }
    return [200, IGSN_CANDIDATE_FIELDS[notebookId ?? ""] ?? []];
  });

  mockAxios.onPost("/api/inventory/v1/import/fieldmark/notebook").reply((config) => {
    const body = config.data ? (JSON.parse(config.data as string) as unknown) : null;
    if (importResponder) return importResponder(body);
    return [
      200,
      {
        containerName: "Test Container from Test Notebook 1",
        containerGlobalId: "IC123456",
      },
    ];
  });
}

async function renderAndWaitForNotebooks(ui = <FieldmarkImportDialogStory />) {
  const result = render(ui);
  expect(await screen.findByRole("dialog")).toBeVisible();
  await waitFor(() => {
    expect(screen.getByRole("grid")).toBeVisible();
  });
  await waitFor(() => {
    expect(screen.getByRole("gridcell", { name: "Test Notebook 1" })).toBeVisible();
  });
  return result;
}

/**
 * Finds the radio button for a specific notebook by locating its row in the
 * DataGrid (the name cell uses data, not i18n, so it is always the real name).
 * The selectRadioAriaLabelFunc is mocked to return its i18n key (same for all
 * rows), so we cannot rely on aria-label to disambiguate rows.
 */
function getRadioForNotebook(notebookName: string): HTMLElement {
  const nameCell = screen.getByRole("gridcell", { name: notebookName });
  const row = nameCell.closest('[role="row"]') as HTMLElement;
  return within(row).getByRole("radio");
}

describe("FieldmarkImportDialog", () => {
  beforeEach(() => {
    mockAxios.reset();
    stubEndpoints();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe("accessibility", () => {
    test("should not have any accessibility issues when open", async () => {
      const { baseElement } = await renderAndWaitForNotebooks();
      await expectAccessible(baseElement);
    });
  });

  describe("notebook fetching", () => {
    test("should fetch and display notebooks when opened", async () => {
      await renderAndWaitForNotebooks();

      expect(screen.getByRole("gridcell", { name: "Test Notebook 1" })).toBeVisible();
      expect(screen.getByRole("gridcell", { name: "Test Notebook 2" })).toBeVisible();
      expect(screen.getByRole("gridcell", { name: "Notebook No Identifiers" })).toBeVisible();
      expect(screen.getByRole("gridcell", { name: "Notebook With Identifiers" })).toBeVisible();
      expect(screen.getAllByRole("gridcell", { name: "draft" })[0]).toBeVisible();
      expect(screen.getByRole("gridcell", { name: "published" })).toBeVisible();
      // The MUI Radio renders its <input> as visually-hidden (the styled span
      // is what is painted), so we assert the selection control is present
      // rather than `toBeVisible` (which jsdom evaluates against the hidden
      // input element and would fail without real layout).
      expect(getRadioForNotebook("Test Notebook 1")).toBeInTheDocument();
      expect(getRadioForNotebook("Test Notebook 2")).toBeInTheDocument();
      expect(getRadioForNotebook("Notebook No Identifiers")).toBeInTheDocument();
      expect(getRadioForNotebook("Notebook With Identifiers")).toBeInTheDocument();
    });
  });

  describe("notebook import", () => {
    test("should make an import request when a notebook is selected and imported", async () => {
      const user = userEvent.setup();
      await renderAndWaitForNotebooks();

      await user.click(getRadioForNotebook("Test Notebook 1"));
      await user.click(screen.getByRole("button", { name: "common:actions.import" }));

      await waitFor(() => {
        const importRequest = mockAxios.history.post.find(
          (request) =>
            request.url === "/api/inventory/v1/import/fieldmark/notebook" &&
            (request.data as string)?.includes('"notebookId":"test-project-1"'),
        );
        expect(importRequest).toBeDefined();
      });
    });

    test("should make an import request without identifier column when notebook has no identifier columns", async () => {
      const user = userEvent.setup();
      await renderAndWaitForNotebooks();

      await user.click(getRadioForNotebook("Notebook No Identifiers"));
      await user.click(screen.getByRole("button", { name: "common:actions.import" }));

      await waitFor(() => {
        const importRequest = mockAxios.history.post.find(
          (request) =>
            request.url === "/api/inventory/v1/import/fieldmark/notebook" &&
            (request.data as string)?.includes('"notebookId":"test-project-no-identifiers"') &&
            !(request.data as string)?.includes('"identifier"'),
        );
        expect(importRequest).toBeDefined();
      });
    });

    test("should make an import request with selected identifier column when notebook has identifier columns", async () => {
      const user = userEvent.setup();
      await renderAndWaitForNotebooks();

      await user.click(getRadioForNotebook("Notebook With Identifiers"));
      // The IGSN ID field select appears once the candidate fields are fetched
      const identifierSelect = await screen.findByRole("combobox", {
        name: "inventory:fieldmarkImport.igsnField.label",
      });
      fireEvent.mouseDown(identifierSelect);
      fireEvent.click(await screen.findByRole("option", { name: "sample_id" }));
      await user.click(screen.getByRole("button", { name: "common:actions.import" }));

      await waitFor(() => {
        const importRequest = mockAxios.history.post.find(
          (request) =>
            request.url === "/api/inventory/v1/import/fieldmark/notebook" &&
            (request.data as string)?.includes('"notebookId":"test-project-with-identifiers"') &&
            (request.data as string)?.includes('"identifier":"sample_id"'),
        );
        expect(importRequest).toBeDefined();
      });
    });

    test("should show importing alert during import process", async () => {
      // Delay the import response so the (infinite) "Importing notebook" alert
      // stays visible long enough to assert on.
      let resolveImport: (() => void) | null = null;
      stubEndpoints();
      mockAxios.onPost("/api/inventory/v1/import/fieldmark/notebook").reply(
        () =>
          new Promise((resolve) => {
            resolveImport = () =>
              resolve([
                200,
                {
                  containerName: "Test Container from Test Notebook 1",
                  containerGlobalId: "IC123456",
                },
              ]);
          }),
      );

      const user = userEvent.setup();
      await renderAndWaitForNotebooks(
        await wrapWithRealI18n(<FieldmarkImportDialogStory />, {
          resources: { common: commonEn, inventory: inventoryEn },
          defaultNS: "inventory",
        }),
      );

      await user.click(getRadioForNotebook("Test Notebook 1"));
      await user.click(screen.getByRole("button", { name: "Import" }));

      const alert = await screen.findByText("Importing notebook");
      const alertContainer = alert.closest('[role="group"]') as HTMLElement;
      expect(alertContainer).toBeVisible();
      expect(alertContainer).toHaveTextContent('Importing notebook "Test Notebook 1" from Fieldmark.');

      // Release the pending import so the test can clean up without dangling
      // promises.
      (resolveImport as (() => void) | null)?.();
    });

    test("should show loading state on import button during import", async () => {
      let resolveImport: (() => void) | null = null;
      stubEndpoints();
      mockAxios.onPost("/api/inventory/v1/import/fieldmark/notebook").reply(
        () =>
          new Promise((resolve) => {
            resolveImport = () =>
              resolve([
                200,
                {
                  containerName: "Test Container from Test Notebook 1",
                  containerGlobalId: "IC123456",
                },
              ]);
          }),
      );

      const user = userEvent.setup();
      await renderAndWaitForNotebooks();

      await user.click(getRadioForNotebook("Test Notebook 1"));
      await user.click(screen.getByRole("button", { name: "common:actions.import" }));

      await waitFor(() => {
        expect(screen.getByRole("button", { name: "common:actions.import" })).toBeDisabled();
      });

      (resolveImport as (() => void) | null)?.();
    });

    test("should display IGSN message when igsnCandidateFields endpoint returns IGSN error", async () => {
      const restoreConsole = silenceConsole(["error"], [/.*/]);
      try {
        const user = userEvent.setup();
        await renderAndWaitForNotebooks(
          await wrapWithRealI18n(<FieldmarkImportDialogStory />, {
            resources: { inventory: inventoryEn },
            defaultNS: "inventory",
          }),
        );

        await user.click(getRadioForNotebook("Notebook IGSN Error"));

        const integrationLink = await screen.findByRole("link", { name: "DataCite IGSN ID integration" });
        expect(integrationLink).toHaveAttribute(
          "href",
          expect.stringContaining("add-igsn-identifiers-to-your-samples"),
        );
      } finally {
        restoreConsole();
      }
    });

    test("should hide identifier parsing UI during import when identifier field is unselected", async () => {
      let resolveImport: (() => void) | null = null;
      stubEndpoints();
      mockAxios.onPost("/api/inventory/v1/import/fieldmark/notebook").reply(
        () =>
          new Promise((resolve) => {
            resolveImport = () =>
              resolve([
                200,
                {
                  containerName: "Test Container from Notebook With Identifiers",
                  containerGlobalId: "IC789012",
                },
              ]);
          }),
      );

      const user = userEvent.setup();
      await renderAndWaitForNotebooks();

      await user.click(getRadioForNotebook("Notebook With Identifiers"));
      await screen.findByRole("combobox", { name: "inventory:fieldmarkImport.igsnField.label" });
      await user.click(screen.getByRole("button", { name: "common:actions.import" }));

      expect(await screen.findByText("inventory:fieldmarkImport.importNotebook.title")).toBeVisible();
      expect(
        screen.queryByRole("combobox", { name: "inventory:fieldmarkImport.igsnField.label" }),
      ).not.toBeInTheDocument();
      expect(screen.queryByText("inventory:fieldmarkImport.igsnField.loading")).not.toBeInTheDocument();

      (resolveImport as (() => void) | null)?.();
    });
  });
});
