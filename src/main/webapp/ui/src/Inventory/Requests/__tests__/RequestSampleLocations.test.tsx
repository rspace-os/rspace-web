import { ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import type React from "react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import materialTheme from "@/theme";
import RequestSampleLocations from "../RequestSampleLocations";

// ApiServiceBase gates every call behind `when(() => !getRootStore().authStore.isSynchronizing)`
// (see ApiServiceBase.ts) - without this, the real ApiService code backing MSW's handlers below
// would never even issue its requests.
vi.mock("@/stores/stores/getRootStore", () => ({
  __esModule: true,
  default: () => ({ authStore: { isSynchronizing: false } }),
}));

const SAMPLE_URL = "/api/inventory/v1/samples/:id";

const apiGet = vi.fn();

function renderWithProviders(
  ui: React.ReactElement,
  queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } }),
) {
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={materialTheme}>{ui}</ThemeProvider>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("RequestSampleLocations", () => {
  test("shows a loading state before the fetch resolves", () => {
    server.use(http.get(SAMPLE_URL, () => new Promise(() => {})));
    renderWithProviders(<RequestSampleLocations sampleId={55} />);

    expect(screen.getByText("inventory:requestsManagement.detail.fields.loadingLocations")).toBeInTheDocument();
  });

  test("shows the restricted message when subSamples comes back null", async () => {
    server.use(
      http.get(SAMPLE_URL, ({ params }) => {
        apiGet("samples", Number(params.id));
        return HttpResponse.json({ subSamples: null, owner: { firstName: "Olive", lastName: "Owner" } });
      }),
    );
    renderWithProviders(<RequestSampleLocations sampleId={55} />);

    await waitFor(() =>
      expect(
        screen.getByText("inventory:requestsManagement.detail.fields.sampleLocationRestricted"),
      ).toBeInTheDocument(),
    );
  });

  test("shows a 'no subsamples' message when the sample has none", async () => {
    server.use(
      http.get(SAMPLE_URL, ({ params }) => {
        apiGet("samples", Number(params.id));
        return HttpResponse.json({ subSamples: [], owner: { firstName: "Olive", lastName: "Owner" } });
      }),
    );
    renderWithProviders(<RequestSampleLocations sampleId={55} />);

    await waitFor(() =>
      expect(screen.getByText("inventory:requestsManagement.detail.fields.noSubsamples")).toBeInTheDocument(),
    );
  });

  test("shows each subsample and its container breadcrumb when loaded", async () => {
    server.use(
      http.get(SAMPLE_URL, ({ params }) => {
        apiGet("samples", Number(params.id));
        return HttpResponse.json({
          subSamples: [
            {
              id: 1,
              globalId: "SS1",
              name: "Sample Fifty Five.01",
              parentContainers: [{ id: 10, globalId: "IC10", name: "Freezer Box" }],
            },
          ],
          owner: { firstName: "Olive", lastName: "Owner" },
        });
      }),
    );
    renderWithProviders(<RequestSampleLocations sampleId={55} />);

    await waitFor(() => expect(screen.getByText("Sample Fifty Five.01")).toBeInTheDocument());
    expect(screen.getByText("Freezer Box")).toBeInTheDocument();
  });

  test("gives each subsample's radio an accessible name identifying that subsample", async () => {
    server.use(
      http.get(SAMPLE_URL, ({ params }) => {
        apiGet("samples", Number(params.id));
        return HttpResponse.json({
          subSamples: [
            { id: 1, globalId: "SS1", name: "Sample Fifty Five.01", parentContainers: [] },
            { id: 2, globalId: "SS2", name: "Sample Fifty Five.02", parentContainers: [] },
          ],
          owner: { firstName: "Olive", lastName: "Owner" },
        });
      }),
    );
    renderWithProviders(<RequestSampleLocations sampleId={55} selectable onSelectSubsample={() => {}} />);

    // Getting these by accessible name (rather than the bare role, which is what e2e had to fall
    // back to before this fix) only succeeds if the radios are actually labelled. This suite runs
    // i18n in cimode (translation keys render literally, uninterpolated), so it can't also verify
    // the two labels differ by subsample name - only that each radio has one at all.
    await waitFor(() =>
      expect(
        screen.getAllByRole("radio", { name: "inventory:requestsManagement.detail.fields.selectSubsampleLabel" }),
      ).toHaveLength(2),
    );
  });

  test("puts the radios in one named radiogroup, so a screen reader announces a group size instead of '1 of 1'", async () => {
    server.use(
      http.get(SAMPLE_URL, ({ params }) => {
        apiGet("samples", Number(params.id));
        return HttpResponse.json({
          subSamples: [
            { id: 1, globalId: "SS1", name: "Sample Fifty Five.01", parentContainers: [] },
            { id: 2, globalId: "SS2", name: "Sample Fifty Five.02", parentContainers: [] },
          ],
          owner: { firstName: "Olive", lastName: "Owner" },
        });
      }),
    );
    renderWithProviders(<RequestSampleLocations sampleId={55} selectable onSelectSubsample={() => {}} />);

    const group = await screen.findByRole("radiogroup", {
      name: "inventory:requestsManagement.detail.fields.selectSubsampleGroupLabel",
    });
    const radios = await waitFor(() => {
      const found = screen.getAllByRole("radio");
      expect(found).toHaveLength(2);
      return found;
    });
    expect(radios.every((radio) => group.contains(radio))).toBe(true);
    // The bug this guards against: each radio having no `name` (or a distinct one) leaves the
    // browser unable to recognise them as one native group, which is what a screen reader's
    // "x of y" position announcement is actually derived from - a wrapping role="radiogroup"
    // with no shared name would still read every radio as its own group of one.
    const names = radios.map((radio) => radio.getAttribute("name"));
    expect(new Set(names).size).toBe(1);
    expect(names[0]).toBeTruthy();
  });

  test("shares one fetch between two consumers of the same sampleId", async () => {
    server.use(
      http.get(SAMPLE_URL, ({ params }) => {
        apiGet("samples", Number(params.id));
        return HttpResponse.json({ subSamples: [], owner: { firstName: "Olive", lastName: "Owner" } });
      }),
    );
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    renderWithProviders(<RequestSampleLocations sampleId={55} />, queryClient);
    renderWithProviders(<RequestSampleLocations sampleId={55} />, queryClient);

    await waitFor(() =>
      expect(screen.getAllByText("inventory:requestsManagement.detail.fields.noSubsamples")).toHaveLength(2),
    );
    // The bug this guards against: RequestSampleLocations and RequestDetailPanel's own
    // subsample-count query each fetched GET /samples/{id} independently. Two components sharing
    // the same query key (see useSampleWithSubSamplesQuery in ../queries.ts) must only cause one
    // underlying network call between them.
    expect(apiGet).toHaveBeenCalledTimes(1);
  });
});
