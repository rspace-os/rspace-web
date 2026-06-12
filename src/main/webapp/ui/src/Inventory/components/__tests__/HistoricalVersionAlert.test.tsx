/*
 * @vitest-environment jsdom
 */

import { screen } from "@testing-library/react";
// biome-ignore lint/correctness/noUnusedImports: initial biome migration
import React from "react";
import { describe, expect, test, vi } from "vitest";
import { render } from "@/__tests__/customQueries";
import "@testing-library/jest-dom/vitest";
import { makeMockContainer } from "../../../stores/models/__tests__/ContainerModel/mocking";
import { makeMockSubSample } from "../../../stores/models/__tests__/SubSampleModel/mocking";
import HistoricalVersionAlert from "../HistoricalVersionAlert";

vi.mock("../../../common/InvApiService", () => ({
  default: {},
}));
vi.mock("../../../stores/stores/RootStore", () => ({
  default: () => ({
    unitStore: {
      getUnit: () => ({ label: "ml" }),
    },
  }),
}));

describe("HistoricalVersionAlert", () => {
  test("renders the version and a link to the latest record for a historical record", () => {
    const subsample = makeMockSubSample({
      version: 2,
      historicalVersion: true,
      globalId: "SS1v2",
    });
    render(<HistoricalVersionAlert record={subsample} />);

    expect(screen.getByText(/version 2/i)).toBeVisible();
    const link = screen.getByRole("link");
    expect(link).toHaveAttribute("href", "/inventory/subsample/1");
  });

  test("describes the version as read-only and drops the 'may not be the latest' hedge", () => {
    // viewing the latest version's snapshot still shows this banner, so the old
    // "may not be the latest version" wording was sometimes wrong (PR #831 review)
    const subsample = makeMockSubSample({
      version: 2,
      historicalVersion: true,
      globalId: "SS1v2",
    });
    render(<HistoricalVersionAlert record={subsample} />);

    const alert = screen.getByRole("alert");
    expect(alert).toHaveTextContent("It is read-only.");
    expect(alert).not.toHaveTextContent(/may not be the latest version/i);
  });

  test("renders the back-link with no orphaned full stop trailing it", () => {
    const subsample = makeMockSubSample({
      version: 2,
      historicalVersion: true,
      globalId: "SS1v2",
    });
    render(<HistoricalVersionAlert record={subsample} />);

    const link = screen.getByRole("link", { name: /view the latest version/i });
    expect(link).toHaveAttribute("href", "/inventory/subsample/1");
    // the trailing full stop that orphaned onto its own line is gone (PR #831 review)
    expect(screen.getByRole("alert")).not.toHaveTextContent(/View the latest version\s*\./);
  });

  test("makes only the 'View the latest version' words an obvious underlined link", () => {
    const subsample = makeMockSubSample({
      version: 2,
      historicalVersion: true,
      globalId: "SS1v2",
    });
    render(<HistoricalVersionAlert record={subsample} />);

    const link = screen.getByRole("link", { name: /view the latest version/i });
    // only those words are the link, not the surrounding "It is read-only." text
    expect(link).toHaveTextContent(/^View the latest version$/);
    // rendered as an obvious, always-underlined link (consistent with other
    // Inventory links), not plain text
    expect(link.className).toMatch(/underlineAlways/);
  });

  test("renders nothing for a live record", () => {
    const subsample = makeMockSubSample();
    const { container } = render(<HistoricalVersionAlert record={subsample} />);
    expect(container).toBeEmptyDOMElement();
  });

  test("explains that contents are excluded for a historical container", () => {
    const container = makeMockContainer({
      version: 2,
      historicalVersion: true,
      globalId: "IC1v2",
    });
    render(<HistoricalVersionAlert record={container} />);

    expect(screen.getByText(/contents are not part of/i)).toBeVisible();
  });

  test("does not mention contents for a historical subsample", () => {
    const subsample = makeMockSubSample({
      version: 2,
      historicalVersion: true,
      globalId: "SS1v2",
    });
    render(<HistoricalVersionAlert record={subsample} />);

    expect(screen.queryByText(/contents are not part of/i)).not.toBeInTheDocument();
  });

  test("the alert is accessible", async () => {
    const subsample = makeMockSubSample({
      version: 2,
      historicalVersion: true,
      globalId: "SS1v2",
    });
    const { baseElement } = render(<HistoricalVersionAlert record={subsample} />);

    // @ts-expect-error toBeAccessible is from @sa11y/vitest
    await expect(baseElement).toBeAccessible(); // eslint-disable-line @typescript-eslint/no-unsafe-call
  });
});
