import { ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import type React from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { server } from "@/__tests__/mswServer";
import i18n from "@/modules/common/i18n";
// Constructing SubSampleModel/SampleModel instances (for the Operations Wizard launch tests)
// touches getRootStore() internally; this registers the real RootStore factory for that.
import "@/stores/stores/RootStore";
import { sampleAttrs } from "@/stores/models/__tests__/SampleModel/mocking";
import { subsampleAttrs } from "@/stores/models/__tests__/SubSampleModel/mocking";
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
// would never even issue its requests. unitStore is needed too: constructing the SubSampleModel/
// SampleModel the Operations Wizard launch tests build from the subSamples fetch touches
// HasQuantity's quantityUnitLabel, which reads getRootStore().unitStore.getUnit(...) directly.
vi.mock("@/stores/stores/getRootStore", () => ({
  __esModule: true,
  default: () => ({
    authStore: { isSynchronizing: false },
    unitStore: { getUnit: () => ({ label: "ml" }) },
  }),
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
const SUBSAMPLE_URL = "/api/inventory/v1/subSamples/:id";

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
  launchOperationWizard.mockResolvedValue(true);
  lastWizardOrigins = [];
  lastExcludedOperationKeys = undefined;
  lastOnPerformed = undefined;
  server.use(...defaultHandlers());
});

// Approve/reject/fulfil/choose-method/transfer/cancel each have their own sibling test file; this
// one covers the panel's own layout and composition - the header, the collapsible sections'
// shared chrome, which top-level panels appear for an owner versus a non-owner - plus the
// Operations Wizard launch reached via the "wizard" radio (ChooseMethodDialog's own sibling file
// covers the dialog itself, not what proceeding with "wizard" actually launches).
describe("RequestDetailPanel", () => {
  it("shows a placeholder when no request is selected", () => {
    renderPanel(null);
    expect(screen.getByText("inventory:requestsManagement.noSelection")).toBeInTheDocument();
  });

  it("renders the request header, requester, and requested sample for the sample owner", async () => {
    const { container } = renderPanel(baseRequest());
    await waitForInitialFetches();

    expect(screen.getByText("inventory:requestsManagement.detail.title")).toBeInTheDocument();
    expect(screen.getByText("Rita Requester")).toBeInTheDocument();
    expect(screen.getAllByText("inventory:requestsManagement.status.pending").length).toBeGreaterThan(0);
    // The owner-only Actions and Sample Locations sections should both be present.
    expect(screen.getByRole("button", { name: "inventory:requestsManagement.detail.approveButton" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "inventory:requestsManagement.detail.rejectButton" })).toBeEnabled();
    await expectAccessible(container);
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

  it("gives each collapsible section's toggle button its own aria-controls, each pointing to a real element", async () => {
    renderPanel(baseRequest());
    await waitForInitialFetches();

    const toggleButtons = screen.getAllByRole("button").filter((button) => button.hasAttribute("aria-controls"));
    // Details, Approval Result, Sample Locations (owner-only - the default signed-in user here is
    // the owner), and Request History.
    expect(toggleButtons).toHaveLength(4);

    const controlsIds = toggleButtons.map((button) => button.getAttribute("aria-controls"));
    // The bug this guards against: aria-controls being entirely absent, or every button pointing
    // at the same id - either way leaving a screen reader user with no way to tell which content
    // region a given "Collapse section"/"Expand section" button actually affects.
    expect(new Set(controlsIds).size).toBe(4);
    for (const id of controlsIds) {
      expect(document.getElementById(id as string)).not.toBeNull();
    }
  });

  it("shows the requester's username with a 'Deleted' suffix, rather than a user-details lookup, once their account has been deleted", async () => {
    // cimode rendering (see src/__tests__/setup.ts) shows only the bare translation key, never
    // the interpolated copy - asserting on rendered text alone can't tell a correct username from
    // a dropped or wrong one, so this spies on the real t() call itself to check what was passed.
    const tSpy = vi.spyOn(i18n, "t");
    renderPanel(baseRequest({ requester: { ...REQUESTER, firstName: null, lastName: null } }));
    await waitForInitialFetches();

    // Not the firstName/lastName lookup chip used for a live account (no account exists any
    // more to look up), and not the account's possibly-stale first/last name either.
    expect(screen.queryByText("Rita Requester")).toBeNull();
    expect(screen.getByText("inventory:requestsManagement.userDeleted")).toBeInTheDocument();
    expect(tSpy).toHaveBeenCalledWith(
      "inventory:requestsManagement.userDeleted",
      expect.objectContaining({ username: REQUESTER.username }),
    );
    tSpy.mockRestore();
  });

  it("shows a Request History entry's createdBy username with a 'Deleted' suffix once their account has been deleted", async () => {
    server.use(
      http.get(SAMPLE_REQUEST_DETAIL_URL, ({ params }) => {
        apiGet("sampleRequests", Number(params.id));
        return HttpResponse.json({
          statusChanges: [
            {
              id: 1,
              status: "APPROVED",
              created: "2026-01-02T10:00:00Z",
              createdBy: { id: 999, username: "old-owner", firstName: null, lastName: null },
              reason: null,
              transferredSample: null,
            },
          ],
          sample: { owner: { firstName: OWNER.firstName, lastName: OWNER.lastName } },
        });
      }),
    );
    // See the equivalent requester test above: cimode hides interpolated values from rendered
    // text, so the username has to be checked via the actual t() call instead.
    const tSpy = vi.spyOn(i18n, "t");
    renderPanel(baseRequest());
    await waitForInitialFetches();

    expect(screen.getByText("inventory:requestsManagement.userDeleted")).toBeInTheDocument();
    expect(tSpy).toHaveBeenCalledWith(
      "inventory:requestsManagement.userDeleted",
      expect.objectContaining({ username: "old-owner" }),
    );
    tSpy.mockRestore();
  });

  it("hides owner-only actions and shows a Cancel option for a non-owner viewing a pending request", async () => {
    currentUser.value = REQUESTER;
    renderPanel(baseRequest());
    await waitForInitialFetches();

    expect(screen.queryByRole("button", { name: "inventory:requestsManagement.detail.approveButton" })).toBeNull();
    expect(screen.getByRole("button", { name: "common:actions.cancel" })).toBeInTheDocument();
  });

  it("launches the Operations Wizard with the selected subsample as its origin when 'Create a new sample' is proceeded", async () => {
    const user = userEvent.setup();
    server.use(
      http.get(SUBSAMPLE_URL, () =>
        HttpResponse.json(subsampleAttrs({ id: 42, globalId: "SS42", sample: sampleAttrs() })),
      ),
    );
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

    expect(await screen.findByTestId("mock-operation-wizard")).toBeInTheDocument();
    await waitFor(() => expect(launchOperationWizard).toHaveBeenCalled());
    expect(lastWizardOrigins).toHaveLength(1);
    expect(lastWizardOrigins[0]).toMatchObject({ id: 42, globalId: "SS42" });
    // Destroying the origin, or pooling (which needs multiple), has no sensible place in a flow
    // that only ever offers the single subsample selected in Sample Locations.
    expect(lastExcludedOperationKeys).toEqual(new Set(["destroy", "pool"]));
  });

  it("opens the Transfer Ownership dialog for the sample the Operations Wizard just created, once it reports one performed", async () => {
    // cimode rendering (see src/__tests__/setup.ts) shows only the bare translation key, never
    // the interpolated copy - asserting on rendered text alone can't tell the new sample's name
    // apart from the originally requested one, so this spies on the real t() call instead.
    const tSpy = vi.spyOn(i18n, "t");
    const user = userEvent.setup();
    server.use(
      http.get(SUBSAMPLE_URL, () =>
        HttpResponse.json(subsampleAttrs({ id: 42, globalId: "SS42", sample: sampleAttrs() })),
      ),
    );
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
    await screen.findByTestId("mock-operation-wizard");

    // Simulates the real Operations Wizard calling its onPerformed prop once an operation (e.g.
    // Split or Create) completes and hands back the sample it created - not driven through the
    // mocked wizard div itself, which has no UI of its own here.
    act(() => {
      lastOnPerformed?.({ id: 999, globalId: "SA999", name: "New Sample From Wizard" });
    });

    await screen.findByRole("dialog", { name: "inventory:requestsManagement.detail.transferDialog.heading" });
    // Not the originally requested sample's name ("Sample Fifty Five") - the Operations Wizard
    // created a new sample, and that is what actually gets transferred to the requester.
    expect(tSpy).toHaveBeenCalledWith(
      "requestsManagement.detail.transferDialog.heading",
      expect.objectContaining({ sampleName: "New Sample From Wizard" }),
    );
    tSpy.mockRestore();
  });
});
