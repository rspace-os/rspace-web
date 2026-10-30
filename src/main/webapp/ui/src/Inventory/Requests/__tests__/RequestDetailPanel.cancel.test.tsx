import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import {
  addAlert,
  apiUpdate,
  baseRequest,
  currentUser,
  failSampleRequestStatusUpdate,
  REQUESTER,
  renderPanel,
  resetRequestDetailPanelFixtures,
  waitForInitialFetches,
} from "./RequestDetailPanel.fixtures";

beforeEach(() => {
  resetRequestDetailPanelFixtures();
});

// Covers RequesterCancelPanel: matches the "Cancel" button behaviour in the Sample form's
// "Request this sample" box, only ever shown to the requester while PENDING.
describe("RequestDetailPanel cancel", () => {
  it("cancels the request as a non-owner and notifies listeners", async () => {
    const user = userEvent.setup();
    currentUser.value = REQUESTER;
    renderPanel(baseRequest());
    await waitForInitialFetches();

    await user.click(screen.getByRole("button", { name: "common:actions.cancel" }));

    await waitFor(() =>
      expect(apiUpdate).toHaveBeenCalledWith("sampleRequests", "101/status", { status: "CANCELLED" }),
    );
    // The status chip updating confirms the mutation's own response was written into the detail
    // query's cache (see invalidateAndSeedDetailStatus in ../mutations.ts) - the replacement for
    // the old window-event broadcast this test used to listen for directly.
    await waitFor(() =>
      expect(screen.getAllByText("inventory:requestsManagement.status.cancelled").length).toBeGreaterThan(0),
    );
  });

  it("shows an error alert and leaves the status unchanged when cancelling fails", async () => {
    const restoreConsole = silenceConsole(["error"], ["inventory:requestsManagement.detail.actionErrors.cancel"]);
    const user = userEvent.setup();
    currentUser.value = REQUESTER;
    failSampleRequestStatusUpdate();
    renderPanel(baseRequest());
    await waitForInitialFetches();

    await user.click(screen.getByRole("button", { name: "common:actions.cancel" }));

    await waitFor(() => expect(addAlert).toHaveBeenCalled());
    expect(addAlert.mock.calls[0][0]).toMatchObject({
      variant: "error",
      message: "inventory:errors.genericActionError",
    });
    expect(screen.queryAllByText("inventory:requestsManagement.status.cancelled")).toHaveLength(0);
    restoreConsole();
  });
});
