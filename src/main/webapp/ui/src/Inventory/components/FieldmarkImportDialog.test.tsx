/*
 * Vitest/jsdom conversion of FieldmarkImportDialog.spec.tsx.
 *
 * The two alert tests that depend on real toast geometry (clicking the
 * sub-message expand toggle on a `position:fixed`, off-viewport toast stack and
 * asserting that the revealed link / sub-message is actually visible) remain in
 * FieldmarkImportDialog.spec.tsx because they need real layout / scroll
 * position. Everything else is converted here.
 */
import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import "@/__tests__/__mocks__/useOauthToken";
import React from "react";
import { screen, waitFor, fireEvent } from "@testing-library/react";
import { render, expectAccessible} from "@/__tests__/customQueries";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";
import { FieldmarkImportDialogStory } from "./FieldmarkImportDialog.story";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";

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

/**
 * Mirrors the spec's `router.route(...)` stubs. The default
 * import endpoint resolves successfully; individual tests override it.
 */
function stubEndpoints({
  importResponder,
}: {
  importResponder?: (body: unknown) => [number, unknown];
} = {}) {
  mockAxios
    .onGet("/api/inventory/v1/fieldmark/notebooks")
    .reply(200, NOTEBOOKS);

  mockAxios
    .onGet(/\/api\/inventory\/v1\/fieldmark\/notebooks\/igsnCandidateFields/)
    .reply((config) => {
      const notebookId = new URLSearchParams(
        (config.url ?? "").split("?")[1] ?? "",
      ).get("notebookId");
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

  mockAxios
    .onPost("/api/inventory/v1/import/fieldmark/notebook")
    .reply((config) => {
      const body = config.data
        ? (JSON.parse(config.data as string) as unknown)
        : null;
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

/**
 * Renders the dialog and waits for the notebooks DataGrid to appear, mirroring
 * the spec's `Given["...mounted"]` + `When["the notebooks have been fetched"]`.
 */
async function renderAndWaitForNotebooks() {
  const result = render(<FieldmarkImportDialogStory />);
  expect(await screen.findByRole("dialog")).toBeVisible();
  await waitFor(() => {
    expect(screen.getByRole("grid")).toBeVisible();
  });
  // Wait for the rows to be populated (loading -> data)
  await waitFor(() => {
    expect(
      screen.getByRole("gridcell", { name: /^Test Notebook 1$/ }),
    ).toBeVisible();
  });
  return result;
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

      expect(
        screen.getByRole("gridcell", { name: /^Test Notebook 1$/ }),
      ).toBeVisible();
      expect(
        screen.getByRole("gridcell", { name: /^Test Notebook 2$/ }),
      ).toBeVisible();
      expect(
        screen.getByRole("gridcell", { name: /^Notebook No Identifiers$/ }),
      ).toBeVisible();
      expect(
        screen.getByRole("gridcell", { name: /^Notebook With Identifiers$/ }),
      ).toBeVisible();
      expect(
        screen.getAllByRole("gridcell", { name: "draft" })[0],
      ).toBeVisible();
      expect(
        screen.getByRole("gridcell", { name: "published" }),
      ).toBeVisible();
      // The MUI Radio renders its <input> as visually-hidden (the styled span
      // is what is painted), so we assert the selection control is present
      // rather than `toBeVisible` (which jsdom evaluates against the hidden
      // input element and would fail without real layout).
      expect(
        screen.getByRole("radio", { name: "Select notebook: Test Notebook 1" }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("radio", { name: "Select notebook: Test Notebook 2" }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("radio", {
          name: "Select notebook: Notebook No Identifiers",
        }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("radio", {
          name: "Select notebook: Notebook With Identifiers",
        }),
      ).toBeInTheDocument();
    });
  });

  describe("notebook import", () => {
    test("should make an import request when a notebook is selected and imported", async () => {
      const user = userEvent.setup();
      await renderAndWaitForNotebooks();

      await user.click(
        screen.getByRole("radio", { name: "Select notebook: Test Notebook 1" }),
      );
      await user.click(screen.getByRole("button", { name: "Import" }));

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

      await user.click(
        screen.getByRole("radio", {
          name: "Select notebook: Notebook No Identifiers",
        }),
      );
      await user.click(screen.getByRole("button", { name: "Import" }));

      await waitFor(() => {
        const importRequest = mockAxios.history.post.find(
          (request) =>
            request.url === "/api/inventory/v1/import/fieldmark/notebook" &&
            (request.data as string)?.includes(
              '"notebookId":"test-project-no-identifiers"',
            ) &&
            !(request.data as string)?.includes('"identifier"'),
        );
        expect(importRequest).toBeDefined();
      });
    });

    test("should make an import request with selected identifier column when notebook has identifier columns", async () => {
      const user = userEvent.setup();
      await renderAndWaitForNotebooks();

      await user.click(
        screen.getByRole("radio", {
          name: "Select notebook: Notebook With Identifiers",
        }),
      );
      // The IGSN ID field select appears once the candidate fields are fetched
      const identifierSelect = await screen.findByRole("combobox", {
        name: /IGSN ID Field/i,
      });
      fireEvent.mouseDown(identifierSelect);
      fireEvent.click(
        await screen.findByRole("option", { name: "sample_id" }),
      );
      await user.click(screen.getByRole("button", { name: "Import" }));

      await waitFor(() => {
        const importRequest = mockAxios.history.post.find(
          (request) =>
            request.url === "/api/inventory/v1/import/fieldmark/notebook" &&
            (request.data as string)?.includes(
              '"notebookId":"test-project-with-identifiers"',
            ) &&
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
      mockAxios
        .onPost("/api/inventory/v1/import/fieldmark/notebook")
        .reply(
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

      await user.click(
        screen.getByRole("radio", { name: "Select notebook: Test Notebook 1" }),
      );
      await user.click(screen.getByRole("button", { name: "Import" }));

      const alert = await screen.findByText("Importing notebook");
      const alertContainer = alert.closest('[role="group"]') as HTMLElement;
      expect(alertContainer).toBeVisible();
      expect(alertContainer).toHaveTextContent("Importing notebook");
      expect(alertContainer).toHaveTextContent("Test Notebook 1");

      // Release the pending import so the test can clean up without dangling
      // promises.
      (resolveImport as (() => void) | null)?.();
    });

    test("should show loading state on import button during import", async () => {
      let resolveImport: (() => void) | null = null;
      stubEndpoints();
      mockAxios
        .onPost("/api/inventory/v1/import/fieldmark/notebook")
        .reply(
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

      await user.click(
        screen.getByRole("radio", { name: "Select notebook: Test Notebook 1" }),
      );
      await user.click(screen.getByRole("button", { name: "Import" }));

      await waitFor(() => {
        expect(screen.getByRole("button", { name: "Import" })).toBeDisabled();
      });

      (resolveImport as (() => void) | null)?.();
    });

    test("should display IGSN message when igsnCandidateFields endpoint returns IGSN error", async () => {
      // The component logs the rejected igsnCandidateFields request.
      const restoreConsole = silenceConsole(["error"], [/.*/]);
      try {
        const user = userEvent.setup();
        await renderAndWaitForNotebooks();

        await user.click(
          screen.getByRole("radio", {
            name: "Select notebook: Notebook IGSN Error",
          }),
        );

        expect(
          await screen.findByText(
            "RSpace can link pre-registered IGSN IDs with samples imported by Fieldmark.",
            { exact: false },
          ),
        ).toBeVisible();
      } finally {
        restoreConsole();
      }
    });

    test("should hide identifier parsing UI during import when identifier field is unselected", async () => {
      let resolveImport: (() => void) | null = null;
      stubEndpoints();
      mockAxios
        .onPost("/api/inventory/v1/import/fieldmark/notebook")
        .reply(
          () =>
            new Promise((resolve) => {
              resolveImport = () =>
                resolve([
                  200,
                  {
                    containerName:
                      "Test Container from Notebook With Identifiers",
                    containerGlobalId: "IC789012",
                  },
                ]);
            }),
        );

      const user = userEvent.setup();
      await renderAndWaitForNotebooks();

      await user.click(
        screen.getByRole("radio", {
          name: "Select notebook: Notebook With Identifiers",
        }),
      );
      // Wait for the identifier select to appear, then import WITHOUT selecting
      // an identifier field (selection stays "unselected").
      await screen.findByRole("combobox", { name: /IGSN ID Field/i });
      await user.click(screen.getByRole("button", { name: "Import" }));

      // The importing alert should appear...
      expect(await screen.findByText("Importing notebook")).toBeVisible();
      // ...and the identifier parsing UI should be hidden during import.
      expect(
        screen.queryByRole("combobox", { name: /IGSN ID Field/i }),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByText("Loading available IGSN ID fields..."),
      ).not.toBeInTheDocument();

      (resolveImport as (() => void) | null)?.();
    });
  });
});
