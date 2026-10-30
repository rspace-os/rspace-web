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
import RequestsPage from "../RequestsPage";
import type { ApiSampleRequestListItem } from "../sampleRequest";

// ApiServiceBase gates every call behind `when(() => !getRootStore().authStore.isSynchronizing)`
// (see ApiServiceBase.ts) - without this, the real ApiService code backing MSW's handlers below
// would never even issue its requests.
vi.mock("@/stores/stores/getRootStore", () => ({
  __esModule: true,
  default: () => ({ authStore: { isSynchronizing: false } }),
}));

// RequestDetailPanel has its own extensive mocking needs (useWhoAmI, useStores, etc.) that are
// irrelevant here; this suite only needs to see which `request` prop RequestsPage actually hands it.
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

  test("recovers by showing a list-selected request after a failed deep link, instead of leaving the error stuck", async () => {
    const consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => undefined);
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
      http.get(SAMPLE_REQUEST_DETAIL_URL, () => HttpResponse.json({ message: "Not found" }, { status: 404 })),
    );

    renderWithProviders("?requestId=999");

    await waitFor(() => expect(screen.getByText("inventory:requestsManagement.deepLinkError")).toBeInTheDocument());

    const user = userEvent.setup();
    (await screen.findByText("SA9")).closest("tr")?.focus();
    await user.keyboard("{Enter}");

    // The bug this guards against: a failed deep link's error flag, once set, never cleared on a
    // plain list click (only a *new* URL id could change the comparison it was derived from),
    // permanently hiding the request the user just picked behind the stale error message.
    await waitFor(() => expect(screen.getByTestId("mock-detail-panel")).toHaveTextContent("id:7"));
    expect(screen.queryByText("inventory:requestsManagement.deepLinkError")).not.toBeInTheDocument();
    consoleErrorSpy.mockRestore();
  });

  test("replaces a stuck error with the linked request once a retry of the same id succeeds", async () => {
    const consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => undefined);
    server.use(
      http.get(SAMPLE_REQUEST_DETAIL_URL, ({ params }) => {
        apiGet(Number(params.id));
        const callsForThisId = apiGet.mock.calls.filter((call) => call[0] === 42).length;
        if (callsForThisId <= 1) return new HttpResponse(null, { status: 503 });
        return HttpResponse.json(requestDetailResponse(42));
      }),
    );
    const { queryClient } = renderWithProviders("?requestId=42");

    await waitFor(() => expect(screen.getByText("inventory:requestsManagement.deepLinkError")).toBeInTheDocument());

    // Simulates a retry - e.g. a refetch on window focus/reconnect - of the very same id that
    // initially failed with a transient error.
    await queryClient.invalidateQueries({ queryKey: ["sampleRequests"] });
    await waitFor(() => {
      const state = queryClient.getQueryState(["sampleRequests", "detail", 42]);
      expect(state?.status).toBe("success");
    });

    // The bug this guards against: marking an id "handled" on its initial failure made the guard
    // skip every later result for that id too, so a subsequent successful retry never replaced the
    // stuck error, even once the query's own cache had settled on a successful status.
    await waitFor(() => expect(screen.getByTestId("mock-detail-panel")).toHaveTextContent("id:42"));
    expect(screen.queryByText("inventory:requestsManagement.deepLinkError")).not.toBeInTheDocument();
    consoleErrorSpy.mockRestore();
  });

  test("does not let a deep link's late successful retry override a request picked manually after its failure", async () => {
    const consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => undefined);
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
      http.get(SAMPLE_REQUEST_DETAIL_URL, ({ params }) => {
        apiGet(Number(params.id));
        const callsForThisId = apiGet.mock.calls.filter((call) => call[0] === 42).length;
        if (callsForThisId <= 1) return new HttpResponse(null, { status: 503 });
        return HttpResponse.json(requestDetailResponse(42));
      }),
    );
    const { queryClient } = renderWithProviders("?requestId=42");

    await waitFor(() => expect(screen.getByText("inventory:requestsManagement.deepLinkError")).toBeInTheDocument());

    const user = userEvent.setup();
    (await screen.findByText("SA9")).closest("tr")?.focus();
    await user.keyboard("{Enter}");
    await waitFor(() => expect(screen.getByTestId("mock-detail-panel")).toHaveTextContent("id:7"));

    // The since-superseded id's own retry succeeding in the background must not clobber the
    // manual pick, exactly as an unrelated background refetch of an already-selected id must not.
    await queryClient.invalidateQueries({ queryKey: ["sampleRequests"] });
    await waitFor(() => {
      const state = queryClient.getQueryState(["sampleRequests", "detail", 42]);
      expect(state?.status).toBe("success");
    });

    expect(screen.getByTestId("mock-detail-panel")).toHaveTextContent("id:7");
    expect(screen.queryByText("inventory:requestsManagement.deepLinkError")).not.toBeInTheDocument();
    consoleErrorSpy.mockRestore();
  });

  test("clears a stuck deep-link error when navigating away to the plain /inventory/requests URL", async () => {
    const consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => undefined);
    server.use(http.get(SAMPLE_REQUEST_DETAIL_URL, () => HttpResponse.json({ message: "Not found" }, { status: 404 })));
    const { navigateTo } = renderWithProviders("?requestId=999");

    await waitFor(() => expect(screen.getByText("inventory:requestsManagement.deepLinkError")).toBeInTheDocument());

    // Simulates the sidebar's "Requests" link, which navigates to the plain route while this page
    // stays mounted - the bug this guards against: the effect did nothing at all once the URL's
    // requestId was gone, so the error (and its handled-id bookkeeping) stayed stuck forever.
    navigateTo("");

    await waitFor(() =>
      expect(screen.queryByText("inventory:requestsManagement.deepLinkError")).not.toBeInTheDocument(),
    );
    expect(screen.getByTestId("mock-detail-panel")).toHaveTextContent("none");
    consoleErrorSpy.mockRestore();
  });

  test("clears a stale deep-linked selection when navigating away to the plain /inventory/requests URL", async () => {
    server.use(
      http.get(SAMPLE_REQUEST_DETAIL_URL, ({ params }) => HttpResponse.json(requestDetailResponse(Number(params.id)))),
    );
    const { navigateTo } = renderWithProviders("?requestId=42");

    await waitFor(() => expect(receivedRequest).toHaveBeenCalledWith(expect.objectContaining({ id: 42 })));

    navigateTo("");

    // The bug this guards against: the old, deep-linked request stayed selected forever, even once
    // the URL no longer referenced it at all.
    await waitFor(() => expect(screen.getByTestId("mock-detail-panel")).toHaveTextContent("none"));
  });

  test("keeps a manually picked request selected when navigating away from a failed deep link to the plain URL", async () => {
    const consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => undefined);
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
      http.get(SAMPLE_REQUEST_DETAIL_URL, () => HttpResponse.json({ message: "Not found" }, { status: 404 })),
    );
    const { navigateTo } = renderWithProviders("?requestId=999");

    await waitFor(() => expect(screen.getByText("inventory:requestsManagement.deepLinkError")).toBeInTheDocument());

    const user = userEvent.setup();
    (await screen.findByText("SA9")).closest("tr")?.focus();
    await user.keyboard("{Enter}");
    await waitFor(() => expect(screen.getByTestId("mock-detail-panel")).toHaveTextContent("id:7"));

    navigateTo("");

    // Leaving the deep link must only drop a selection *it* made, not one the user picked
    // themselves - the same protection the deep link's own background refetches already get.
    expect(screen.getByTestId("mock-detail-panel")).toHaveTextContent("id:7");
    consoleErrorSpy.mockRestore();
  });

  test.each(["abc", "", "1.5", "0", "-3"])(
    "shows the deep-link error immediately for an invalid requestId=%s, without querying the detail endpoint",
    async (invalidValue) => {
      const consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => undefined);
      server.use(
        http.get(SAMPLE_REQUEST_DETAIL_URL, ({ params }) => {
          apiGet(params.id);
          return HttpResponse.json({ message: "should never be reached" }, { status: 404 });
        }),
      );

      renderWithProviders(`?requestId=${invalidValue}`);

      await waitFor(() => expect(screen.getByText("inventory:requestsManagement.deepLinkError")).toBeInTheDocument());
      // The bug this guards against: Number("abc") is NaN, Number("") is 0, Number("1.5") is 1.5,
      // and 0/-3 aren't valid ids either - none of this was validated, so each became a real (and,
      // for NaN specifically, permanently un-dedupable, since NaN !== NaN) GET sampleRequests/{id}
      // request instead of being rejected before ever touching the network.
      expect(apiGet).not.toHaveBeenCalled();
      consoleErrorSpy.mockRestore();
    },
  );

  test("recovers by showing a list-selected request after an invalid requestId, instead of leaving the error stuck", async () => {
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
    );

    renderWithProviders("?requestId=abc");

    await waitFor(() => expect(screen.getByText("inventory:requestsManagement.deepLinkError")).toBeInTheDocument());

    const user = userEvent.setup();
    (await screen.findByText("SA9")).closest("tr")?.focus();
    await user.keyboard("{Enter}");

    await waitFor(() => expect(screen.getByTestId("mock-detail-panel")).toHaveTextContent("id:7"));
    expect(screen.queryByText("inventory:requestsManagement.deepLinkError")).not.toBeInTheDocument();
  });

  test("resolves normally once the URL changes from an invalid requestId to a valid one", async () => {
    server.use(
      http.get(SAMPLE_REQUEST_DETAIL_URL, ({ params }) => {
        apiGet(Number(params.id));
        return HttpResponse.json(requestDetailResponse(42));
      }),
    );
    const { navigateTo } = renderWithProviders("?requestId=abc");

    await waitFor(() => expect(screen.getByText("inventory:requestsManagement.deepLinkError")).toBeInTheDocument());

    navigateTo("?requestId=42");

    await waitFor(() => expect(receivedRequest).toHaveBeenCalledWith(expect.objectContaining({ id: 42 })));
    expect(screen.queryByText("inventory:requestsManagement.deepLinkError")).not.toBeInTheDocument();
  });

  test("renders the detail panel's own empty state when there is no requestId in the URL", async () => {
    renderWithProviders("");

    expect(await screen.findByTestId("mock-detail-panel")).toHaveTextContent("none");
    expect(screen.queryByText("inventory:requestsManagement.deepLinkError")).not.toBeInTheDocument();
  });
});
