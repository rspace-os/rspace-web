import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import {
  baseRequest,
  deploymentProperties,
  REQUESTER,
  renderPanel,
  resetRequestDetailPanelFixtures,
  waitForInitialFetches,
} from "./RequestDetailPanel.fixtures";

beforeEach(() => {
  resetRequestDetailPanelFixtures();
});

// Covers OwnerActionsPanel's Prepare/Transfer Sample button (which of the two labels it shows,
// and when each is enabled), and ChooseMethodDialog. TransferOwnershipDialog's own content and the
// full prepare-then-transfer outcomes have their own sibling test file; what proceeding with the
// "wizard" radio actually launches is covered in RequestDetailPanel.test.tsx.
describe("RequestDetailPanel choose method", () => {
  it("skips the Choose Sample to Prepare dialog and goes straight to Transfer when inventory.sampleRequests.available is on and inventory.operations.available is off", async () => {
    const user = userEvent.setup();
    deploymentProperties["inventory.sampleRequests.available"] = "ALLOWED";
    deploymentProperties["inventory.operations.available"] = "DENIED";
    renderPanel(baseRequest({ status: "APPROVED" }));
    await waitForInitialFetches();

    await user.click(screen.getByRole("button", { name: "mock-select-subsample" }));
    const transferButton = screen.getByRole("button", {
      name: "inventory:requestsManagement.detail.transferSampleButton",
    });
    expect(
      screen.queryByRole("button", { name: "inventory:requestsManagement.detail.prepareSampleButton" }),
    ).toBeNull();

    await user.click(transferButton);

    expect(
      screen.queryByRole("dialog", { name: "inventory:requestsManagement.detail.chooseMethodDialog.title" }),
    ).toBeNull();
    const transferDialog = await screen.findByRole("dialog", {
      name: "inventory:requestsManagement.detail.transferDialog.heading",
    });
    expect(within(transferDialog).getByText(`${REQUESTER.firstName} ${REQUESTER.lastName}`)).toBeInTheDocument();
  });

  it("keeps the Prepare Sample label and dialog when only one of the two properties allows skipping", async () => {
    deploymentProperties["inventory.sampleRequests.available"] = "ALLOWED";
    deploymentProperties["inventory.operations.available"] = "ALLOWED";
    renderPanel(baseRequest({ status: "APPROVED" }));
    await waitForInitialFetches();

    expect(
      screen.getByRole("button", { name: "inventory:requestsManagement.detail.prepareSampleButton" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "inventory:requestsManagement.detail.transferSampleButton" }),
    ).toBeNull();
  });

  it("keeps the Prepare Sample button disabled until a subsample is selected when operations.available is on", async () => {
    deploymentProperties["inventory.sampleRequests.available"] = "DENIED";
    deploymentProperties["inventory.operations.available"] = "ALLOWED";
    renderPanel(baseRequest({ status: "APPROVED" }));
    await waitForInitialFetches();

    expect(
      screen.getByRole("button", { name: "inventory:requestsManagement.detail.prepareSampleButton" }),
    ).toBeDisabled();
  });

  it("enables the Transfer Sample button without a subsample selection when operations.available is off, since the transfer applies to the whole sample regardless", async () => {
    deploymentProperties["inventory.sampleRequests.available"] = "ALLOWED";
    deploymentProperties["inventory.operations.available"] = "DENIED";
    renderPanel(baseRequest({ status: "APPROVED" }));
    await waitForInitialFetches();

    // No "mock-select-subsample" click here - the bug this guards against required one before the
    // button (which transfers the whole sample, not a particular subsample, on this path) would
    // ever enable.
    expect(
      screen.getByRole("button", { name: "inventory:requestsManagement.detail.transferSampleButton" }),
    ).toBeEnabled();
    expect(
      screen.getByText("inventory:requestsManagement.detail.preparationHint.approvedReadyToTransfer"),
    ).toBeInTheDocument();
  });

  it("shows the generic 'prepare a subsample' hint for a pending request when operations.available is on", async () => {
    deploymentProperties["inventory.sampleRequests.available"] = "DENIED";
    deploymentProperties["inventory.operations.available"] = "ALLOWED";
    renderPanel(baseRequest({ status: "PENDING" }));
    await waitForInitialFetches();

    expect(screen.getByText("inventory:requestsManagement.detail.preparationHint.pending")).toBeInTheDocument();
    expect(screen.queryByText("inventory:requestsManagement.detail.preparationHint.pendingTransferOnly")).toBeNull();
  });

  it("shows the transfer-only hint for a pending request when operations.available is off", async () => {
    deploymentProperties["inventory.sampleRequests.available"] = "ALLOWED";
    deploymentProperties["inventory.operations.available"] = "DENIED";
    renderPanel(baseRequest({ status: "PENDING" }));
    await waitForInitialFetches();

    // The bug this guards against: the generic hint mentions "preparing a subsample", which isn't
    // accurate once the Operations Wizard is unavailable - every route from here ends in
    // transferring the whole sample directly, regardless of any particular subsample.
    expect(
      screen.getByText("inventory:requestsManagement.detail.preparationHint.pendingTransferOnly"),
    ).toBeInTheDocument();
    expect(screen.queryByText("inventory:requestsManagement.detail.preparationHint.pending")).toBeNull();
  });
});
