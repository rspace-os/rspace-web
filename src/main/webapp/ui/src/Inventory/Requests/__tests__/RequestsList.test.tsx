import { ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import type React from "react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import materialTheme from "@/theme";
import RequestsList, { type ApiSampleRequestListItem } from "../RequestsList";

function renderWithProviders(ui: React.ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

// ApiServiceBase gates every call behind `when(() => !getRootStore().authStore.isSynchronizing)`
// (see ApiServiceBase.ts) - without this, the real ApiService code backing MSW's handler below
// would never even issue its requests.
vi.mock("@/stores/stores/getRootStore", () => ({
  __esModule: true,
  default: () => ({ authStore: { isSynchronizing: false } }),
}));

const SAMPLE_REQUESTS_URL = "/api/inventory/v1/sampleRequests";

const apiQuery = vi.fn();

/**
 * 30 requests, one per id, with later ids created later. The default sort
 * (submitted, desc) therefore shows id 30 first and id 1 last - a stable,
 * predictable order to assert pagination against.
 */
function makeRequests(count: number): Array<ApiSampleRequestListItem> {
  return [...Array(count).keys()].map((i) => {
    const id = i + 1;
    return {
      id,
      status: "PENDING",
      created: new Date(2026, 0, id).toISOString(),
      note: null,
      requester: { id: 1, username: "requester", firstName: "Rita", lastName: "Requester" },
      sample: { id, globalId: `SA${id}`, name: `Sample ${id}`, owner: { id: 2 } },
    };
  });
}

function renderList(
  requests: Array<ApiSampleRequestListItem>,
  onSelect: (request: ApiSampleRequestListItem) => void = () => {},
) {
  server.use(
    http.get(SAMPLE_REQUESTS_URL, ({ request }) => {
      apiQuery("sampleRequests", new URL(request.url).searchParams);
      return HttpResponse.json({ requests, totalHits: requests.length });
    }),
  );
  return renderWithProviders(
    <ThemeProvider theme={materialTheme}>
      <RequestsList selectedRequestId={null} onSelect={onSelect} />
    </ThemeProvider>,
  );
}

/**
 * Serves `allRequests` back a page (of whatever `pageSize`/`pageNumber` the
 * component actually requested) at a time, the way the real backend does -
 * for asserting that the component walks every page rather than stopping
 * after the first (capped at 100 per call, regardless of what's asked for).
 */
function renderPagedList(allRequests: Array<ApiSampleRequestListItem>) {
  server.use(
    http.get(SAMPLE_REQUESTS_URL, ({ request }) => {
      const params = new URL(request.url).searchParams;
      apiQuery("sampleRequests", params);
      const pageNumber = Number(params.get("pageNumber") ?? "0");
      const pageSize = Number(params.get("pageSize") ?? "100");
      const start = pageNumber * pageSize;
      return HttpResponse.json({
        requests: allRequests.slice(start, start + pageSize),
        totalHits: allRequests.length,
      });
    }),
  );
  return renderWithProviders(
    <ThemeProvider theme={materialTheme}>
      <RequestsList selectedRequestId={null} onSelect={() => {}} />
    </ThemeProvider>,
  );
}

async function waitForLoaded(count: number) {
  await waitFor(() => expect(screen.getByText(`SA${count}`)).toBeInTheDocument());
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("RequestsList pagination", () => {
  test("defaults to a page size of 25", async () => {
    renderList(makeRequests(30));
    await waitForLoaded(30);

    // Newest (id 30) through to id 6 are on the first page of 25; id 5 and
    // below are pushed onto the second page.
    expect(screen.getByText("SA6")).toBeInTheDocument();
    expect(screen.queryByText("SA5")).not.toBeInTheDocument();
    expect(within(screen.getByRole("navigation")).getByText("1–25 of 30")).toBeInTheDocument();
  });

  test("moves to the next page of results", async () => {
    const user = userEvent.setup();
    renderList(makeRequests(30));
    await waitForLoaded(30);

    await user.click(screen.getByRole("button", { name: "Go to next page" }));

    expect(screen.getByText("SA1")).toBeInTheDocument();
    expect(screen.queryByText("SA6")).not.toBeInTheDocument();
  });

  test("changing the rows-per-page option shows that many rows", async () => {
    const user = userEvent.setup();
    renderList(makeRequests(30));
    await waitForLoaded(30);

    await user.click(within(screen.getByRole("navigation")).getByRole("combobox"));
    await user.click(screen.getByRole("option", { name: "10" }));

    expect(screen.getByText("SA21")).toBeInTheDocument();
    expect(screen.queryByText("SA20")).not.toBeInTheDocument();
  });

  test("resets to the first page when the requests filter changes", async () => {
    const user = userEvent.setup();
    renderList(makeRequests(30));
    await waitForLoaded(30);

    await user.click(screen.getByRole("button", { name: "Go to next page" }));
    expect(screen.getByText("SA1")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /inventory:requestsManagement\.filters\.requests\.label/ }));
    await user.click(screen.getByRole("menuitem", { name: "inventory:requestsManagement.filters.requests.sent" }));

    await waitFor(() => expect(screen.getByText("SA30")).toBeInTheDocument());
    expect(screen.queryByText("SA1")).not.toBeInTheDocument();
  });

  test('offers "All" for a count above the selectable page sizes but within the decoupled threshold', async () => {
    const user = userEvent.setup();
    renderList(makeRequests(60));
    await waitForLoaded(60);

    await user.click(within(screen.getByRole("navigation")).getByRole("combobox"));

    expect(screen.getByRole("option", { name: "60 (All)" })).toBeInTheDocument();
  });

  test("fetches every page from the backend rather than stopping at its 100-per-call cap", async () => {
    renderPagedList(makeRequests(150));

    await waitFor(() => expect(apiQuery).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(within(screen.getByRole("navigation")).getByText("1–25 of 150")).toBeInTheDocument());
  });
});

describe("RequestsList keyboard selection", () => {
  test("gives each row a tabIndex so it can be reached with the keyboard", async () => {
    renderList(makeRequests(3));
    await waitFor(() => expect(screen.getByText("SA3")).toBeInTheDocument());

    for (const row of screen.getAllByRole("row")) {
      // The header row has no selection behaviour and so is excluded from this.
      if (within(row).queryAllByRole("columnheader").length > 0) continue;
      expect(row).toHaveAttribute("tabindex", "0");
    }
  });

  test("selects the focused row's request when Enter is pressed", async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    renderList(makeRequests(3), onSelect);
    await waitFor(() => expect(screen.getByText("SA2")).toBeInTheDocument());

    screen.getByText("SA2").closest("tr")?.focus();
    await user.keyboard("{Enter}");

    expect(onSelect).toHaveBeenCalledWith(expect.objectContaining({ id: 2 }));
  });

  test("selects the focused row's request when Space is pressed", async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    renderList(makeRequests(3), onSelect);
    await waitFor(() => expect(screen.getByText("SA2")).toBeInTheDocument());

    screen.getByText("SA2").closest("tr")?.focus();
    await user.keyboard(" ");

    expect(onSelect).toHaveBeenCalledWith(expect.objectContaining({ id: 2 }));
  });
});

