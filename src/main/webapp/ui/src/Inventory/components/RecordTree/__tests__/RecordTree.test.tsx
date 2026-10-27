import { ThemeProvider } from "@mui/material/styles";
import { treeItemClasses } from "@mui/x-tree-view/TreeItem";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import SearchContext from "../../../../stores/contexts/Search";
import { mockFactory } from "../../../../stores/definitions/__tests__/Factory/mocking";
import { containerAttrs, makeMockContainer } from "../../../../stores/models/__tests__/ContainerModel/mocking";
import type ContainerModel from "../../../../stores/models/ContainerModel";
import Search from "../../../../stores/models/Search";
import materialTheme from "../../../../theme";
import RecordTree from "../RecordTree";

vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    searchStore: { search: {} },
    uiStore: { addAlert: () => {}, setVisiblePanel: () => {} },
    authStore: { isSynchronizing: false },
    moveStore: {
      selectedResultsIncludesContainers: false,
      selectedResultsIncludesSubSamples: false,
      selectedResults: [],
    },
    peopleStore: { currentUser: { username: "user" } },
  }),
}));

vi.mock("../NavigateToNode", () => ({
  default: () => null,
}));

function containerWith(children: Array<ContainerModel>, globalId: string): ContainerModel {
  const container = makeMockContainer({ globalId, parentContainers: [containerAttrs({ globalId: "IC99" })] });
  container.contentSearch.cacheFetcher.results = children;
  vi.spyOn(container, "fetchAdditionalInfo").mockImplementation(() => Promise.resolve());
  vi.spyOn(container, "loadChildren").mockImplementation(() => {});
  return container;
}

function renderTree(results: Array<ContainerModel>) {
  const search = new Search({ factory: mockFactory() });
  search.fetcher.setResults(results);
  const setActiveResult = vi.spyOn(search, "setActiveResult").mockImplementation(() => Promise.resolve());
  render(
    <ThemeProvider theme={materialTheme}>
      <SearchContext.Provider value={{ search, differentSearchForSettingActiveResult: search }}>
        <RecordTree />
      </SearchContext.Provider>
    </ThemeProvider>,
  );
  return { search, setActiveResult };
}

describe("RecordTree", () => {
  test("Space on a focused row selects it", async () => {
    const user = userEvent.setup();
    const container = containerWith([], "IC1");
    const { setActiveResult } = renderTree([container]);

    await user.tab();
    await user.keyboard(" ");

    expect(setActiveResult).toHaveBeenCalledWith(container);
  });

  test("Enter on a row with no children selects it", async () => {
    const user = userEvent.setup();
    const container = containerWith([], "IC1");
    const { setActiveResult } = renderTree([container]);

    await user.tab();
    await user.keyboard("{Enter}");

    expect(setActiveResult).toHaveBeenCalledWith(container);
  });

  test("Space on the already selected row does not clear the selection", async () => {
    const user = userEvent.setup();
    const container = containerWith([], "IC1");
    const { search, setActiveResult } = renderTree([container]);
    search.activeResult = container;

    await user.tab();
    await user.keyboard(" ");

    expect(setActiveResult).not.toHaveBeenCalledWith(null);
  });

  test("A click on the expand arrow does not select the row", async () => {
    const user = userEvent.setup();
    const container = containerWith([containerWith([], "IC2")], "IC1");
    const { setActiveResult } = renderTree([container]);

    const row = screen.getAllByRole("treeitem")[0];
    // biome-ignore lint/style/noNonNullAssertion: the row has children, so the expand arrow is rendered
    await user.click(row.querySelector<HTMLElement>(`.${treeItemClasses.iconContainer}`)!);

    expect(setActiveResult).not.toHaveBeenCalled();
  });
});
