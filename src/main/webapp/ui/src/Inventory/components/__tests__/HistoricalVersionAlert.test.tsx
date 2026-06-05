/*
 * @vitest-environment jsdom
 */
import React from "react";
import { describe, expect, test, vi } from "vitest";
import { render } from "@/__tests__/customQueries";
import { screen } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import HistoricalVersionAlert from "../HistoricalVersionAlert";
import { makeMockSubSample } from "../../../stores/models/__tests__/SubSampleModel/mocking";
import { makeMockContainer } from "../../../stores/models/__tests__/ContainerModel/mocking";

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

    expect(
      screen.queryByText(/contents are not part of/i),
    ).not.toBeInTheDocument();
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
