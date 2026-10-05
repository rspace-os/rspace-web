import { ThemeProvider } from "@mui/material/styles";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
// Constructing SubSampleModel/SampleModel instances (for the Operations Wizard launch tests)
// touches getRootStore() internally; this registers the real RootStore factory for that.
import "@/stores/stores/RootStore";
import { sampleAttrs } from "@/stores/models/__tests__/SampleModel/mocking";
import { subsampleAttrs } from "@/stores/models/__tests__/SubSampleModel/mocking";
import materialTheme from "@/theme";
import RequestDetailPanel from "../RequestDetailPanel";
import type { ApiSampleRequestListItem } from "../RequestsList";
import { SAMPLE_REQUEST_STATUS_CHANGED_EVENT } from "../sampleRequestEvents";

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
    onSelection,
  }: {
    recipient: { username: string } | null;
    onSelection: (p: unknown) => void;
  }) => (
    <div>
      <span data-testid="recipient-value">{recipient ? recipient.username : "none"}</span>
      <button type="button" aria-label="mock-pick-recipient" onClick={() => onSelection({ username: "manual-pick" })} />
    </div>
  ),
}));

// The real hook drives a whole lock-acquisition/wizard-dialog flow unrelated to what this suite
// is verifying - that the panel asks for the right origin and the right excluded operations. A
// stub records both and exposes a fake "wizard" marker so a test can assert the launch happened.
const launchOperationWizard = vi.fn().mockResolvedValue(true);
let lastWizardOrigins: ReadonlyArray<{ id: number | null; globalId: string | null }> = [];
let lastExcludedOperationKeys: ReadonlySet<string> | undefined;
let lastOnPerformed: ((sample: { id: number; globalId: string; name: string } | null) => void) | undefined;
vi.mock("../../components/Operations/useOperationWizardLauncher", () => ({
  useOperationWizardLauncher: (
    origins: ReadonlyArray<{ id: number | null; globalId: string | null }>,
    options: {
      onPerformed?: (sample: { id: number; globalId: string; name: string } | null) => void;
      excludedOperationKeys?: ReadonlySet<string>;
    } = {},
  ) => {
    lastWizardOrigins = origins;
    lastExcludedOperationKeys = options.excludedOperationKeys;
    lastOnPerformed = options.onPerformed;
    return {
      launch: launchOperationWizard,
      wizard: origins.length > 0 ? <div data-testid="mock-operation-wizard" /> : null,
    };
  },
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
  return render(
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
  launchOperationWizard.mockResolvedValue(true);
  lastWizardOrigins = [];
  lastExcludedOperationKeys = undefined;

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
    expect(screen.getAllByText("Pending").length).toBeGreaterThan(0);
    // The owner-only Actions and Sample Locations sections should both be present.
    expect(screen.getByRole("button", { name: "inventory:requestsManagement.detail.approveButton" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "inventory:requestsManagement.detail.rejectButton" })).toBeEnabled();
  });

  it("shows the requester's username with a 'Deleted' suffix, rather than a user-details lookup, once their account has been deleted", async () => {
    renderPanel(baseRequest({ requester: { ...REQUESTER, id: null } }));
    await waitForInitialFetches();

    // Not the firstName/lastName lookup chip used for a live account (no account exists any
    // more to look up), and not the account's possibly-stale first/last name either.
    expect(screen.queryByText("Rita Requester")).toBeNull();
    expect(screen.getByText("inventory:requestsManagement.userDeleted")).toBeInTheDocument();
  });

  it("shows a Request History entry's createdBy username with a 'Deleted' suffix once their account has been deleted", async () => {
    apiGet.mockImplementation((resource: string) => {
      if (resource === "sampleRequests") {
        return Promise.resolve({
          data: {
            statusChanges: [
              {
                id: 1,
                status: "APPROVED",
                created: "2026-01-02T10:00:00Z",
                createdBy: { id: null, username: "old-owner", firstName: "Olive", lastName: "Owner" },
                reason: null,
                transferredSample: null,
              },
            ],
            sample: { owner: { firstName: OWNER.firstName, lastName: OWNER.lastName } },
          },
        });
      }
      if (resource === "samples") return Promise.resolve({ data: { subSamples: [{ id: 1 }] } });
      return Promise.reject(new Error(`unexpected ApiService.get(${resource})`));
    });
    renderPanel(baseRequest());
    await waitForInitialFetches();

    expect(screen.getByText("inventory:requestsManagement.userDeleted")).toBeInTheDocument();
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
    const onStatusChanged = vi.fn();
    window.addEventListener(SAMPLE_REQUEST_STATUS_CHANGED_EVENT, onStatusChanged);
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
    await waitFor(() => expect(screen.getAllByText("Cancelled").length).toBeGreaterThan(0));
    expect(onStatusChanged).toHaveBeenCalled();
    window.removeEventListener(SAMPLE_REQUEST_STATUS_CHANGED_EVENT, onStatusChanged);
  });

  it("approves a pending request and notifies listeners", async () => {
    const user = userEvent.setup();
    const onStatusChanged = vi.fn();
    window.addEventListener(SAMPLE_REQUEST_STATUS_CHANGED_EVENT, onStatusChanged);
    apiUpdate.mockImplementation((resource: string) => {
      if (resource === "sampleRequests") return Promise.resolve({ data: { status: "APPROVED" } });
      return Promise.reject(new Error("unexpected"));
    });
    renderPanel(baseRequest());
    await waitForInitialFetches();

    await user.click(screen.getByRole("button", { name: "inventory:requestsManagement.detail.approveButton" }));

    await waitFor(() => expect(apiUpdate).toHaveBeenCalledWith("sampleRequests", "101/status", { status: "APPROVED" }));
    await waitFor(() => expect(screen.getAllByText("Approved").length).toBeGreaterThan(0));
    expect(onStatusChanged).toHaveBeenCalled();
    window.removeEventListener(SAMPLE_REQUEST_STATUS_CHANGED_EVENT, onStatusChanged);
  });

  it("requires a reason before rejecting, then rejects and notifies listeners", async () => {
    const user = userEvent.setup();
    const onStatusChanged = vi.fn();
    window.addEventListener(SAMPLE_REQUEST_STATUS_CHANGED_EVENT, onStatusChanged);
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
    expect(screen.getAllByText("Rejected").length).toBeGreaterThan(0);
    expect(onStatusChanged).toHaveBeenCalled();
    window.removeEventListener(SAMPLE_REQUEST_STATUS_CHANGED_EVENT, onStatusChanged);
  });

  it("marks an approved request as fulfilled without transferring anything, and notifies listeners", async () => {
    const user = userEvent.setup();
    const onStatusChanged = vi.fn();
    window.addEventListener(SAMPLE_REQUEST_STATUS_CHANGED_EVENT, onStatusChanged);
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
    expect(screen.getAllByText("Fulfilled").length).toBeGreaterThan(0);
    expect(onStatusChanged).toHaveBeenCalled();
    window.removeEventListener(SAMPLE_REQUEST_STATUS_CHANGED_EVENT, onStatusChanged);
  });

  it("prepares an approved request via the Choose Sample to Prepare dialog and transfers it directly", async () => {
    const user = userEvent.setup();
    const onStatusChanged = vi.fn();
    window.addEventListener(SAMPLE_REQUEST_STATUS_CHANGED_EVENT, onStatusChanged);
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
    // Two notifications are expected: one from markRequestFulfilled (fired before changeOwner
    // even runs) and a second one once changeOwner itself resolves - that second one is what
    // picks up any other requests the backend auto-rejected as a side effect of the transfer,
    // which the first notification fired too early to reflect.
    await waitFor(() => expect(onStatusChanged).toHaveBeenCalledTimes(2));
    window.removeEventListener(SAMPLE_REQUEST_STATUS_CHANGED_EVENT, onStatusChanged);
  });

  it("launches the Operations Wizard for the selected subsample, excluding Pool and Destroy, when 'Create a new sample' is chosen", async () => {
    const user = userEvent.setup();
    apiGet.mockImplementation((resource: string, id: number) => {
      if (resource === "sampleRequests") {
        return Promise.resolve({
          data: { statusChanges: [], sample: { owner: { firstName: OWNER.firstName, lastName: OWNER.lastName } } },
        });
      }
      if (resource === "samples") return Promise.resolve({ data: { subSamples: [{ id: 1 }] } });
      if (resource === "subSamples") {
        expect(id).toBe(42);
        return Promise.resolve({
          data: subsampleAttrs({ id: 42, globalId: "SS42", name: "Sample Fifty Five.01", sample: sampleAttrs() }),
        });
      }
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
        name: "inventory:requestsManagement.detail.chooseMethodDialog.wizardOption",
      }),
    );
    await user.click(
      within(chooseDialog).getByRole("button", {
        name: "inventory:requestsManagement.detail.chooseMethodDialog.proceedButton",
      }),
    );

    await waitFor(() => expect(apiGet).toHaveBeenCalledWith("subSamples", 42));
    await waitFor(() => expect(launchOperationWizard).toHaveBeenCalled());
    expect(lastWizardOrigins).toHaveLength(1);
    expect(lastWizardOrigins[0]).toMatchObject({ id: 42, globalId: "SS42" });
    expect(lastExcludedOperationKeys).toEqual(new Set(["destroy", "pool"]));
  });

  it("opens the Transfer Ownership dialog for the newly-created sample once the Operations Wizard performs an operation", async () => {
    const user = userEvent.setup();
    apiGet.mockImplementation((resource: string) => {
      if (resource === "sampleRequests") {
        return Promise.resolve({
          data: { statusChanges: [], sample: { owner: { firstName: OWNER.firstName, lastName: OWNER.lastName } } },
        });
      }
      if (resource === "samples") return Promise.resolve({ data: { subSamples: [{ id: 1 }] } });
      if (resource === "subSamples") {
        return Promise.resolve({
          data: subsampleAttrs({ id: 42, globalId: "SS42", name: "Sample Fifty Five.01", sample: sampleAttrs() }),
        });
      }
      return Promise.reject(new Error(`unexpected ApiService.get(${resource})`));
    });
    apiUpdate.mockImplementation((resource: string, path: string) => {
      if (resource === "sampleRequests") return Promise.resolve({ data: { status: "FULFILLED" } });
      if (resource === "samples" && path === "77/actions/changeOwner") return Promise.resolve({ data: { id: 77 } });
      return Promise.reject(new Error("unexpected"));
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
        name: "inventory:requestsManagement.detail.chooseMethodDialog.wizardOption",
      }),
    );
    await user.click(
      within(chooseDialog).getByRole("button", {
        name: "inventory:requestsManagement.detail.chooseMethodDialog.proceedButton",
      }),
    );
    await waitFor(() => expect(launchOperationWizard).toHaveBeenCalled());

    // Simulates the wizard's Passage/Aliquot/etc. step handing back the new sample it created.
    act(() => lastOnPerformed?.({ id: 77, globalId: "SA77", name: "Sample Fifty Five.02" }));

    const transferDialog = await screen.findByRole("dialog", {
      name: "inventory:requestsManagement.detail.transferDialog.heading",
    });
    await waitFor(() => expect(getUser).toHaveBeenCalledWith(REQUESTER.username));

    // The new sample didn't exist before this flow, so the usual "you will no longer own this
    // sample" warning (and its "subsamples will be moved" bullet) don't apply - this is the
    // wizard-path replacement content for both.
    expect(
      within(transferDialog).getByText("inventory:requestsManagement.detail.transferDialog.newSampleHint"),
    ).toBeInTheDocument();
    expect(within(transferDialog).queryByText("inventory:requestsManagement.detail.transferDialog.warning")).toBeNull();
    expect(
      within(transferDialog).getByText("inventory:requestsManagement.detail.transferDialog.bullets.newSampleMoved"),
    ).toBeInTheDocument();
    expect(
      within(transferDialog).queryByText("inventory:requestsManagement.detail.transferDialog.bullets.subsamplesMoved"),
    ).toBeNull();

    await user.click(within(transferDialog).getByRole("button", { name: "common:actions.transfer" }));

    await waitFor(() =>
      expect(apiUpdate).toHaveBeenCalledWith("sampleRequests", "101/status", {
        status: "FULFILLED",
        transferredSampleGlobalId: "SA77",
      }),
    );
    await waitFor(() =>
      expect(apiUpdate).toHaveBeenCalledWith("samples", "77/actions/changeOwner", {
        owner: { username: REQUESTER.username },
      }),
    );
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

  describe("Choose Sample to Prepare dialog warnings", () => {
    /** Reaches the Choose Sample to Prepare dialog with the "transfer" radio already selected. */
    async function openChooseMethodDialogWithTransferSelected(user: ReturnType<typeof userEvent.setup>) {
      deploymentProperties["inventory.sampleRequests.available"] = "DENIED";
      deploymentProperties["inventory.operations.available"] = "ALLOWED";
      renderPanel(baseRequest({ status: "APPROVED" }));
      await waitForInitialFetches();
      await user.click(screen.getByRole("button", { name: "mock-select-subsample" }));
      await user.click(screen.getByRole("button", { name: "inventory:requestsManagement.detail.prepareSampleButton" }));
      const dialog = screen.getByRole("dialog", {
        name: "inventory:requestsManagement.detail.chooseMethodDialog.title",
      });
      await user.click(
        within(dialog).getByRole("radio", {
          name: "inventory:requestsManagement.detail.chooseMethodDialog.transferOption",
        }),
      );
      return dialog;
    }

    it("names the Pending state specifically when every other active request is Pending", async () => {
      const user = userEvent.setup();
      apiQuery.mockImplementation((resource: string) => {
        if (resource === "sampleRequests") {
          return Promise.resolve({
            data: { requests: [{ id: 202, status: "PENDING", requester: { firstName: "Sam", lastName: "Second" } }] },
          });
        }
        return Promise.reject(new Error(`unexpected ApiService.query(${resource})`));
      });
      const dialog = await openChooseMethodDialogWithTransferSelected(user);

      expect(
        await within(dialog).findByText(
          "inventory:requestsManagement.detail.chooseMethodDialog.otherActiveRequestsWarningPending",
        ),
      ).toBeInTheDocument();
    });

    it("names the Approved states specifically when every other active request is Approved", async () => {
      const user = userEvent.setup();
      apiQuery.mockImplementation((resource: string) => {
        if (resource === "sampleRequests") {
          return Promise.resolve({
            data: { requests: [{ id: 203, status: "APPROVED", requester: { firstName: "Tara", lastName: "Third" } }] },
          });
        }
        return Promise.reject(new Error(`unexpected ApiService.query(${resource})`));
      });
      const dialog = await openChooseMethodDialogWithTransferSelected(user);

      expect(
        await within(dialog).findByText(
          "inventory:requestsManagement.detail.chooseMethodDialog.otherActiveRequestsWarningApproved",
        ),
      ).toBeInTheDocument();
    });

    it("falls back to the either/or wording when other active requests span both Pending and Approved", async () => {
      const user = userEvent.setup();
      apiQuery.mockImplementation((resource: string) => {
        if (resource === "sampleRequests") {
          return Promise.resolve({
            data: {
              requests: [
                { id: 202, status: "PENDING", requester: { firstName: "Sam", lastName: "Second" } },
                { id: 203, status: "APPROVED", requester: { firstName: "Tara", lastName: "Third" } },
              ],
            },
          });
        }
        return Promise.reject(new Error(`unexpected ApiService.query(${resource})`));
      });
      const dialog = await openChooseMethodDialogWithTransferSelected(user);

      expect(
        await within(dialog).findByText(
          "inventory:requestsManagement.detail.chooseMethodDialog.otherActiveRequestsWarning",
        ),
      ).toBeInTheDocument();
    });

    it("combines both warnings into one alert, leading the other-requests half with 'also', when the sample also has multiple subsamples", async () => {
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
      apiQuery.mockImplementation((resource: string) => {
        if (resource === "sampleRequests") {
          return Promise.resolve({
            data: { requests: [{ id: 202, status: "PENDING", requester: { firstName: "Sam", lastName: "Second" } }] },
          });
        }
        return Promise.reject(new Error(`unexpected ApiService.query(${resource})`));
      });
      const dialog = await openChooseMethodDialogWithTransferSelected(user);

      const alert = await within(dialog).findByRole("alert");
      expect(alert).toHaveTextContent("inventory:requestsManagement.detail.chooseMethodDialog.transferWarning");
      expect(alert).toHaveTextContent(
        "inventory:requestsManagement.detail.chooseMethodDialog.otherActiveRequestsWarningPendingCombined",
      );
    });
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
