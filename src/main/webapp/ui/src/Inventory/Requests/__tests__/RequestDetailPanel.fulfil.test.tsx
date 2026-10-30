import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import {
  addAlert,
  apiUpdate,
  baseRequest,
  failSampleRequestStatusUpdate,
  renderPanel,
  resetRequestDetailPanelFixtures,
  waitForInitialFetches,
} from "./RequestDetailPanel.fixtures";

beforeEach(() => {
  resetRequestDetailPanelFixtures();
});

// Covers FulfilRequestDialog, reached from an APPROVED request's "Mark as Fulfilled" button,
// without going through the Prepare/Transfer flow.
describe("RequestDetailPanel fulfil", () => {
  it("marks an approved request as fulfilled without transferring anything, and notifies listeners", async () => {
    const user = userEvent.setup();
    renderPanel(baseRequest({ status: "APPROVED" }));
    await waitForInitialFetches();

    await user.click(screen.getByRole("button", { name: "inventory:requestsManagement.detail.markAsFulfilledButton" }));
    // Gives a screen reader user a meaningful announcement on open, rather than just "dialog".
    const dialog = screen.getByRole("dialog", { name: "inventory:requestsManagement.detail.fulfilDialog.title" });
    await user.click(
      within(dialog).getByRole("button", { name: "inventory:requestsManagement.detail.fulfilDialog.fulfilButton" }),
    );

    await waitFor(() =>
      expect(apiUpdate).toHaveBeenCalledWith("sampleRequests", "101/status", { status: "FULFILLED" }),
    );
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(screen.getAllByText("inventory:requestsManagement.status.fulfilled").length).toBeGreaterThan(0);
  });

  it("keeps the Mark as Fulfilled dialog open, and does not notify listeners, when the fulfil call is rejected", async () => {
    const restoreConsole = silenceConsole(["error"], ["inventory:requestsManagement.detail.actionErrors.fulfil"]);
    const user = userEvent.setup();
    // A 409 here is exactly the scenario the fix guards against: someone else already rejected
    // or cancelled the request before this fulfil call reached the backend.
    failSampleRequestStatusUpdate(409);
    renderPanel(baseRequest({ status: "APPROVED" }));
    await waitForInitialFetches();

    await user.click(screen.getByRole("button", { name: "inventory:requestsManagement.detail.markAsFulfilledButton" }));
    const dialog = screen.getByRole("dialog");
    await user.click(
      within(dialog).getByRole("button", { name: "inventory:requestsManagement.detail.fulfilDialog.fulfilButton" }),
    );

    await waitFor(() =>
      expect(apiUpdate).toHaveBeenCalledWith("sampleRequests", "101/status", { status: "FULFILLED" }),
    );
    // The bug this guards against closed the dialog (and left the status chip reading Fulfilled)
    // as if the call had succeeded, even though it was rejected.
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.queryAllByText("inventory:requestsManagement.status.fulfilled")).toHaveLength(0);
    await waitFor(() => expect(addAlert).toHaveBeenCalled());
    expect(addAlert.mock.calls[0][0]).toMatchObject({
      variant: "error",
      message: "inventory:errors.genericActionError",
    });
    restoreConsole();
  });
});
