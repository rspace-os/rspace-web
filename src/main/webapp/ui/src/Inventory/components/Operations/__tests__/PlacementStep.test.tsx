import "@/stores/stores/RootStore";
import "@/__tests__/__mocks__/resizeObserver";
import { ThemeProvider } from "@mui/material/styles";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { beforeAll, describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { createRealI18nWrapper } from "@/__tests__/helpers/realI18n";
import common from "@/modules/common/i18n/locales/en-US/common.json";
import inventory from "@/modules/common/i18n/locales/en-US/inventory.json";
import { containerAttrs, makeMockContainer } from "@/stores/models/__tests__/ContainerModel/mocking";
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

const picked = vi.hoisted(() => ({ container: null as unknown, loaded: Promise.resolve() }));
/** The results view: inside a container it is that container's grid, otherwise the step's own search. */
vi.mock("../../../Search/SearchView", async () => {
  const { useContext } = await import("react");
  const { default: SearchContext } = await import("@/stores/contexts/Search");
  return {
    default: () => {
      const { search, scopedResult } = useContext(SearchContext);
      if (scopedResult) return <div data-testid="container-content" />;
      const pick = async () => {
        const record = picked.container as ContainerModel;
        (search as Search).activeResult = record;
        await picked.loaded;
        (search as Search).callbacks?.setActiveResult?.(record);
      };
      return (
        <button
          type="button"
          data-testid="picker-pick"
          aria-label="Pick"
          data-active={(search as Search).activeResult?.name ?? ""}
          onClick={() => void pick()}
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

  describe("a container still loading when the user switches to the workbench", () => {
    function Harness({ onChange }: { onChange: (next: PlacementSelection) => void }) {
      const [value, setValue] = React.useState<PlacementSelection>({ mode: "container", container: null });
      return (
        <PlacementStep
          value={value}
          onChange={(next) => {
            onChange(next);
            setValue(next);
          }}
          count={1}
        />
      );
    }

    async function pickThenLeaveOnWorkbench() {
      const user = userEvent.setup();
      const onChange = vi.fn();
      let finishLoading = () => {};
      picked.container = makeMockContainer({ name: "Shelf" });
      picked.loaded = new Promise((resolve) => {
        finishLoading = resolve;
      });
      render(
        <ThemeProvider theme={materialTheme}>
          <InEnglish>
            <Harness onChange={onChange} />
          </InEnglish>
        </ThemeProvider>,
      );
      await user.click(screen.getByTestId("picker-pick"));
      await user.click(screen.getByRole("radio", { name: "Leave on my workbench" }));
      await act(async () => finishLoading());
      return { user, onChange };
    }

    it("is not applied once it finishes loading", async () => {
      const { onChange } = await pickThenLeaveOnWorkbench();
      expect(onChange).toHaveBeenLastCalledWith({ mode: "workbench" });
      expect(screen.getByRole("radio", { name: "Leave on my workbench" })).toBeChecked();
    });

    it("is not shown as picked when the user chooses a location again", async () => {
      const { user } = await pickThenLeaveOnWorkbench();
      await user.click(screen.getByRole("radio", { name: "Choose a location" }));
      expect(screen.getByTestId("picker-pick")).toHaveAttribute("data-active", "");
    });
  });

  describe("a container that finishes loading after it stopped being the user's pick", () => {
    const deferredPick = (name: string) => {
      let finishLoading = () => {};
      const loaded = new Promise<void>((resolve) => {
        finishLoading = resolve;
      });
      return { container: makeMockContainer({ name }), loaded, finishLoading };
    };
    const pick = async (user: ReturnType<typeof userEvent.setup>, p: ReturnType<typeof deferredPick>) => {
      picked.container = p.container;
      picked.loaded = p.loaded;
      await user.click(screen.getByTestId("picker-pick"));
    };

    it("is ignored when the user has since picked another container", async () => {
      const user = userEvent.setup();
      const onChange = renderStep({ mode: "container", container: null });
      const a = deferredPick("A");
      const b = deferredPick("B");
      await pick(user, a);
      await pick(user, b);
      await act(async () => b.finishLoading());
      await act(async () => a.finishLoading());
      expect(onChange).toHaveBeenLastCalledWith({ mode: "container", container: b.container });
    });

    it("is ignored once the step has been closed", async () => {
      const user = userEvent.setup();
      const onChange = vi.fn();
      const { unmount } = render(
        <ThemeProvider theme={materialTheme}>
          <InEnglish>
            <PlacementStep value={{ mode: "container", container: null }} onChange={onChange} count={1} />
          </InEnglish>
        </ThemeProvider>,
      );
      const a = deferredPick("A");
      await pick(user, a);
      unmount();
      await act(async () => a.finishLoading());
      expect(onChange).not.toHaveBeenCalled();
    });
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

  it("is accessible while the user picks grid locations", async () => {
    const box = gridBox();
    prepareContainer(box, 2);
    renderStep({ mode: "container", container: box }, 2);
    await expectAccessible(document.body);
  });

  it("asks the user to deselect an occupied location", () => {
    const box = gridBox(1, {
      locations: [{ id: 11, coordX: 1, coordY: 1, content: containerAttrs({ id: 9, globalId: "IC9" }) }],
    });
    prepareContainer(box, 1);
    box.locations?.[0].toggleSelected(true);
    renderStep({ mode: "container", container: box }, 1);
    expect(screen.getByRole("status")).toHaveTextContent("A selected location is already occupied. Deselect it.");
  });

  it("asks the user to deselect the surplus locations", () => {
    const box = gridBox();
    prepareContainer(box, 1);
    for (const location of box.locations?.slice(0, 3) ?? []) location.toggleSelected(true);
    renderStep({ mode: "container", container: box }, 1);
    expect(screen.getByRole("status")).toHaveTextContent("Deselect 2 locations.");
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
