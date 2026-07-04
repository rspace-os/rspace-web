import { fireEvent, screen } from "@testing-library/react";
import { afterEach, describe, expect, test, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import type { AxiosResponse } from "axios";
import type { ComponentProps } from "react";
import { renderWithRealI18n, wrapWithRealI18n } from "@/__tests__/helpers/realI18n";
import commonEn from "@/modules/common/i18n/locales/en-US/common.json";
import inventoryEn from "@/modules/common/i18n/locales/en-US/inventory.json";
import InvApiService from "../../../../common/InvApiService";
import { makeMockSample } from "../../../../stores/models/__tests__/SampleModel/mocking";
import { makeMockSubSample } from "../../../../stores/models/__tests__/SubSampleModel/mocking";
import { makeMockTemplate } from "../../../../stores/models/__tests__/TemplateModel/mocking";
import VersionHistory from "../VersionHistory";

vi.mock("../../../../common/InvApiService", () => ({
  default: {
    get: () => ({}),
  },
}));
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    unitStore: {
      getUnit: () => ({ label: "ml" }),
    },
  }),
}));

const revisionsResponse = (
  revisions: Array<{
    revisionId: number;
    version: number;
    lastModified?: string;
    modifiedByFullName?: string;
  }>,
) =>
  ({
    data: {
      revisions: revisions.map(({ revisionId, version, lastModified, modifiedByFullName }) => ({
        revisionId,
        revisionType: "MOD",
        record: {
          version,
          lastModified: lastModified ?? "2026-06-01T10:00:00.000Z",
          modifiedByFullName: modifiedByFullName ?? "Some User",
        },
      })),
      revisionsCount: revisions.length,
    },
    status: 200,
    statusText: "OK",
    headers: {},
    config: {},
  }) as AxiosResponse;

const realI18nConfig = {
  resources: { common: commonEn, inventory: inventoryEn },
  defaultNS: "inventory",
};

type VersionHistoryRecord = ComponentProps<typeof VersionHistory>["record"];

async function renderVersionHistory(record: VersionHistoryRecord) {
  return renderWithRealI18n(<VersionHistory record={record} />, realI18nConfig);
}

async function wrapVersionHistory(record: VersionHistoryRecord) {
  return wrapWithRealI18n(<VersionHistory record={record} />, realI18nConfig);
}

