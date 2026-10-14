import { ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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

function tree(queryClient: QueryClient, search: string) {
  return (
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
    </QueryClientProvider>
  );
}

/**
 * `navigateTo` re-renders the *same* mounted RequestsPage with a new `location.search` - as a
 * client-side navigation would, without remounting the page - rather than making a fresh render
 * with a different initial URL.
 */
function renderWithProviders(search: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const utils = render(tree(queryClient, search));
  return {
    ...utils,
    queryClient,
    navigateTo: (newSearch: string) => utils.rerender(tree(queryClient, newSearch)),
  };
}

function requestDetailResponse(id: number) {
  return {
    id,
    status: "PENDING",
    created: "2026-01-01T10:00:00Z",
    note: null,
    requester: { id: 1, username: "requester", firstName: "Rita", lastName: "Requester" },
    sample: { id: 5, globalId: "SA5", name: "Sample Five", owner: { id: 1 } },
    statusChanges: [],
  };
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
        return HttpResponse.json(requestDetailResponse(42));
      }),
    );

    renderWithProviders("?requestId=42");

    await waitFor(() => expect(receivedRequest).toHaveBeenCalledWith(expect.objectContaining({ id: 42 })));
    // The bug this guards against: a second, separate GET sampleRequests/{id} alongside the one
    // RequestDetailPanel's own query would make for the same id - this is now the only call for it.
    expect(apiGet).toHaveBeenCalledTimes(1);
  });

  test("follows the URL to a new request when it changes while the page stays mounted", async () => {
    server.use(
      http.get(SAMPLE_REQUEST_DETAIL_URL, ({ params }) => {
        const id = Number(params.id);
        apiGet(id);
        return HttpResponse.json(requestDetailResponse(id));
      }),
    );
    const { navigateTo } = renderWithProviders("?requestId=42");

    await waitFor(() => expect(receivedRequest).toHaveBeenCalledWith(expect.objectContaining({ id: 42 })));

    navigateTo("?requestId=43");

    // The bug this guards against: gating the deep-link lookup on "is a request already selected"
    // permanently stopped it from ever tracking the URL again once request 42 resolved, leaving
    // the panel stuck showing request 42 even though the URL (and the user) had moved on to 43.
    await waitFor(() => expect(receivedRequest).toHaveBeenCalledWith(expect.objectContaining({ id: 43 })));
  });

  test("does not let a background refetch of the deep-linked id override a request since picked from the list", async () => {
    server.use(
      http.get(SAMPLE_REQUESTS_URL, () =>
        HttpResponse.json({
          requests: [
            {
              id: 7,
              status: "PENDING",
              created: "2026-01-02T10:00:00Z",
              note: null,
              requester: { id: 2, username: "other", firstName: "Oscar", lastName: "Other" },
              sample: { id: 9, globalId: "SA9", name: "Sample Nine", owner: { id: 1 } },
            },
          ],
          totalHits: 1,
        }),
      ),
      // Returns a different status after the first call - React Query's structural sharing would
      // otherwise keep returning the very same `data` reference on a background refetch of
      // identical content, which would mask the bug this test exists to catch.
      http.get(SAMPLE_REQUEST_DETAIL_URL, ({ params }) => {
        apiGet(Number(params.id));
        const status = apiGet.mock.calls.filter((call) => call[0] === 42).length > 1 ? "APPROVED" : "PENDING";
        return HttpResponse.json({ ...requestDetailResponse(42), status });
      }),
    );
    const { queryClient } = renderWithProviders("?requestId=42");

    await waitFor(() => expect(receivedRequest).toHaveBeenCalledWith(expect.objectContaining({ id: 42 })));

    const user = userEvent.setup();
    // GlobalId's own onClick calls stopPropagation (it has its own navigation behaviour), so the
    // row itself - not the "SA9" chip inside it - is what needs the click/keypress.
    (await screen.findByText("SA9")).closest("tr")?.focus();
    await user.keyboard("{Enter}");
    await waitFor(() => expect(receivedRequest).toHaveBeenCalledWith(expect.objectContaining({ id: 7 })));

    // Simulates an unrelated mutation elsewhere invalidating sampleRequestsQueryKeys.all, which
    // this page's still-enabled (url-tracking) deep-link query for 42 shares - it refetches in the
    // background even though the user has since picked request 7 from the list.
    await queryClient.invalidateQueries({ queryKey: ["sampleRequests"] });

    // Waits on the query cache itself (not just the network call, nor the UI) settling with the
    // refetch's new content - a more direct signal than polling the DOM that the full update cycle
    // (fetch resolved, cache written, subscribed components notified) has actually completed,
    // before judging whether a clobber happened.
    await waitFor(() => {
      const cached = queryClient.getQueryData<{ status: string }>(["sampleRequests", "detail", 42]);
      expect(cached?.status).toBe("APPROVED");
    });
    // The bug this guards against: promoting on every change of the query's data reference (a
    // background refetch counts, even though the id itself didn't change) would clobber the
    // manually-picked request 7 back to 42, even though the URL never changed.
    expect(screen.getByTestId("mock-detail-panel")).toHaveTextContent("id:7");
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
