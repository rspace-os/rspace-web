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
});
