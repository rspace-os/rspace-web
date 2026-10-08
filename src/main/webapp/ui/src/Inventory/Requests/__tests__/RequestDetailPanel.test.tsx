import { ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type React from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import materialTheme from "@/theme";
import RequestDetailPanel from "../RequestDetailPanel";
import type { ApiSampleRequestListItem } from "../RequestsList";

function renderWithProviders(ui: React.ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

const OWNER = { id: 1, username: "owner", firstName: "Olive", lastName: "Owner" };
const REQUESTER = { id: 2, username: "requester", firstName: "Rita", lastName: "Requester" };

// A mutable box so individual tests can swap the signed-in user without re-declaring the mock.
const currentUser: { value: typeof OWNER } = { value: OWNER };
vi.mock("@/hooks/api/useWhoAmI", () => ({
  __esModule: true,
  default: () => ({
    tag: "success",
    value: {
      ...currentUser.value,
      hasPiRole: false,
      hasSysAdminRole: false,
      email: `${currentUser.value.username}@example.com`,
      bench: null,
      workbenchId: null,
      getBench: () => Promise.reject(new Error("Not implemented by this Person implementation")),
      isCurrentUser: true,
      fullName: `${currentUser.value.firstName} ${currentUser.value.lastName}`,
      label: `${currentUser.value.firstName} ${currentUser.value.lastName} (${currentUser.value.username})`,
    },
  }),
}));

// Both deployment properties default to whatever leaves the pre-existing Choose Sample to
// Prepare dialog in place (inventory.sampleRequests.available alone isn't enough to skip it).
const deploymentProperties: Record<string, string> = {
  "inventory.sampleRequests.available": "DENIED",
  "inventory.operations.available": "ALLOWED",
};
vi.mock("@/hooks/api/useDeploymentProperty", () => ({
  useDeploymentProperty: (name: string) => ({ tag: "success", value: deploymentProperties[name] }),
}));

const getUser = vi.fn();
const addAlert = vi.fn();
vi.mock("@/stores/use-stores", () => ({
  __esModule: true,
  default: () => ({
    peopleStore: { getUser: (...args: Array<unknown>) => getUser(...args) },
    uiStore: { addAlert: (...args: Array<unknown>) => addAlert(...args) },
  }),
}));

const apiGet = vi.fn();
const apiQuery = vi.fn();
const apiUpdate = vi.fn();
vi.mock("@/common/InvApiService", () => ({
  __esModule: true,
  default: {
    get: (...args: Array<unknown>) => apiGet(...args),
    query: (...args: Array<unknown>) => apiQuery(...args),
    update: (...args: Array<unknown>) => apiUpdate(...args),
  },
}));

// RequestSampleLocations does its own fetching and rendering of the location table; all this
// test suite needs from it is a way to deterministically select a subsample.
vi.mock("../RequestSampleLocations", () => ({
  __esModule: true,
  default: ({ onSelectSubsample }: { onSelectSubsample: (s: { id: number; name: string }) => void }) => (
    <button
      type="button"
      aria-label="mock-select-subsample"
      onClick={() => onSelectSubsample({ id: 42, name: "Sample Fifty Five.01" })}
    />
  ),
}));

// PeopleField is a full autocomplete with its own group-member/search fetching; this suite only
// needs to see which recipient it was given and to simulate the owner manually picking one.
vi.mock("../../components/Inputs/PeopleField", () => ({
  __esModule: true,
  default: ({
    recipient,
    restrictToUser,
    onSelection,
  }: {
    recipient: { username: string } | null;
    restrictToUser?: { username: string };
    onSelection: (p: unknown) => void;
  }) => (
    <div>
      <span data-testid="recipient-value">{recipient ? recipient.username : "none"}</span>
      <span data-testid="restrict-to-user-value">{restrictToUser ? restrictToUser.username : "none"}</span>
      <button type="button" aria-label="mock-pick-recipient" onClick={() => onSelection({ username: "manual-pick" })} />
      <button type="button" aria-label="mock-clear-recipient" onClick={() => onSelection(null)} />
    </div>
  ),
}));

function baseRequest(overrides: Partial<ApiSampleRequestListItem> = {}): ApiSampleRequestListItem {
  return {
    id: 101,
    status: "PENDING",
    created: "2026-01-01T10:00:00Z",
    note: "Please can I have this",
    requester: REQUESTER,
    sample: { id: 55, globalId: "SA55", name: "Sample Fifty Five", owner: { id: OWNER.id } },
    ...overrides,
  };
}

function renderPanel(request: ApiSampleRequestListItem | null) {
  return renderWithProviders(
    <ThemeProvider theme={materialTheme}>
      <RequestDetailPanel request={request} />
    </ThemeProvider>,
  );
}

/** Waits for the initial mount fetches (status changes, subsample count, other requests) to settle. */
async function waitForInitialFetches() {
  await waitFor(() => expect(apiGet).toHaveBeenCalledWith("sampleRequests", expect.any(Number)));
  await waitFor(() => expect(apiGet).toHaveBeenCalledWith("samples", expect.any(Number)));
  await waitFor(() => expect(apiQuery).toHaveBeenCalled());
}

beforeEach(() => {
  vi.clearAllMocks();
  currentUser.value = OWNER;
  deploymentProperties["inventory.sampleRequests.available"] = "DENIED";
  deploymentProperties["inventory.operations.available"] = "ALLOWED";
  getUser.mockResolvedValue({ username: REQUESTER.username });

  apiGet.mockImplementation((resource: string) => {
    if (resource === "sampleRequests") {
      return Promise.resolve({
        data: { statusChanges: [], sample: { owner: { firstName: OWNER.firstName, lastName: OWNER.lastName } } },
      });
    }
    if (resource === "samples") {
      return Promise.resolve({ data: { subSamples: [{ id: 1 }] } });
    }
    return Promise.reject(new Error(`unexpected ApiService.get(${resource})`));
  });
  apiQuery.mockImplementation((resource: string) => {
    if (resource === "sampleRequests") {
      return Promise.resolve({ data: { requests: [] } });
    }
    return Promise.reject(new Error(`unexpected ApiService.query(${resource})`));
  });
  apiUpdate.mockImplementation((resource: string, _path: string, body: { status?: string }) => {
    if (resource === "sampleRequests") {
      return Promise.resolve({ data: { status: body.status } });
    }
    if (resource === "samples") {
      return Promise.resolve({ data: { id: 999 } });
    }
    return Promise.reject(new Error(`unexpected ApiService.update(${resource})`));
  });
});

describe("RequestDetailPanel", () => {
  it("shows a placeholder when no request is selected", () => {
    renderPanel(null);
    expect(screen.getByText("inventory:requestsManagement.noSelection")).toBeInTheDocument();
  });

  it("renders the request header, requester, and requested sample for the sample owner", async () => {
    renderPanel(baseRequest());
    await waitForInitialFetches();

    expect(screen.getByText("inventory:requestsManagement.detail.title")).toBeInTheDocument();
    expect(screen.getByText("Rita Requester")).toBeInTheDocument();
    expect(screen.getAllByText("inventory:requestsManagement.status.pending").length).toBeGreaterThan(0);
    // The owner-only Actions and Sample Locations sections should both be present.
    expect(screen.getByRole("button", { name: "inventory:requestsManagement.detail.approveButton" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "inventory:requestsManagement.detail.rejectButton" })).toBeEnabled();
  });

  it("marks a collapsible section header's toggle button with aria-expanded, reflecting its live state", async () => {
    const user = userEvent.setup();
    renderPanel(baseRequest());
    await waitForInitialFetches();

    // All four section headers (Details, Approval Result, Sample Locations, Request History)
    // share the same markup, so this one stands in for the rest.
    const detailsHeading = screen.getByText("inventory:requestsManagement.detail.sections.details");
    const detailsHeader = detailsHeading.closest("div");
    if (!detailsHeader) throw new Error("Could not find the Details section's header container");
    const toggleButton = within(detailsHeader).getByRole("button");
    expect(toggleButton).toHaveAttribute("aria-expanded", "true");

    await user.click(toggleButton);
    expect(toggleButton).toHaveAttribute("aria-expanded", "false");
  });

  it("hides owner-only actions and shows a Cancel option for a non-owner viewing a pending request", async () => {
    currentUser.value = REQUESTER;
    renderPanel(baseRequest());
    await waitForInitialFetches();

    expect(screen.queryByRole("button", { name: "inventory:requestsManagement.detail.approveButton" })).toBeNull();
    expect(
      screen.getByRole("button", { name: "inventory:sample.requestMaterialSection.cancelRequestButton" }),
    ).toBeInTheDocument();
  });

  it("cancels the request as a non-owner and notifies listeners", async () => {
    const user = userEvent.setup();
    currentUser.value = REQUESTER;
    apiUpdate.mockImplementation((resource: string) => {
      if (resource === "sampleRequests") return Promise.resolve({ data: { status: "CANCELLED" } });
      return Promise.reject(new Error("unexpected"));
    });
    renderPanel(baseRequest());
    await waitForInitialFetches();

    await user.click(
      screen.getByRole("button", { name: "inventory:sample.requestMaterialSection.cancelRequestButton" }),
    );

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
    const restoreConsole = silenceConsole(["error"], ["Failed to cancel sample request"]);
    const user = userEvent.setup();
    currentUser.value = REQUESTER;
    apiUpdate.mockImplementation((resource: string) => {
      if (resource === "sampleRequests") return Promise.reject(new Error("Backend unavailable"));
      return Promise.reject(new Error("unexpected"));
    });
    renderPanel(baseRequest());
    await waitForInitialFetches();

    await user.click(
      screen.getByRole("button", { name: "inventory:sample.requestMaterialSection.cancelRequestButton" }),
    );

    await waitFor(() => expect(addAlert).toHaveBeenCalled());
    expect(addAlert.mock.calls[0][0]).toMatchObject({
      variant: "error",
      message: "inventory:errors.genericActionError",
    });
    expect(screen.queryAllByText("inventory:requestsManagement.status.cancelled")).toHaveLength(0);
    restoreConsole();
  });

  it("approves a pending request and notifies listeners", async () => {
    const user = userEvent.setup();
    apiUpdate.mockImplementation((resource: string) => {
      if (resource === "sampleRequests") return Promise.resolve({ data: { status: "APPROVED" } });
      return Promise.reject(new Error("unexpected"));
    });
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
    const restoreConsole = silenceConsole(["error"], ["Failed to approve sample request"]);
    const user = userEvent.setup();
    apiUpdate.mockImplementation((resource: string) => {
      if (resource === "sampleRequests") return Promise.reject(new Error("Backend unavailable"));
      return Promise.reject(new Error("unexpected"));
    });
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
    let resolveUpdate: ((value: { data: { status: string } }) => void) | undefined;
    apiUpdate.mockImplementation((resource: string) => {
      if (resource === "sampleRequests") {
        return new Promise((resolve) => {
          resolveUpdate = resolve;
        });
      }
      return Promise.reject(new Error("unexpected"));
    });
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
    resolveUpdate?.({ data: { status: "APPROVED" } });
    await waitFor(() =>
      expect(screen.getAllByText("inventory:requestsManagement.status.approved").length).toBeGreaterThan(0),
    );
  });

  it("requires a reason before rejecting, then rejects and notifies listeners", async () => {
    const user = userEvent.setup();
    apiUpdate.mockImplementation((resource: string) => {
      if (resource === "sampleRequests") return Promise.resolve({ data: { status: "REJECTED" } });
      return Promise.reject(new Error("unexpected"));
    });
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
    const restoreConsole = silenceConsole(["error"], ["Failed to reject sample request"]);
    const user = userEvent.setup();
    apiUpdate.mockImplementation((resource: string) => {
      if (resource === "sampleRequests") return Promise.reject(new Error("Backend unavailable"));
      return Promise.reject(new Error("unexpected"));
    });
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

  it("marks an approved request as fulfilled without transferring anything, and notifies listeners", async () => {
    const user = userEvent.setup();
    apiUpdate.mockImplementation((resource: string) => {
      if (resource === "sampleRequests") return Promise.resolve({ data: { status: "FULFILLED" } });
      return Promise.reject(new Error("unexpected"));
    });
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
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(screen.getAllByText("inventory:requestsManagement.status.fulfilled").length).toBeGreaterThan(0);
  });

  it("keeps the Mark as Fulfilled dialog open, and does not notify listeners, when the fulfil call is rejected", async () => {
    const restoreConsole = silenceConsole(["error"], ["Failed to fulfil sample request"]);
    const user = userEvent.setup();
    // A 409 here is exactly the scenario the fix guards against: someone else already rejected
    // or cancelled the request before this fulfil call reached the backend.
    apiUpdate.mockImplementation((resource: string) => {
      if (resource === "sampleRequests") return Promise.reject(new Error("409 Conflict"));
      return Promise.reject(new Error("unexpected"));
    });
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

  it("prepares an approved request via the Choose Sample to Prepare dialog and transfers it directly", async () => {
    const user = userEvent.setup();
    // Neither property gates this route toward skipping the dialog.
    deploymentProperties["inventory.sampleRequests.available"] = "DENIED";
    deploymentProperties["inventory.operations.available"] = "ALLOWED";
    apiUpdate.mockImplementation((resource: string, path: string) => {
      if (resource === "sampleRequests") return Promise.resolve({ data: { status: "FULFILLED" } });
      if (resource === "samples" && path === "55/actions/changeOwner") return Promise.resolve({ data: { id: 55 } });
      return Promise.reject(new Error("unexpected"));
    });
    renderPanel(baseRequest({ status: "APPROVED" }));
    await waitForInitialFetches();

    await user.click(screen.getByRole("button", { name: "mock-select-subsample" }));
    const prepareButton = screen.getByRole("button", {
      name: "inventory:requestsManagement.detail.prepareSampleButton",
    });
    expect(prepareButton).toBeEnabled();
    await user.click(prepareButton);

    const chooseDialog = screen.getByRole("dialog", {
      name: "inventory:requestsManagement.detail.chooseMethodDialog.title",
    });
    await user.click(
      within(chooseDialog).getByRole("radio", {
        name: "inventory:requestsManagement.detail.chooseMethodDialog.transferOption",
      }),
    );
    await user.click(
      within(chooseDialog).getByRole("button", {
        name: "inventory:requestsManagement.detail.chooseMethodDialog.proceedButton",
      }),
    );

    const transferDialog = await screen.findByRole("dialog", {
      name: "inventory:requestsManagement.detail.transferDialog.heading",
    });
    await waitFor(() => expect(getUser).toHaveBeenCalledWith(REQUESTER.username));
    await waitFor(() =>
      expect(within(transferDialog).getByTestId("recipient-value")).toHaveTextContent(REQUESTER.username),
    );

    await user.click(within(transferDialog).getByRole("button", { name: "common:actions.transfer" }));

    await waitFor(() =>
      expect(apiUpdate).toHaveBeenCalledWith("sampleRequests", "101/status", { status: "FULFILLED" }),
    );
    await waitFor(() =>
      expect(apiUpdate).toHaveBeenCalledWith("samples", "55/actions/changeOwner", {
        owner: { username: REQUESTER.username },
      }),
    );
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(addAlert).toHaveBeenCalledWith(expect.objectContaining({ variant: "success" }));
    // Replaces the old "two window-event notifications" assertion: invalidateAndSeedDetailStatus
    // (../mutations.ts) invalidates every sampleRequests query once the whole fulfil-then-transfer
    // chain completes, which both refreshes this panel's own detail query and leaves the status
    // chip showing the mutation's own authoritative response.
    await waitFor(() =>
      expect(screen.getAllByText("inventory:requestsManagement.status.fulfilled").length).toBeGreaterThan(0),
    );
  });

  it("does not transfer ownership, closes the dialog, and shows an error when the fulfil call is rejected", async () => {
    const restoreConsole = silenceConsole(["error"], ["Failed to transfer sample ownership"]);
    const user = userEvent.setup();
    // Neither property gates this route toward skipping the dialog.
    deploymentProperties["inventory.sampleRequests.available"] = "DENIED";
    deploymentProperties["inventory.operations.available"] = "ALLOWED";
    // A 409 here is exactly the scenario the fix guards against: someone else already rejected
    // or cancelled the request before this fulfil call reached the backend. changeOwner must
    // never run off the back of it.
    apiUpdate.mockImplementation((resource: string) => {
      if (resource === "sampleRequests") return Promise.reject(new Error("409 Conflict"));
      return Promise.reject(new Error("unexpected"));
    });
    // The request is still APPROVED when the panel mounts (matching the prop below) - that's what
    // makes the Prepare Sample button clickable in the first place - but has been CANCELLED by the
    // time the failed transfer's invalidation triggers this query's refetch, simulating someone
    // else closing it in between. The panel should pick that up rather than carry on showing the
    // stale APPROVED status this attempt started from.
    let sampleRequestsGetCalls = 0;
    apiGet.mockImplementation((resource: string) => {
      if (resource === "sampleRequests") {
        sampleRequestsGetCalls += 1;
        return Promise.resolve({
          data: {
            status: sampleRequestsGetCalls === 1 ? "APPROVED" : "CANCELLED",
            statusChanges: [],
            sample: { owner: { firstName: OWNER.firstName, lastName: OWNER.lastName } },
          },
        });
      }
      if (resource === "samples") return Promise.resolve({ data: { subSamples: [{ id: 1 }] } });
      return Promise.reject(new Error(`unexpected ApiService.get(${resource})`));
    });
    renderPanel(baseRequest({ status: "APPROVED" }));
    await waitForInitialFetches();

    await user.click(screen.getByRole("button", { name: "mock-select-subsample" }));
    await user.click(screen.getByRole("button", { name: "inventory:requestsManagement.detail.prepareSampleButton" }));

    const chooseDialog = screen.getByRole("dialog", {
      name: "inventory:requestsManagement.detail.chooseMethodDialog.title",
    });
    await user.click(
      within(chooseDialog).getByRole("radio", {
        name: "inventory:requestsManagement.detail.chooseMethodDialog.transferOption",
      }),
    );
    await user.click(
      within(chooseDialog).getByRole("button", {
        name: "inventory:requestsManagement.detail.chooseMethodDialog.proceedButton",
      }),
    );

    const transferDialog = await screen.findByRole("dialog", {
      name: "inventory:requestsManagement.detail.transferDialog.heading",
    });
    await waitFor(() => expect(getUser).toHaveBeenCalledWith(REQUESTER.username));
    await user.click(within(transferDialog).getByRole("button", { name: "common:actions.transfer" }));

    await waitFor(() =>
      expect(apiUpdate).toHaveBeenCalledWith("sampleRequests", "101/status", { status: "FULFILLED" }),
    );
    expect(apiUpdate).not.toHaveBeenCalledWith("samples", "55/actions/changeOwner", expect.anything());
    await waitFor(() =>
      expect(
        screen.queryByRole("dialog", { name: "inventory:requestsManagement.detail.transferDialog.heading" }),
      ).toBeNull(),
    );
    expect(addAlert).not.toHaveBeenCalledWith(expect.objectContaining({ variant: "success" }));
    expect(addAlert).toHaveBeenCalledWith(
      expect.objectContaining({
        variant: "error",
        message: "inventory:requestsManagement.detail.transferCancelledErrorMessage",
      }),
    );
    // Refreshed from the backend rather than left showing the stale APPROVED status this attempt
    // started from. The left-hand Requests list and the Sidebar's pending-count badge both share
    // the same invalidation (sampleRequestsQueryKeys.all in ../mutations.ts), so they'd pick this
    // up too without needing a test of their own to prove it here.
    await waitFor(() =>
      expect(screen.getAllByText("inventory:requestsManagement.status.cancelled").length).toBeGreaterThan(0),
    );
    restoreConsole();
  });

  it("shows the placeholder Operations Wizard step when 'Create a new sample' is chosen", async () => {
    const user = userEvent.setup();
    renderPanel(baseRequest({ status: "APPROVED" }));
    await waitForInitialFetches();

    await user.click(screen.getByRole("button", { name: "mock-select-subsample" }));
    await user.click(screen.getByRole("button", { name: "inventory:requestsManagement.detail.prepareSampleButton" }));
    const chooseDialog = screen.getByRole("dialog", {
      name: "inventory:requestsManagement.detail.chooseMethodDialog.title",
    });
    await user.click(
      within(chooseDialog).getByRole("radio", {
        name: "inventory:requestsManagement.detail.chooseMethodDialog.wizardOption",
      }),
    );
    await user.click(
      within(chooseDialog).getByRole("button", {
        name: "inventory:requestsManagement.detail.chooseMethodDialog.proceedButton",
      }),
    );

    expect(
      await screen.findByRole("dialog", { name: "inventory:requestsManagement.detail.prepareDialog.title" }),
    ).toBeInTheDocument();
  });

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
    await waitFor(() => expect(getUser).toHaveBeenCalledWith(REQUESTER.username));
    expect(within(transferDialog).getByTestId("recipient-value")).toBeInTheDocument();
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

  describe("Transfer Ownership dialog content", () => {
    /** Reaches the Transfer Ownership dialog by the shortest route (the skip-dialog path). */
    async function openTransferDialog(user: ReturnType<typeof userEvent.setup>) {
      deploymentProperties["inventory.sampleRequests.available"] = "ALLOWED";
      deploymentProperties["inventory.operations.available"] = "DENIED";
      renderPanel(baseRequest({ status: "APPROVED" }));
      await waitForInitialFetches();
      await user.click(screen.getByRole("button", { name: "mock-select-subsample" }));
      await user.click(
        screen.getByRole("button", { name: "inventory:requestsManagement.detail.transferSampleButton" }),
      );
      return screen.findByRole("dialog", { name: "inventory:requestsManagement.detail.transferDialog.heading" });
    }

    it("shows the heading, ownership warning, and only the always-present bullets for a single subsample with no other requests", async () => {
      const user = userEvent.setup();
      apiGet.mockImplementation((resource: string) => {
        if (resource === "sampleRequests") {
          return Promise.resolve({
            data: { statusChanges: [], sample: { owner: { firstName: OWNER.firstName, lastName: OWNER.lastName } } },
          });
        }
        if (resource === "samples") return Promise.resolve({ data: { subSamples: [{ id: 1 }] } });
        return Promise.reject(new Error(`unexpected ApiService.get(${resource})`));
      });
      const dialog = await openTransferDialog(user);

      // Confirmed by openTransferDialog's findByRole itself: the dialog's title IS the heading.
      expect(within(dialog).getByRole("alert")).toHaveTextContent(
        "inventory:requestsManagement.detail.transferDialog.warning",
      );
      expect(
        within(dialog).getByText("inventory:requestsManagement.detail.transferDialog.whatWillHappen"),
      ).toBeInTheDocument();
      expect(
        within(dialog).getByText("inventory:requestsManagement.detail.transferDialog.bullets.subsamplesMoved"),
      ).toBeInTheDocument();
      expect(
        within(dialog).getByText("inventory:requestsManagement.detail.transferDialog.bullets.requestFulfilled"),
      ).toBeInTheDocument();
      expect(
        within(dialog).queryByText("inventory:requestsManagement.detail.transferDialog.bullets.subsamplesTransferred"),
      ).toBeNull();
      expect(
        within(dialog).queryByText("inventory:requestsManagement.detail.transferDialog.bullets.otherRequestsRejected"),
      ).toBeNull();
    });

    it("keeps the Recipient field restricted to the requester even after it is cleared", async () => {
      const user = userEvent.setup();
      apiGet.mockImplementation((resource: string) => {
        if (resource === "sampleRequests") {
          return Promise.resolve({
            data: { statusChanges: [], sample: { owner: { firstName: OWNER.firstName, lastName: OWNER.lastName } } },
          });
        }
        if (resource === "samples") return Promise.resolve({ data: { subSamples: [{ id: 1 }] } });
        return Promise.reject(new Error(`unexpected ApiService.get(${resource})`));
      });
      const dialog = await openTransferDialog(user);

      await waitFor(() =>
        expect(within(dialog).getByTestId("restrict-to-user-value")).toHaveTextContent(REQUESTER.username),
      );

      await user.click(within(dialog).getByRole("button", { name: "mock-clear-recipient" }));

      expect(within(dialog).getByTestId("recipient-value")).toHaveTextContent("none");
      // The bug this guards against: clearing the field also lifted restrictToUser, showing
      // every user in the instance again instead of just the requester.
      expect(within(dialog).getByTestId("restrict-to-user-value")).toHaveTextContent(REQUESTER.username);
    });

    it("adds the multiple-subsamples bullet when the sample has more than one subsample", async () => {
      const user = userEvent.setup();
      apiGet.mockImplementation((resource: string) => {
        if (resource === "sampleRequests") {
          return Promise.resolve({
            data: { statusChanges: [], sample: { owner: { firstName: OWNER.firstName, lastName: OWNER.lastName } } },
          });
        }
        if (resource === "samples") return Promise.resolve({ data: { subSamples: [{ id: 1 }, { id: 2 }, { id: 3 }] } });
        return Promise.reject(new Error(`unexpected ApiService.get(${resource})`));
      });
      const dialog = await openTransferDialog(user);

      expect(
        await within(dialog).findByText(
          "inventory:requestsManagement.detail.transferDialog.bullets.subsamplesTransferred",
        ),
      ).toBeInTheDocument();
    });

    it("uses the 'Both subsamples' wording specifically when the sample has exactly two subsamples", async () => {
      const user = userEvent.setup();
      apiGet.mockImplementation((resource: string) => {
        if (resource === "sampleRequests") {
          return Promise.resolve({
            data: { statusChanges: [], sample: { owner: { firstName: OWNER.firstName, lastName: OWNER.lastName } } },
          });
        }
        if (resource === "samples") return Promise.resolve({ data: { subSamples: [{ id: 1 }, { id: 2 }] } });
        return Promise.reject(new Error(`unexpected ApiService.get(${resource})`));
      });
      const dialog = await openTransferDialog(user);

      expect(
        await within(dialog).findByText(
          "inventory:requestsManagement.detail.transferDialog.bullets.subsamplesTransferredBoth",
        ),
      ).toBeInTheDocument();
      expect(
        within(dialog).queryByText("inventory:requestsManagement.detail.transferDialog.bullets.subsamplesTransferred"),
      ).toBeNull();
    });

    it("adds the other-requests-rejected bullet when other active requests exist on the same sample", async () => {
      const user = userEvent.setup();
      apiQuery.mockImplementation((resource: string) => {
        if (resource === "sampleRequests") {
          return Promise.resolve({
            data: {
              requests: [
                { id: 202, requester: { firstName: "Sam", lastName: "Second" } },
                { id: 203, requester: { firstName: "Tara", lastName: "Third" } },
              ],
            },
          });
        }
        return Promise.reject(new Error(`unexpected ApiService.query(${resource})`));
      });
      const dialog = await openTransferDialog(user);

      expect(
        await within(dialog).findByText(
          "inventory:requestsManagement.detail.transferDialog.bullets.otherRequestsRejected",
        ),
      ).toBeInTheDocument();
    });

    it("omits the other-requests-rejected bullet once other active requests have resolved to none", async () => {
      const user = userEvent.setup();
      const dialog = await openTransferDialog(user);

      await waitFor(() => expect(apiQuery).toHaveBeenCalled());
      expect(
        within(dialog).queryByText("inventory:requestsManagement.detail.transferDialog.bullets.otherRequestsRejected"),
      ).toBeNull();
    });
  });
});
