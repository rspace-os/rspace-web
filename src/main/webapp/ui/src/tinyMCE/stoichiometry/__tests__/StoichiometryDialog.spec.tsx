import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import React from "react";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { worker } from "@/__tests__/browserSetup";
import { emulateHighContrast, expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import StoichiometryDialogEntrypoint from "../StoichiometryDialogEntrypoint";
import { chemistryIntegrationHandler, createDialogStoichiometryResponse } from "./mocks/stoichiometryMocks";
import { StoichiometryDialogPage } from "./pageObjects/StoichiometryDialogPage";
import {
  StoichiometryDialogWithCalculateButtonStory,
  StoichiometryDialogWithTableStory,
} from "./StoichiometryDialog.story";

type CalledSpy = {
  handler: () => void;
  hasBeenCalled: () => boolean;
};

const createCalledSpy = (): CalledSpy => {
  let called = false;
  return {
    handler: () => {
      called = true;
    },
    hasBeenCalled: () => called,
  };
};

type NetworkRequest = { method: string; pathname: string };

const networkRequests: Array<NetworkRequest> = [];

const hasStoichiometryRequest = (method: "POST" | "PUT" | "DELETE"): boolean =>
  networkRequests.some((request) => request.pathname.includes("/api/v1/stoichiometry") && request.method === method);

const dialogStoichiometryHandlers = () => {
  const response = createDialogStoichiometryResponse();
  return [
    http.get("/api/v1/stoichiometry", () => HttpResponse.json(response)),
    http.post("/api/v1/stoichiometry", () => HttpResponse.json(response)),
    http.put("/api/v1/stoichiometry", () => HttpResponse.json(response)),
    http.delete("/api/v1/stoichiometry", () => HttpResponse.json({ success: true })),
  ];
};

const dialog = new StoichiometryDialogPage();

beforeEach(() => {
  networkRequests.length = 0;
  worker.use(chemistryIntegrationHandler(), ...dialogStoichiometryHandlers());
  worker.events.on("request:start", ({ request }) => {
    networkRequests.push({
      method: request.method,
      pathname: new URL(request.url).pathname,
    });
  });
});

afterEach(() => {
  worker.events.removeAllListeners();
  cleanup();
});

describe("Stoichiometry Dialog", () => {
  test("shows calculate button when no table is present", async () => {
    render(<StoichiometryDialogWithCalculateButtonStory />);
    await expect.element(dialog.calculateButton).toBeVisible();
  });

  test("calculate dialog has no accessibility violations", async () => {
    render(<StoichiometryDialogWithCalculateButtonStory />);
    await expect.element(dialog.calculateButton).toBeVisible();
    await expectNoAxeViolations();
  });

  test("displays stoichiometry table when data is available", async () => {
    render(<StoichiometryDialogWithTableStory />);
    await expect.element(dialog.table).toBeVisible();
  });

  test("Has no accessibility violations", async () => {
    render(<StoichiometryDialogWithTableStory />);
    await expect.element(dialog.table).toBeVisible();
    await expectNoAxeViolations();
  });

  test("supports high-contrast mode", async () => {
    await emulateHighContrast();
    render(<StoichiometryDialogWithTableStory />);
    await expect.element(dialog.table).toBeVisible();
    await expectNoAxeViolations();
  });

  test("invokes callback when table is successfully created", async () => {
    const onTableCreatedSpy = createCalledSpy();
    render(<StoichiometryDialogWithCalculateButtonStory onTableCreated={onTableCreatedSpy.handler} />);
    await dialog.clickCalculate();
    await expect.element(dialog.table).toBeVisible();
    expect(onTableCreatedSpy.hasBeenCalled()).toBe(true);
  });

  test("makes a POST API call when creating a new table", async () => {
    render(<StoichiometryDialogWithCalculateButtonStory />);
    await dialog.clickCalculate();
    await expect.element(dialog.table).toBeVisible();
    await expect.poll(() => hasStoichiometryRequest("POST")).toBe(true);
  });

  test("shows an inline error when creating a new table fails", async () => {
    const createErrorMessage = "Unable to create stoichiometry table.";
    worker.use(
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
          { status: 500 },
        ),
      ),
    );

    render(<StoichiometryDialogWithCalculateButtonStory />);
    await dialog.clickCalculate();
    await expect.element(dialog.calculateButton).toBeVisible();
    await expect.element(dialog.inlineError(createErrorMessage)).toBeVisible();
  });

  test("does not show save button when table has not been modified", async () => {
    render(<StoichiometryDialogWithTableStory />);
    await expect.element(dialog.table).toBeVisible();
    await expect.element(dialog.saveButton).not.toBeInTheDocument();
  });

  test("shows save button when table data is modified by editing a cell", async () => {
    render(<StoichiometryDialogWithTableStory />);
    await expect.element(dialog.table).toBeVisible();
    await dialog.editFirstMassCell();
    await expect.element(dialog.saveButton).toBeVisible();
  });

  test("shows save button when limiting reagent is changed", async () => {
    render(<StoichiometryDialogWithTableStory />);
    await expect.element(dialog.table).toBeVisible();
    await dialog.selectLimitingReagent("Cyclopentadiene");
    await expect.element(dialog.saveButton).toBeVisible();
  });

  test("hides save button after saving changes", async () => {
    render(<StoichiometryDialogWithTableStory />);
    await expect.element(dialog.table).toBeVisible();
    await dialog.editFirstMassCell();
    await dialog.saveChanges();
    await expect.element(dialog.saveButton).not.toBeInTheDocument();
  });

  test("makes a PUT API call when saving changes to the table", async () => {
    render(<StoichiometryDialogWithTableStory />);
    await expect.element(dialog.table).toBeVisible();
    await dialog.editFirstMassCell();
    await dialog.saveChanges();
    await expect.poll(() => hasStoichiometryRequest("PUT")).toBe(true);
  });

  test("shows delete button when table is present", async () => {
    render(<StoichiometryDialogWithTableStory />);
    await expect.element(dialog.table).toBeVisible();
    await expect.element(dialog.deleteButton).toBeVisible();
  });

  test("shows confirmation dialog when delete button is clicked", async () => {
    render(<StoichiometryDialogWithTableStory />);
    await expect.element(dialog.table).toBeVisible();
    await dialog.clickDelete();
    await expect.element(dialog.deleteConfirmationDialog).toBeVisible();
  });

  test("makes a DELETE API call and hides table when deletion is confirmed", async () => {
    // The dialog delegates hiding the table to its parent (it calls onDelete);
    // this stateful wrapper clears the stoichiometry id on delete, mirroring
    // how the document editor drops the table from the page after deletion.
    const DeletableDialog = () => {
      const [stoichiometryId, setStoichiometryId] = React.useState<number | undefined>(1);
      return (
        <StoichiometryDialogEntrypoint
          open
          onClose={() => {}}
          chemId={12345}
          recordId={1}
          stoichiometryId={stoichiometryId}
          stoichiometryRevision={1}
          onDelete={() => setStoichiometryId(undefined)}
        />
      );
    };

    render(<DeletableDialog />);
    await expect.element(dialog.table).toBeVisible();
    await dialog.clickDelete();
    await dialog.confirmDeletion();
    await expect.poll(() => hasStoichiometryRequest("DELETE")).toBe(true);
    await expect.element(dialog.table).not.toBeInTheDocument();
  });
});
