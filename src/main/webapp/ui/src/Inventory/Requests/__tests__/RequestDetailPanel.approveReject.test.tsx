import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it } from "vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import { server } from "@/__tests__/mswServer";
import {
  addAlert,
  apiUpdate,
  baseRequest,
  failSampleRequestStatusUpdate,
  renderPanel,
  resetRequestDetailPanelFixtures,
  SAMPLE_REQUEST_STATUS_URL,
  waitForInitialFetches,
} from "./RequestDetailPanel.fixtures";

beforeEach(() => {
  resetRequestDetailPanelFixtures();
});

// Covers OwnerActionsPanel's Approve/Reject buttons and RejectRequestDialog.
describe("RequestDetailPanel approve/reject", () => {
  it("approves a pending request and notifies listeners", async () => {
    const user = userEvent.setup();
    renderPanel(baseRequest());
    await waitForInitialFetches();

    await user.click(screen.getByRole("button", { name: "inventory:requestsManagement.detail.approveButton" }));

    await waitFor(() => expect(apiUpdate).toHaveBeenCalledWith("sampleRequests", "101/status", { status: "APPROVED" }));
    // Replaces the old window-event assertion: the status chip updating proves the mutation
    // notified every other sampleRequests query to refresh (see ../mutations.ts).
    await waitFor(() =>
      expect(screen.getAllByText("inventory:requestsManagement.status.approved").length).toBeGreaterThan(0),
    );
  });

  it("shows an error alert, leaves the status unchanged, and re-enables the button when approving fails", async () => {
    const restoreConsole = silenceConsole(["error"], ["inventory:requestsManagement.detail.actionErrors.approve"]);
    const user = userEvent.setup();
    failSampleRequestStatusUpdate();
    renderPanel(baseRequest());
    await waitForInitialFetches();

    const approveButton = screen.getByRole("button", { name: "inventory:requestsManagement.detail.approveButton" });
    await user.click(approveButton);

    await waitFor(() => expect(addAlert).toHaveBeenCalled());
    expect(addAlert.mock.calls[0][0]).toMatchObject({
      variant: "error",
      message: "inventory:errors.genericActionError",
    });
    expect(screen.queryAllByText("inventory:requestsManagement.status.approved")).toHaveLength(0);
    await waitFor(() => expect(approveButton).toBeEnabled());
    restoreConsole();
  });

  it("disables the Approve button while a request is in flight, preventing a duplicate call", async () => {
    const user = userEvent.setup();
    let resolveUpdate: (() => void) | undefined;
    server.use(
      http.put(SAMPLE_REQUEST_STATUS_URL, async ({ params, request }) => {
        const body = (await request.json()) as { status: string };
        apiUpdate("sampleRequests", `${params.id}/status`, body);
        await new Promise<void>((resolve) => {
          resolveUpdate = resolve;
        });
        return HttpResponse.json({ status: body.status });
      }),
    );
    renderPanel(baseRequest());
    await waitForInitialFetches();

    const approveButton = screen.getByRole("button", { name: "inventory:requestsManagement.detail.approveButton" });
    await user.click(approveButton);
    await waitFor(() => expect(approveButton).toBeDisabled());
    // A disabled button is a no-op click as far as a real user is concerned; userEvent enforces
    // that by refusing the interaction outright, so a raw DOM event stands in for "clicked again
    // while disabled" here.
    fireEvent.click(approveButton);

    expect(apiUpdate).toHaveBeenCalledTimes(1);
    resolveUpdate?.();
    await waitFor(() =>
      expect(screen.getAllByText("inventory:requestsManagement.status.approved").length).toBeGreaterThan(0),
    );
  });

  it("requires a reason before rejecting, then rejects and notifies listeners", async () => {
    const user = userEvent.setup();
    renderPanel(baseRequest());
    await waitForInitialFetches();

    await user.click(screen.getByRole("button", { name: "inventory:requestsManagement.detail.rejectButton" }));
    const dialog = screen.getByRole("dialog", { name: "inventory:requestsManagement.detail.rejectDialog.title" });
    const confirmButton = within(dialog).getByRole("button", {
      name: "inventory:requestsManagement.detail.rejectDialog.rejectRequestButton",
    });
    expect(confirmButton).toBeDisabled();

    await user.type(within(dialog).getByRole("textbox"), "Sample no longer available");
    expect(confirmButton).toBeEnabled();
    await user.click(confirmButton);

    await waitFor(() =>
      expect(apiUpdate).toHaveBeenCalledWith("sampleRequests", "101/status", {
        status: "REJECTED",
        reason: "Sample no longer available",
      }),
    );
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(screen.getAllByText("inventory:requestsManagement.status.rejected").length).toBeGreaterThan(0);
  });

  it("labels the reject reason textbox so it has an accessible name", async () => {
    const user = userEvent.setup();
    renderPanel(baseRequest());
    await waitForInitialFetches();

    await user.click(screen.getByRole("button", { name: "inventory:requestsManagement.detail.rejectButton" }));
    const dialog = screen.getByRole("dialog", { name: "inventory:requestsManagement.detail.rejectDialog.title" });

    // Getting this by its accessible name (rather than the bare role used elsewhere in this
    // suite) is the point of the test: it only succeeds if the label text is actually linked to
    // the textbox, rather than being unassociated text that merely sits above it.
    expect(
      within(dialog).getByRole("textbox", {
        name: "inventory:requestsManagement.detail.rejectDialog.reasonLabel",
      }),
    ).toBeInTheDocument();
  });

  it("shows an error alert and keeps the dialog open with the typed reason when rejecting fails", async () => {
    const restoreConsole = silenceConsole(["error"], ["inventory:requestsManagement.detail.actionErrors.reject"]);
    const user = userEvent.setup();
    failSampleRequestStatusUpdate();
    renderPanel(baseRequest());
    await waitForInitialFetches();

    await user.click(screen.getByRole("button", { name: "inventory:requestsManagement.detail.rejectButton" }));
    const dialog = screen.getByRole("dialog", { name: "inventory:requestsManagement.detail.rejectDialog.title" });
    await user.type(within(dialog).getByRole("textbox"), "Sample no longer available");
    await user.click(
      within(dialog).getByRole("button", {
        name: "inventory:requestsManagement.detail.rejectDialog.rejectRequestButton",
      }),
    );

    await waitFor(() => expect(addAlert).toHaveBeenCalled());
    expect(addAlert.mock.calls[0][0]).toMatchObject({
      variant: "error",
      message: "inventory:errors.genericActionError",
    });
    // The reason the owner already typed is kept so a retry doesn't need retyping it, and
    // the dialog stays open rather than silently discarding the rejection attempt.
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(within(dialog).getByRole("textbox")).toHaveValue("Sample no longer available");
    expect(screen.queryAllByText("inventory:requestsManagement.status.rejected")).toHaveLength(0);
    restoreConsole();
  });
});
