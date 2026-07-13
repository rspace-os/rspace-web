import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { worker } from "@/__tests__/browserSetup";
import { clickWhenInViewport, moveToastStackIntoViewport } from "@/__tests__/pageObjects/viewport";
import { FieldmarkImportDialogStory } from "./FieldmarkImportDialog.story";
import { FieldmarkImportDialogPage } from "./pageObjects/FieldmarkImportDialogPage";

/*
 * These two tests cover the toast/alert stack interaction: asserting that
 * the expand toggle is clickable and that the revealed sub-message link is
 * genuinely visible. Both rely on real element geometry (the toast container
 * sits at a negative top offset). The viewport helpers bring it into view
 * before interacting / asserting.
 *
 * The bulk of FieldmarkImportDialog's behaviour (notebook rendering, import
 * endpoint wiring, IGSN message, accessibility in the base case) is covered
 * by the jsdom unit test in FieldmarkImportDialog.test.tsx.
 */

const NOTEBOOKS_PAYLOAD = [
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

/** Default handlers for all igsnCandidateFields endpoints. */
const igsnCandidateFieldsHandlers = () => [
  http.get("/api/inventory/v1/fieldmark/notebooks/igsnCandidateFields", ({ request }) => {
    const url = new URL(request.url);
    const notebookId = url.searchParams.get("notebookId");
    switch (notebookId) {
      case "test-project-1":
        return HttpResponse.json(["sample_id", "batch_id"]);
      case "test-project-2":
        return HttpResponse.json([]);
      case "test-project-no-identifiers":
        return HttpResponse.json([]);
      case "test-project-with-identifiers":
        return HttpResponse.json(["sample_id", "batch_id"]);
      case "test-project-igsn-error":
        return HttpResponse.json(
          {
            data: {
              validationErrors: [{ message: "IGSN integration is not enabled" }],
            },
          },
          { status: 400 },
        );
      case "test-project-detailed-error":
        return HttpResponse.json(["identifier_field"]);
      default:
        return HttpResponse.json([]);
    }
  }),
];

/** Default successful import handler (used by test 1). */
const importSuccessHandler = () =>
  http.post("/api/inventory/v1/import/fieldmark/notebook", () =>
    HttpResponse.json({
      containerName: "Test Container from Test Notebook 1",
      containerGlobalId: "IC123456",
    }),
  );

const dialog = new FieldmarkImportDialogPage();

beforeEach(() => {
  worker.use(
    http.get("/api/inventory/v1/fieldmark/notebooks", () => HttpResponse.json(NOTEBOOKS_PAYLOAD)),
    ...igsnCandidateFieldsHandlers(),
    importSuccessHandler(),
  );
});

afterEach(() => {
  cleanup();
});

describe("FieldmarkImportDialog", () => {
  describe("notebook import", () => {
    test("should show success alert after import completes", async () => {
      render(<FieldmarkImportDialogStory />);

      // Wait for the dialog and data grid to render
      await expect.element(dialog.dialog).toBeVisible();
      await expect.element(dialog.dataGrid).toBeVisible();

      // Select the first notebook and click Import
      await dialog.selectNotebook("Test Notebook 1");
      await dialog.clickImport();

      // Bring the toast stack (fixed, with negative top offset) into viewport
      const toastsEl = document.querySelector('[data-testid="Toasts"]');
      if (toastsEl instanceof HTMLElement) {
        moveToastStackIntoViewport(toastsEl);
      }

      // The success alert should appear
      const successAlert = dialog.successAlert();
      await expect.element(successAlert).toBeVisible();
      await expect.element(successAlert).toHaveTextContent("Successfully imported notebook");

      // Expand sub-messages
      await clickWhenInViewport(dialog.subMessageToggle(1));

      // The sub-message link to the created container should be visible
      const subMessageAlert = successAlert
        .getByRole("alert")
        .filter({ hasText: "Test Container from Test Notebook 1" });
      const link = subMessageAlert.getByRole("link", { name: "IC123456" });
      await expect.element(link).toBeVisible();
      await expect.element(link).toHaveAttribute("href", "/globalId/IC123456");
    });

    test("should show detailed error message when import fails with validation errors", async () => {
      // Override import endpoint to return a 400 with validation errors when
      // the detailed-error notebook is submitted
      worker.use(
        http.post("/api/inventory/v1/import/fieldmark/notebook", async ({ request }) => {
          const body = (await request.json()) as Record<string, unknown>;
          if (body.notebookId === "test-project-detailed-error") {
            return HttpResponse.json(
              {
                status: "BAD_REQUEST",
                httpCode: 400,
                internalCode: 40002,
                message: "Errors detected : 2",
                messageCode: null,
                errors: [
                  'fieldmarkApiImportRequest: Error importing notebook "1726126204618-rspace-igsn-demo" from Fieldmark: Unable to find an existing assignable identifier: 10.82316/mq1c-b544',
                  "fieldmarkApiImportRequest: Additional validation error: Sample template validation failed",
                ],
                iso8601Timestamp: "2025-08-07T08:40:52.973881769Z",
                data: {
                  validationErrors: [
                    {
                      field: null,
                      rejectedValue: null,
                      objectName: "fieldmarkApiImportRequest",
                      message:
                        'Error importing notebook "1726126204618-rspace-igsn-demo" from Fieldmark: Unable to find an existing assignable identifier: 10.82316/mq1c-b544',
                    },
                    {
                      field: null,
                      rejectedValue: null,
                      objectName: "fieldmarkApiImportRequest",
                      message: "Additional validation error: Sample template validation failed",
                    },
                  ],
                },
              },
              { status: 400 },
            );
          }
          // Fallback for any other notebook
          return HttpResponse.json({
            containerName: "Test Container from Test Notebook 1",
            containerGlobalId: "IC123456",
          });
        }),
      );

      render(<FieldmarkImportDialogStory />);

      // Wait for the dialog and data grid to render
      await expect.element(dialog.dialog).toBeVisible();
      await expect.element(dialog.dataGrid).toBeVisible();

      // Select the "Notebook Detailed Error" notebook
      await dialog.selectNotebook("Notebook Detailed Error");

      // Select an IGSN candidate field from the combobox
      await dialog.selectIgsnOption("identifier_field");

      // Click Import
      await dialog.clickImport();

      // Bring the toast stack into viewport
      const toastsEl = document.querySelector('[data-testid="Toasts"]');
      if (toastsEl instanceof HTMLElement) {
        moveToastStackIntoViewport(toastsEl);
      }

      // The error alert should appear
      const errorAlert = dialog.errorAlert();
      await expect.element(errorAlert).toBeVisible();
      await expect.element(errorAlert).toHaveTextContent("Could not import notebook.");

      // Expand the 2 sub-messages
      await clickWhenInViewport(dialog.subMessageToggle(2));

      // Verify each detailed error message is visible
      const firstError = errorAlert.getByRole("alert").filter({
        hasText:
          'Error importing notebook "1726126204618-rspace-igsn-demo" from Fieldmark: Unable to find an existing assignable identifier: 10.82316/mq1c-b544',
      });
      await expect.element(firstError).toBeVisible();

      const secondError = errorAlert.getByRole("alert").filter({
        hasText: "Additional validation error: Sample template validation failed",
      });
      await expect.element(secondError).toBeVisible();
    });
  });
});
