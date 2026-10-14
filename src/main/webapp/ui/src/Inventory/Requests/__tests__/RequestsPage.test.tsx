import { ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { LandmarksProvider } from "@/components/LandmarksContext";
import NavigateContext from "@/stores/contexts/Navigate";
import materialTheme from "@/theme";
import type { ApiSampleRequestListItem } from "../RequestsList";
import RequestsPage from "../RequestsPage";

// ApiServiceBase gates every call behind `when(() => !getRootStore().authStore.isSynchronizing)`
// (see ApiServiceBase.ts) - without this, the real ApiService code backing MSW's handlers below
// would never even issue its requests.
vi.mock("@/stores/stores/getRootStore", () => ({
  __esModule: true,
  default: () => ({ authStore: { isSynchronizing: false } }),
}));

// RequestDetailPanel has its own extensive mocking needs (useWhoAmI, useStores, PeopleField,
// etc.) that are irrelevant here; this suite only needs to see which `request` prop RequestsPage
// actually hands it.
const receivedRequest = vi.fn();
vi.mock("../RequestDetailPanel", () => ({
  __esModule: true,
  default: ({ request }: { request: ApiSampleRequestListItem | null }) => {
    receivedRequest(request);
    return <div data-testid="mock-detail-panel">{request ? `id:${request.id}` : "none"}</div>;
  },
}));

const SAMPLE_REQUESTS_URL = "/api/inventory/v1/sampleRequests";
const SAMPLE_REQUEST_DETAIL_URL = "/api/inventory/v1/sampleRequests/:id";

const apiGet = vi.fn();

function renderWithProviders(search: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={materialTheme}>
        <LandmarksProvider>
          <NavigateContext.Provider
            value={{
              useNavigate: () => () => {},
              useLocation: () => ({ hash: "", pathname: "/inventory/requests", search, state: {}, key: "" }),
            }}
          >
            <RequestsPage />
          </NavigateContext.Provider>
        </LandmarksProvider>
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  server.use(http.get(SAMPLE_REQUESTS_URL, () => HttpResponse.json({ requests: [], totalHits: 0 })));
});

describe("RequestsPage deep-linking", () => {
  test("resolves a ?requestId= deep link via a single GET, handing the result to RequestDetailPanel", async () => {
    server.use(
      http.get(SAMPLE_REQUEST_DETAIL_URL, ({ params }) => {
        apiGet(Number(params.id));
        return HttpResponse.json({
          id: 42,
          status: "PENDING",
          created: "2026-01-01T10:00:00Z",
          note: null,
          requester: { id: 1, username: "requester", firstName: "Rita", lastName: "Requester" },
          sample: { id: 5, globalId: "SA5", name: "Sample Five", owner: { id: 1 } },
          statusChanges: [],
        });
      }),
    );

    renderWithProviders("?requestId=42");

    await waitFor(() => expect(receivedRequest).toHaveBeenCalledWith(expect.objectContaining({ id: 42 })));
    // The bug this guards against: a second, separate GET sampleRequests/{id} alongside the one
    // RequestDetailPanel's own query would make for the same id - this is now the only call for it.
    expect(apiGet).toHaveBeenCalledTimes(1);
  });

  test("shows a visible error, not just a console.error, when the linked request is bad or forbidden", async () => {
    const consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => undefined);
    server.use(http.get(SAMPLE_REQUEST_DETAIL_URL, () => HttpResponse.json({ message: "Not found" }, { status: 404 })));

    renderWithProviders("?requestId=999");

    await waitFor(() => expect(screen.getByText("inventory:requestsManagement.deepLinkError")).toBeInTheDocument());
    expect(screen.queryByTestId("mock-detail-panel")).not.toBeInTheDocument();
    consoleErrorSpy.mockRestore();
  });

  test("renders the detail panel's own empty state when there is no requestId in the URL", async () => {
    renderWithProviders("");

    expect(await screen.findByTestId("mock-detail-panel")).toHaveTextContent("none");
    expect(screen.queryByText("inventory:requestsManagement.deepLinkError")).not.toBeInTheDocument();
  });
});
