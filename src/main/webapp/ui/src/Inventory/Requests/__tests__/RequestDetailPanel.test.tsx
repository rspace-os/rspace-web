import { ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import type React from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
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

// Approve/reject/fulfil/prepare/transfer/cancel each have their own sibling test file; this one
// covers the panel's own layout and composition - the header, the collapsible sections' shared
// chrome, and which top-level panels appear for an owner versus a non-owner.
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

  it("hides owner-only actions and shows a Cancel option for a non-owner viewing a pending request", async () => {
    currentUser.value = REQUESTER;
    renderPanel(baseRequest());
    await waitForInitialFetches();

    expect(screen.queryByRole("button", { name: "inventory:requestsManagement.detail.approveButton" })).toBeNull();
    expect(screen.getByRole("button", { name: "common:actions.cancel" })).toBeInTheDocument();
  });
});
