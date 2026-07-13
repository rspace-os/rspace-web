/*
 * @vitest-environment jsdom
 */

import { screen } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import { render } from "@/__tests__/customQueries";
import "@testing-library/jest-dom/vitest";
import { makeMockContainer } from "../../../stores/models/__tests__/ContainerModel/mocking";
import { makeMockSubSample } from "../../../stores/models/__tests__/SubSampleModel/mocking";
import HistoricalVersionAlert from "../HistoricalVersionAlert";

vi.mock("../../../common/InvApiService", () => ({
  default: {},
}));
vi.mock("../../../stores/stores/getRootStore", () => ({
  default: () => ({
    unitStore: {
      getUnit: () => ({ label: "ml" }),
    },
  }),
}));

describe("HistoricalVersionAlert", () => {
  test("renders an alert for a historical record", () => {
    const subsample = makeMockSubSample({
      version: 2,
      historicalVersion: true,
      globalId: "SS1v2",
    });
    render(<HistoricalVersionAlert record={subsample} />);

    expect(screen.getByRole("alert")).toBeVisible();
  });

  test("does not retain 'may not be the latest' hedge", () => {
    const subsample = makeMockSubSample({
      version: 2,
      historicalVersion: true,
      globalId: "SS1v2",
    });
    render(<HistoricalVersionAlert record={subsample} />);

    const alert = screen.getByRole("alert");
    expect(alert).not.toHaveTextContent(/may not be the latest version/i);
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

    expect(screen.getByText("inventory:historicalVersion.contentsNotShown")).toBeVisible();
  });

  test("does not mention contents for a historical subsample", () => {
    const subsample = makeMockSubSample({
      version: 2,
      historicalVersion: true,
      globalId: "SS1v2",
    });
    render(<HistoricalVersionAlert record={subsample} />);

    expect(screen.queryByText("inventory:historicalVersion.contentsNotShown")).not.toBeInTheDocument();
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
