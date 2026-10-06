import "@/stores/stores/RootStore";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type React from "react";
import { beforeAll, describe, expect, it, vi } from "vitest";
import { createRealI18nWrapper } from "@/__tests__/helpers/realI18n";
import common from "@/modules/common/i18n/locales/en-US/common.json";
import inventory from "@/modules/common/i18n/locales/en-US/inventory.json";
import { makeMockContainer } from "@/stores/models/__tests__/ContainerModel/mocking";
import type ContainerModel from "@/stores/models/ContainerModel";
import type { ContainerAttrs } from "@/stores/models/ContainerModel";
import type Search from "@/stores/models/Search";
import PlacementStep from "../PlacementStep";
import { type PlacementSelection, prepareContainer } from "../placement";

let InEnglish: React.ComponentType<{ children: React.ReactNode }>;
beforeAll(async () => {
  InEnglish = await createRealI18nWrapper({ resources: { common, inventory }, defaultNS: "common" });
});

const picked = vi.hoisted(() => ({ container: null as unknown }));
vi.mock("../../Picker/Picker", () => ({
  default: ({ search }: { search: Search }) => (
    <button
      type="button"
      data-testid="picker-pick"
      onClick={() => search.callbacks?.setActiveResult?.(picked.container as ContainerModel)}
    />
  ),
}));

vi.mock("../../../Search/SearchView", () => ({ default: () => <div data-testid="container-content" /> }));

/** A 2x2 grid box with `used` of its four locations taken. */
const gridBox = (used = 0, attrs: Partial<ContainerAttrs> = {}) => {
  const box = makeMockContainer({
    name: "Box",
    cType: "GRID",
    gridLayout: { columnsNumber: 2, rowsNumber: 2, columnsLabelType: "N123", rowsLabelType: "ABC" },
    locationsCount: 4,
    contentSummary: { totalCount: used, subSampleCount: used, containerCount: 0, instrumentCount: 0 },
    ...attrs,
  });
  vi.spyOn(box, "refreshAssociatedSearch").mockImplementation(() => {});
  return box;
};

function renderStep(value: PlacementSelection, count = 1) {
  const onChange = vi.fn();
  render(
    <InEnglish>
      <PlacementStep value={value} onChange={onChange} count={count} />
    </InEnglish>,
  );
  return onChange;
}

describe("PlacementStep", () => {
  it("offers the workbench as the selected choice and hides the picker", () => {
    renderStep({ mode: "workbench" });
    expect(screen.getByRole("radio", { name: "Leave on my workbench" })).toBeChecked();
    expect(screen.queryByTestId("picker-pick")).not.toBeInTheDocument();
  });

  it("switches to container mode with nothing picked yet", async () => {
    const user = userEvent.setup();
    const onChange = renderStep({ mode: "workbench" });
    await user.click(screen.getByRole("radio", { name: "Place in a container" }));
    expect(onChange).toHaveBeenCalledWith({ mode: "container", container: null });
  });

  it("reports the container picked in the step's own picker", async () => {
    const user = userEvent.setup();
    const container = makeMockContainer({ name: "Shelf" });
    picked.container = container;
    const onChange = renderStep({ mode: "container", container: null });
    await user.click(screen.getByTestId("picker-pick"));
    const [selection] = onChange.mock.lastCall as [PlacementSelection];
    expect(selection.mode === "container" && selection.container).toBe(container);
  });

  it("tells the user how many locations are free when the container is too full", () => {
    renderStep({ mode: "container", container: gridBox(3) }, 2);
    expect(screen.getByRole("alert")).toHaveTextContent("Only 1 of the 2 locations needed are free.");
  });

  it("asks for the remaining grid locations and shows the grid to pick them in", () => {
    const box = gridBox();
    prepareContainer(box, 2);
    box.locations?.[0].toggleSelected(true);
    renderStep({ mode: "container", container: box }, 2);
    expect(screen.getByRole("status")).toHaveTextContent("Select 1 more location.");
    expect(screen.getByTestId("container-content")).toBeInTheDocument();
  });

  it("confirms the destination once a list container is picked, with no grid to fill", () => {
    const list = makeMockContainer({ name: "Shelf", cType: "LIST" });
    renderStep({ mode: "container", container: list }, 2);
    expect(screen.getByRole("status")).toHaveTextContent("The new subsamples will be placed in Shelf.");
    expect(screen.queryByTestId("container-content")).not.toBeInTheDocument();
  });

  it("rejects an image container, which this step does not support yet", () => {
    renderStep({ mode: "container", container: makeMockContainer({ cType: "IMAGE" }) });
    expect(screen.getByRole("alert")).toHaveTextContent(/Image containers are not supported/);
  });
});
