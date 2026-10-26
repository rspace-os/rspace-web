import { ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import type React from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
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
// Prepare dialog in place (inventory.sampleRequests.available alone isn't enough to skip it) -
// not that this file's non-owner tests ever reach that dialog.
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

/** Makes every sampleRequests status PUT for the rest of this test fail with the given status. */
function failSampleRequestStatusUpdate(status = 500) {
  server.use(
    http.put(SAMPLE_REQUEST_STATUS_URL, async ({ params, request }) => {
      const body = await request.json();
      apiUpdate("sampleRequests", `${params.id}/status`, body);
      return HttpResponse.json({ message: "Backend unavailable" }, { status });
    }),
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
  server.use(...defaultHandlers());
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
    const restoreConsole = silenceConsole(["error"], ["Failed to cancel sample request"]);
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
