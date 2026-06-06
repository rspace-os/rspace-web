/*
 * @vitest-environment jsdom
 */
import { test, describe, expect, afterEach, vi } from "vitest";
import React from "react";
import { screen, fireEvent } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { type AxiosResponse } from "axios";
import VersionHistory from "../VersionHistory";
import InvApiService from "../../../../common/InvApiService";
import { makeMockSubSample } from "../../../../stores/models/__tests__/SubSampleModel/mocking";
import { makeMockSample } from "../../../../stores/models/__tests__/SampleModel/mocking";
import { makeMockTemplate } from "../../../../stores/models/__tests__/TemplateModel/mocking";
import { render } from "@/__tests__/customQueries";

vi.mock("../../../../common/InvApiService", () => ({
  default: {
    get: () => ({}),
  },
}));
vi.mock("../../../../stores/stores/RootStore", () => ({
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
      revisions: revisions.map(
        ({ revisionId, version, lastModified, modifiedByFullName }) => ({
          revisionId,
          revisionType: "MOD",
          record: {
            version,
            lastModified: lastModified ?? "2026-06-01T10:00:00.000Z",
            modifiedByFullName: modifiedByFullName ?? "Some User",
          },
        }),
      ),
      revisionsCount: revisions.length,
    },
    status: 200,
    statusText: "OK",
    headers: {},
    config: {},
  }) as AxiosResponse;

describe("VersionHistory", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  test("shows the current version and fetches the revisions list when opened", async () => {
    const spy = vi
      .spyOn(InvApiService, "get")
      .mockImplementation(() =>
        Promise.resolve(revisionsResponse([{ revisionId: 100, version: 1 }])),
      );
    const subsample = makeMockSubSample({ version: 2 });
    render(<VersionHistory record={subsample} />);

    expect(screen.getByText("2")).toBeVisible();
    fireEvent.click(
      screen.getByRole("button", { name: /view version history/i }),
    );
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
    render(<VersionHistory record={subsample} />);
    fireEvent.click(
      screen.getByRole("button", { name: /view version history/i }),
    );

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
    render(<VersionHistory record={subsample} />);
    fireEvent.click(
      screen.getByRole("button", { name: /view version history/i }),
    );

    await screen.findByRole("table");
    const link = screen.getByRole("link", { name: /version 1/i });
    expect(link).toHaveAttribute("href", "/inventory/subsample/1?version=1");
  });

  test("shows an error message when the fetch fails", async () => {
    vi.spyOn(InvApiService, "get").mockImplementation(() =>
      Promise.reject(new Error("An error")),
    );
    const subsample = makeMockSubSample({ version: 2 });
    render(<VersionHistory record={subsample} />);
    fireEvent.click(
      screen.getByRole("button", { name: /view version history/i }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent("An error");
  });

  test("renders nothing for an unsaved record", () => {
    const sample = makeMockSample({ id: null, globalId: null });
    const { container } = render(<VersionHistory record={sample} />);
    expect(container).toBeEmptyDOMElement();
  });

  test("shows the version history of a sample template", async () => {
    const spy = vi
      .spyOn(InvApiService, "get")
      .mockImplementation(() =>
        Promise.resolve(revisionsResponse([{ revisionId: 100, version: 1 }])),
      );
    const template = makeMockTemplate({ version: 2 });
    render(<VersionHistory record={template} />);

    expect(screen.getByText("2")).toBeVisible();
    fireEvent.click(
      screen.getByRole("button", { name: /view version history/i }),
    );
    expect(await screen.findByRole("table")).toBeVisible();
    expect(spy).toHaveBeenCalledWith("sampleTemplates/1/revisions");
  });

  test("a template version row links to the versioned template viewer URL", async () => {
    vi.spyOn(InvApiService, "get").mockImplementation(() =>
      Promise.resolve(revisionsResponse([{ revisionId: 100, version: 1 }])),
    );
    const template = makeMockTemplate({ version: 2 });
    render(<VersionHistory record={template} />);
    fireEvent.click(
      screen.getByRole("button", { name: /view version history/i }),
    );

    await screen.findByRole("table");
    const link = screen.getByRole("link", { name: /version 1/i });
    expect(link).toHaveAttribute(
      "href",
      "/inventory/sampletemplate/1?version=1",
    );
  });

  test("shows an informational message when there is no history yet", async () => {
    vi.spyOn(InvApiService, "get").mockImplementation(() =>
      Promise.resolve(revisionsResponse([])),
    );
    const subsample = makeMockSubSample({ version: 1 });
    render(<VersionHistory record={subsample} />);
    fireEvent.click(
      screen.getByRole("button", { name: /view version history/i }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      /no version history/i,
    );
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
    render(<VersionHistory record={subsample} />);
    fireEvent.click(
      screen.getByRole("button", { name: /view version history/i }),
    );

    await screen.findByRole("table");
    const currentRow = screen
      .getAllByRole("row")
      .find((row) => /version 2/i.test(row.textContent ?? ""));
    expect(currentRow).toHaveTextContent("(current)");
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
    render(<VersionHistory record={subsample} />);
    fireEvent.click(
      screen.getByRole("button", { name: /view version history/i }),
    );

    await screen.findByRole("table");
    expect(screen.getByText("Newest Editor")).toBeVisible();
    expect(screen.queryByText("Oldest Editor")).not.toBeInTheDocument();
  });

  test("the dialog is accessible", async () => {
    vi.spyOn(InvApiService, "get").mockImplementation(() =>
      Promise.resolve(revisionsResponse([{ revisionId: 100, version: 1 }])),
    );
    const subsample = makeMockSubSample({ version: 2 });
    const { baseElement } = render(<VersionHistory record={subsample} />);
    fireEvent.click(
      screen.getByRole("button", { name: /view version history/i }),
    );
    await screen.findByRole("table");

    // @ts-expect-error toBeAccessible is from @sa11y/vitest
    await expect(baseElement).toBeAccessible(); // eslint-disable-line @typescript-eslint/no-unsafe-call
  });
});