describe("RequestsList Requester column sorting", () => {
  test("sorts by the requester's displayed name, not their id", async () => {
    const user = userEvent.setup();
    // Requester ids and display names deliberately disagree on order: sorting by id would put
    // Zara (id 1) first, but sorting by the name shown in the chip puts Amy first.
    const requests: Array<ApiSampleRequestListItem> = [
      {
        id: 1,
        status: "PENDING",
        created: "2026-01-01T10:00:00Z",
        note: null,
        requester: { id: 1, username: "zara", firstName: "Zara", lastName: "Zephyr" },
        sample: { id: 1, globalId: "SA1", name: "Sample One", owner: { id: 99 } },
      },
      {
        id: 2,
        status: "PENDING",
        created: "2026-01-02T10:00:00Z",
        note: null,
        requester: { id: 5, username: "amy", firstName: "Amy", lastName: "Anderson" },
        sample: { id: 2, globalId: "SA2", name: "Sample Two", owner: { id: 99 } },
      },
    ];
    renderList(requests);
    await waitFor(() => expect(screen.getByText("Zara Zephyr")).toBeInTheDocument());

    await user.click(screen.getByText("inventory:requestsManagement.columns.requester"));

    const names = screen.getAllByText(/Zephyr|Anderson/).map((el) => el.textContent);
    expect(names).toEqual(["Amy Anderson", "Zara Zephyr"]);
  });
});

describe("RequestsList search filtering", () => {
  test("filters the list to requests whose sample matches the search query", async () => {
    const user = userEvent.setup();
    renderList(makeRequests(30));
    await waitFor(() => expect(screen.getByText("SA30")).toBeInTheDocument());

    await user.type(screen.getByRole("searchbox"), "SA5");

    expect(screen.getByText("SA5")).toBeInTheDocument();
    expect(screen.queryByText("SA30")).not.toBeInTheDocument();
    expect(screen.queryByText("SA1")).not.toBeInTheDocument();
  });

  test("filters the list to requests whose requester matches the search query", async () => {
    const user = userEvent.setup();
    const requests: Array<ApiSampleRequestListItem> = [
      {
        id: 1,
        status: "PENDING",
        created: "2026-01-01T10:00:00Z",
        note: null,
        requester: { id: 1, username: "zara", firstName: "Zara", lastName: "Zephyr" },
        sample: { id: 1, globalId: "SA1", name: "Sample One", owner: { id: 99 } },
      },
      {
        id: 2,
        status: "PENDING",
        created: "2026-01-02T10:00:00Z",
        note: null,
        requester: { id: 5, username: "amy", firstName: "Amy", lastName: "Anderson" },
        sample: { id: 2, globalId: "SA2", name: "Sample Two", owner: { id: 99 } },
      },
    ];
    renderList(requests);
    await waitFor(() => expect(screen.getByText("SA2")).toBeInTheDocument());

    await user.type(screen.getByRole("searchbox"), "anderson");

    expect(screen.getByText("Amy Anderson")).toBeInTheDocument();
    expect(screen.queryByText("Zara Zephyr")).not.toBeInTheDocument();
  });

  test("shows the no-results message when nothing matches the search query", async () => {
    const user = userEvent.setup();
    renderList(makeRequests(3));
    await waitFor(() => expect(screen.getByText("SA3")).toBeInTheDocument());

    await user.type(screen.getByRole("searchbox"), "no such sample");

    expect(screen.getByText("inventory:requestsManagement.noResults")).toBeInTheDocument();
    expect(screen.queryByText("SA1")).not.toBeInTheDocument();
  });

  test("resets to the first page when the search query changes", async () => {
    const user = userEvent.setup();
    renderList(makeRequests(30));
    await waitFor(() => expect(screen.getByText("SA30")).toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: "Go to next page" }));
    expect(screen.getByText("SA1")).toBeInTheDocument();

    await user.type(screen.getByRole("searchbox"), "SA");

    await waitFor(() => expect(screen.getByText("SA30")).toBeInTheDocument());
  });
});
