import "@/stores/stores/RootStore";
import "@/__tests__/__mocks__/resizeObserver";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type React from "react";
import { useTranslation } from "react-i18next";
import { beforeAll, describe, expect, it, vi } from "vitest";
import { createRealI18nWrapper } from "@/__tests__/helpers/realI18n";
import common from "@/modules/common/i18n/locales/en-US/common.json";
import inventory from "@/modules/common/i18n/locales/en-US/inventory.json";
import { makeMockContainer } from "@/stores/models/__tests__/ContainerModel/mocking";
import type ContainerModel from "@/stores/models/ContainerModel";
import type { ContainerAttrs } from "@/stores/models/ContainerModel";
import type Search from "@/stores/models/Search";
import materialTheme from "@/theme";
import PlacementStep from "../PlacementStep";
import { type PlacementSelection, prepareContainer } from "../placement";

let InEnglish: React.ComponentType<{ children: React.ReactNode }>;
beforeAll(async () => {
  InEnglish = await createRealI18nWrapper({ resources: { common, inventory }, defaultNS: "common" });
});

const picked = vi.hoisted(() => ({ container: null as unknown }));
/** The results view: inside a container it is that container's grid, otherwise the step's own search. */
vi.mock("../../../Search/SearchView", async () => {
  const { useContext } = await import("react");
  const { default: SearchContext } = await import("@/stores/contexts/Search");
  return {
    default: () => {
      const { search, scopedResult } = useContext(SearchContext);
      if (scopedResult) return <div data-testid="container-content" />;
      return (
        <button
          type="button"
          data-testid="picker-pick"
          onClick={() => (search as Search).callbacks?.setActiveResult?.(picked.container as ContainerModel)}
        />
      );
    },
  };
});

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
    <ThemeProvider theme={materialTheme}>
      <InEnglish>
        <PlacementStep value={value} onChange={onChange} count={count} />
      </InEnglish>
    </ThemeProvider>,
  );
  return onChange;
}

describe("PlacementStep", () => {
  it("offers the workbench as the selected choice and hides the picker", () => {
    renderStep({ mode: "workbench" });
    expect(screen.getByRole("radio", { name: "Leave on my workbench" })).toBeChecked();
    expect(screen.queryByTestId("picker-pick")).not.toBeInTheDocument();
  });

  it("offers a plain search box over the results, with no filter controls or parameter chips", () => {
    renderStep({ mode: "container", container: null });
    expect(screen.getByRole("searchbox")).toBeInTheDocument();
    expect(screen.getByTestId("picker-pick")).toBeInTheDocument();
    for (const name of ["Type", "Owner", "Bench", "Status", "Tags"])
      expect(screen.queryByRole("button", { name })).not.toBeInTheDocument();
    expect(screen.queryByText(/^(Type|Status):/)).not.toBeInTheDocument();
  });

  it("lets the user switch the results between tree and list", async () => {
    const user = userEvent.setup();
    renderStep({ mode: "container", container: null });
    await user.click(screen.getByRole("button", { name: "Change view" }));
    expect(screen.getByRole("menuitem", { name: "Tree View" })).toHaveAttribute("aria-current", "true");
    await user.click(screen.getByRole("menuitem", { name: "List View" }));
    await user.click(screen.getByRole("button", { name: "Change view" }));
    expect(screen.getByRole("menuitem", { name: "List View" })).toHaveAttribute("aria-current", "true");
    expect(screen.queryByRole("menuitem", { name: "Card View" })).not.toBeInTheDocument();
  });

  it("switches to container mode with nothing picked yet", async () => {
    const user = userEvent.setup();
    const onChange = renderStep({ mode: "workbench" });
    await user.click(screen.getByRole("radio", { name: "Choose a location" }));
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

describe("the remembered-container note", () => {
  function Note() {
    const { t } = useTranslation("inventory");
    return <p>{t("operations.placement.rememberedUnavailable", { container: "Box" })}</p>;
  }

  it("asks the user to choose, since the wizard no longer falls back to the workbench", () => {
    render(
      <InEnglish>
        <Note />
      </InEnglish>,
    );
    expect(screen.getByText(/Choose another location, or leave them on your workbench\.$/)).toBeInTheDocument();
    expect(screen.queryByText(/will stay on your workbench/)).not.toBeInTheDocument();
  });
});
