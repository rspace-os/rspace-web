import { ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import type React from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
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

// ApiServiceBase gates every call behind `when(() => !getRootStore().authStore.isSynchronizing)`
// (see ApiServiceBase.ts) - without this, the real ApiService code backing MSW's handlers below
// would never even issue its requests.
vi.mock("@/stores/stores/getRootStore", () => ({
  __esModule: true,
  default: () => ({ authStore: { isSynchronizing: false } }),
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
// needs to see which recipient it was given (reached here via the skip-dialog route straight to
// the Transfer Ownership dialog).
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

const apiGet = vi.fn();
const apiQuery = vi.fn();
const apiUpdate = vi.fn();

const SAMPLE_REQUEST_DETAIL_URL = "/api/inventory/v1/sampleRequests/:id";
const SAMPLE_REQUESTS_URL = "/api/inventory/v1/sampleRequests";
const SAMPLE_REQUEST_STATUS_URL = "/api/inventory/v1/sampleRequests/:id/status";
const SAMPLE_URL = "/api/inventory/v1/samples/:id";
const SAMPLE_CHANGE_OWNER_URL = "/api/inventory/v1/samples/:id/actions/changeOwner";

/**
 * The happy-path handler set re-registered before every test, matching what apiGet/apiQuery/
 * apiUpdate used to default to back when they were the mocked module itself. They're now pure
 * call recorders - asserted on exactly as before - while the response comes from whichever
 * handler (this default set, or a test's own server.use override below) MSW actually matches.
 */
function defaultHandlers() {
  return [
    http.get(SAMPLE_REQUEST_DETAIL_URL, ({ params }) => {
      apiGet("sampleRequests", Number(params.id));
      return HttpResponse.json({
        statusChanges: [],
        sample: { owner: { firstName: OWNER.firstName, lastName: OWNER.lastName } },
      });
    }),
    http.get(SAMPLE_URL, ({ params }) => {
      apiGet("samples", Number(params.id));
      return HttpResponse.json({ subSamples: [{ id: 1 }] });
    }),
    http.get(SAMPLE_REQUESTS_URL, ({ request }) => {
      apiQuery("sampleRequests", new URL(request.url).searchParams);
      return HttpResponse.json({ requests: [] });
    }),
    http.put(SAMPLE_REQUEST_STATUS_URL, async ({ params, request }) => {
      const body = (await request.json()) as { status: string; reason?: string };
      apiUpdate("sampleRequests", `${params.id}/status`, body);
      return HttpResponse.json({ status: body.status });
    }),
    http.put(SAMPLE_CHANGE_OWNER_URL, async ({ params, request }) => {
      const body = await request.json();
      apiUpdate("samples", `${params.id}/actions/changeOwner`, body);
      return HttpResponse.json({ id: 999 });
    }),
  ];
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
  server.use(...defaultHandlers());
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