describe("VersionHistory", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  test("shows the current version and fetches the revisions list when opened", async () => {
    const spy = vi
      .spyOn(InvApiService, "get")
      .mockImplementation(() => Promise.resolve(revisionsResponse([{ revisionId: 100, version: 1 }])));
    const subsample = makeMockSubSample({ version: 2 });
    await renderVersionHistory(subsample);

    expect(screen.getByText("2")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "View version history" }));
    expect(await screen.findByRole("table")).toBeVisible();
    expect(spy).toHaveBeenCalledWith("subSamples/1/revisions");
  });

  test("groups several revisions of the same version into one row", async () => {
    vi.spyOn(InvApiService, "get").mockImplementation(() =>
      Promise.resolve(
        revisionsResponse([
          { revisionId: 100, version: 1 },
          { revisionId: 250, version: 2 },
          { revisionId: 380, version: 2 },
        ]),
      ),
    );
    const subsample = makeMockSubSample({ version: 2 });
    await renderVersionHistory(subsample);
    fireEvent.click(screen.getByRole("button", { name: "View version history" }));

    await screen.findByRole("table");
    const rows = screen.getAllByRole("row");
    // header row + one row per version (not per revision)
    expect(rows).toHaveLength(3);
  });

  test("a version row links to the versioned viewer URL", async () => {
    vi.spyOn(InvApiService, "get").mockImplementation(() =>
      Promise.resolve(revisionsResponse([{ revisionId: 100, version: 1 }])),
    );
    const subsample = makeMockSubSample({ version: 2 });
    await renderVersionHistory(subsample);
    fireEvent.click(screen.getByRole("button", { name: "View version history" }));

    await screen.findByRole("table");
    const link = screen.getByRole("link", { name: "Version 1" });
    expect(link).toHaveAttribute("href", "/inventory/subsample/1?version=1");
  });

  test("shows an error message when the fetch fails", async () => {
    vi.spyOn(InvApiService, "get").mockImplementation(() => Promise.reject(new Error("An error")));
    const subsample = makeMockSubSample({ version: 2 });
    await renderVersionHistory(subsample);
    fireEvent.click(screen.getByRole("button", { name: "View version history" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("An error");
  });

  test("a non-Error rejection still produces a helpful error message", async () => {
    vi.spyOn(InvApiService, "get").mockImplementation(() =>
      // eslint-disable-next-line @typescript-eslint/prefer-promise-reject-errors
      Promise.reject("boom"),
    );
    const subsample = makeMockSubSample({ version: 2 });
    await renderVersionHistory(subsample);
    fireEvent.click(screen.getByRole("button", { name: "View version history" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Could not load version history.");
  });

  test("renders nothing for an unsaved record", async () => {
    const sample = makeMockSample({ id: null, globalId: null });
    const { container } = await renderVersionHistory(sample);
    expect(container).toBeEmptyDOMElement();
  });

  test("shows the version history of a sample template", async () => {
    const spy = vi
      .spyOn(InvApiService, "get")
      .mockImplementation(() => Promise.resolve(revisionsResponse([{ revisionId: 100, version: 1 }])));
    const template = makeMockTemplate({ version: 2 });
    await renderVersionHistory(template);

    expect(screen.getByText("2")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "View version history" }));
    expect(await screen.findByRole("table")).toBeVisible();
    expect(spy).toHaveBeenCalledWith("sampleTemplates/1/revisions");
  });

  test("a template version row links to the versioned template viewer URL", async () => {
    vi.spyOn(InvApiService, "get").mockImplementation(() =>
      Promise.resolve(revisionsResponse([{ revisionId: 100, version: 1 }])),
    );
    const template = makeMockTemplate({ version: 2 });
    await renderVersionHistory(template);
    fireEvent.click(screen.getByRole("button", { name: "View version history" }));

    await screen.findByRole("table");
    const link = screen.getByRole("link", { name: "Version 1" });
    expect(link).toHaveAttribute("href", "/inventory/sampletemplate/1?version=1");
  });

  test("shows an informational message when there is no history yet", async () => {
    vi.spyOn(InvApiService, "get").mockImplementation(() => Promise.resolve(revisionsResponse([])));
    const subsample = makeMockSubSample({ version: 1 });
    await renderVersionHistory(subsample);
    fireEvent.click(screen.getByRole("button", { name: "View version history" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("No version history is available for this item yet.");
  });

  test("the current version row is marked as such", async () => {
    vi.spyOn(InvApiService, "get").mockImplementation(() =>
      Promise.resolve(
        revisionsResponse([
          { revisionId: 100, version: 1 },
          { revisionId: 250, version: 2 },
        ]),
      ),
    );
    const subsample = makeMockSubSample({ version: 2 });
    await renderVersionHistory(subsample);
    fireEvent.click(screen.getByRole("button", { name: "View version history" }));

    await screen.findByRole("table");
    const currentRow = screen.getAllByRole("row").find((row) => row.textContent?.includes("Version 2"));
    expect(currentRow).toHaveTextContent("(current)");
  });

  test("a historical view marks its pinned version as viewing, not current", async () => {
    vi.spyOn(InvApiService, "get").mockImplementation(() =>
      Promise.resolve(
        revisionsResponse([
          { revisionId: 100, version: 1 },
          { revisionId: 250, version: 2 },
        ]),
      ),
    );
    const subsample = makeMockSubSample({
      version: 1,
      historicalVersion: true,
    });
    await renderVersionHistory(subsample);
    fireEvent.click(screen.getByRole("button", { name: "View version history" }));

    await screen.findByRole("table");
    const viewedRow = screen.getAllByRole("row").find((row) => row.textContent?.includes("Version 1"));
    expect(viewedRow).toHaveTextContent("(viewing)");
    expect(screen.getAllByRole("row").some((row) => row.textContent?.includes("(current)"))).toBe(false);
  });

  test("the newest revision of a version wins regardless of response order", async () => {
    // the endpoint returns oldest-first today, but nothing guarantees it:
    // serve newest-first and assert the newest revision's details still win
    vi.spyOn(InvApiService, "get").mockImplementation(() =>
      Promise.resolve(
        revisionsResponse([
          {
            revisionId: 380,
            version: 2,
            lastModified: "2026-06-03T10:00:00.000Z",
            modifiedByFullName: "Newest Editor",
          },
          {
            revisionId: 250,
            version: 2,
            lastModified: "2026-06-01T10:00:00.000Z",
            modifiedByFullName: "Oldest Editor",
          },
        ]),
      ),
    );
    const subsample = makeMockSubSample({ version: 2 });
    await renderVersionHistory(subsample);
    fireEvent.click(screen.getByRole("button", { name: "View version history" }));

    await screen.findByRole("table");
    expect(screen.getByText("Newest Editor")).toBeVisible();
    expect(screen.queryByText("Oldest Editor")).not.toBeInTheDocument();
  });

  test("reopening the dialog does not flash the previous content", async () => {
    const resolvers: Array<(r: AxiosResponse) => void> = [];
    vi.spyOn(InvApiService, "get").mockImplementation(
      () =>
        new Promise((resolve) => {
          resolvers.push(resolve);
        }),
    );
    const subsample = makeMockSubSample({ version: 2 });
    await renderVersionHistory(subsample);
    fireEvent.click(screen.getByRole("button", { name: "View version history" }));
    resolvers[0](revisionsResponse([{ revisionId: 100, version: 1 }]));
    await screen.findByRole("table");

    fireEvent.click(screen.getByRole("button", { name: "Close" }));
    // wait out the dialog exit transition, then reopen
    const reopen = await screen.findByRole("button", {
      name: "View version history",
    });
    fireEvent.click(reopen);

    // while the second fetch is in flight, the stale table must not be shown
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  test("a stale fetch for a previous record cannot overwrite the current one", async () => {
    const resolvers: Array<(r: AxiosResponse) => void> = [];
    vi.spyOn(InvApiService, "get").mockImplementation(
      () =>
        new Promise((resolve) => {
          resolvers.push(resolve);
        }),
    );
    const subsample = makeMockSubSample({ version: 2 });
    const { rerender } = await renderVersionHistory(subsample);
    fireEvent.click(screen.getByRole("button", { name: "View version history" }));

    // the active record changes while the first fetch is still in flight
    const sample = makeMockSample({ version: 3 });
    rerender(await wrapVersionHistory(sample));

    // the new record's response lands first...
    resolvers[1](revisionsResponse([{ revisionId: 9, version: 3 }]));
    // ...then the stale response for the previous record arrives last
    resolvers[0](revisionsResponse([{ revisionId: 1, version: 1 }]));

    await screen.findByRole("table");
    expect(screen.getByRole("link", { name: "Version 3" })).toBeVisible();
    expect(screen.queryByRole("link", { name: "Version 1" })).not.toBeInTheDocument();
  });

  test("the dialog is accessible", async () => {
    vi.spyOn(InvApiService, "get").mockImplementation(() =>
      Promise.resolve(revisionsResponse([{ revisionId: 100, version: 1 }])),
    );
    const subsample = makeMockSubSample({ version: 2 });
    const { baseElement } = await renderVersionHistory(subsample);
    fireEvent.click(screen.getByRole("button", { name: "View version history" }));
    await screen.findByRole("table");

    // @ts-expect-error toBeAccessible is from @sa11y/vitest
    await expect(baseElement).toBeAccessible(); // eslint-disable-line @typescript-eslint/no-unsafe-call
  });
});
