import { ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import type React from "react";
import { expect, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import materialTheme from "@/theme";
import RequestDetailPanel from "../RequestDetailPanel";
import type { ApiSampleRequestListItem } from "../sampleRequest";

/**
 * Shared setup for the RequestDetailPanel.*.test.tsx suite: the signed-in user and deployment
 * property mocks, the MSW handlers standing in for the backend, and the render/wait helpers every
 * sibling file in this directory builds its own tests on top of.
 */

export function renderWithProviders(ui: React.ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

export const OWNER = { id: 1, username: "owner", firstName: "Olive", lastName: "Owner" };
export const REQUESTER = { id: 2, username: "requester", firstName: "Rita", lastName: "Requester" };

// A mutable box so individual tests can swap the signed-in user without re-declaring the mock.
export const currentUser: { value: typeof OWNER } = { value: OWNER };
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
export const deploymentProperties: Record<string, string> = {
  "inventory.sampleRequests.available": "DENIED",
  "inventory.operations.available": "ALLOWED",
};
vi.mock("@/hooks/api/useDeploymentProperty", () => ({
  useDeploymentProperty: (name: string) => ({ tag: "success", value: deploymentProperties[name] }),
}));

export const addAlert = vi.fn();
vi.mock("@/stores/use-stores", () => ({
  __esModule: true,
  default: () => ({
    uiStore: { addAlert: (...args: Array<unknown>) => addAlert(...args) },
  }),
}));

// ApiServiceBase gates every call behind `when(() => !getRootStore().authStore.isSynchronizing)`
// (see ApiServiceBase.ts) - without this, the real ApiService code backing MSW's handlers below
// would never even issue its requests. unitStore is needed too: constructing the SubSampleModel
// the Operations Wizard launch tests build from the subSamples fetch touches HasQuantity's
// quantityUnitLabel, which reads getRootStore().unitStore.getUnit(...) directly.
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

export function baseRequest(overrides: Partial<ApiSampleRequestListItem> = {}): ApiSampleRequestListItem {
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

export function renderPanel(request: ApiSampleRequestListItem | null) {
  return renderWithProviders(
    <ThemeProvider theme={materialTheme}>
      <RequestDetailPanel request={request} />
    </ThemeProvider>,
  );
}

export const apiGet = vi.fn();
export const apiQuery = vi.fn();
export const apiUpdate = vi.fn();

export const SAMPLE_REQUEST_DETAIL_URL = "/api/inventory/v1/sampleRequests/:id";
export const SAMPLE_REQUESTS_URL = "/api/inventory/v1/sampleRequests";
export const SAMPLE_REQUEST_STATUS_URL = "/api/inventory/v1/sampleRequests/:id/status";
export const SAMPLE_REQUEST_FULFIL_BY_TRANSFER_URL = "/api/inventory/v1/sampleRequests/:id/actions/fulfilByTransfer";
export const SAMPLE_URL = "/api/inventory/v1/samples/:id";
export const SAMPLE_CHANGE_OWNER_URL = "/api/inventory/v1/samples/:id/actions/changeOwner";

/**
 * The happy-path handler set re-registered before every test, matching what apiGet/apiQuery/
 * apiUpdate used to default to back when they were the mocked module itself. They're now pure
 * call recorders - asserted on exactly as before - while the response comes from whichever
 * handler (this default set, or a test's own server.use override) MSW actually matches.
 */
export function defaultHandlers() {
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
    http.put(SAMPLE_REQUEST_FULFIL_BY_TRANSFER_URL, async ({ params, request }) => {
      const body = await request.json();
      apiUpdate("sampleRequests", `${params.id}/actions/fulfilByTransfer`, body);
      return HttpResponse.json({ status: "FULFILLED" });
    }),
  ];
}

/** Makes every sampleRequests status PUT for the rest of this test fail with the given status. */
export function failSampleRequestStatusUpdate(status = 500) {
  server.use(
    http.put(SAMPLE_REQUEST_STATUS_URL, async ({ params, request }) => {
      const body = await request.json();
      apiUpdate("sampleRequests", `${params.id}/status`, body);
      return HttpResponse.json({ message: "Backend unavailable" }, { status });
    }),
  );
}

/** Makes every fulfilByTransfer PUT for the rest of this test fail with the given status/message. */
export function failFulfilByTransfer(status = 500, message = "Backend unavailable") {
  server.use(
    http.put(SAMPLE_REQUEST_FULFIL_BY_TRANSFER_URL, async ({ params, request }) => {
      const body = await request.json();
      apiUpdate("sampleRequests", `${params.id}/actions/fulfilByTransfer`, body);
      return HttpResponse.json({ message }, { status });
    }),
  );
}

/** Waits for the initial mount fetches (status changes, subsample count, other requests) to settle. */
export async function waitForInitialFetches() {
  await waitFor(() => expect(apiGet).toHaveBeenCalledWith("sampleRequests", expect.any(Number)));
  await waitFor(() => expect(apiGet).toHaveBeenCalledWith("samples", expect.any(Number)));
  await waitFor(() => expect(apiQuery).toHaveBeenCalled());
}

/** Resets per-test state: call recorders, the signed-in user, deployment properties, and MSW handlers. */
export function resetRequestDetailPanelFixtures() {
  vi.clearAllMocks();
  currentUser.value = OWNER;
  deploymentProperties["inventory.sampleRequests.available"] = "DENIED";
  deploymentProperties["inventory.operations.available"] = "ALLOWED";
  server.use(...defaultHandlers());
}
