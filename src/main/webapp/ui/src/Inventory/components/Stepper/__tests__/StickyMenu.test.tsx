import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { runInAction } from "mobx";
import { observer } from "mobx-react-lite";
import { HttpResponse, http } from "msw";
import { afterEach, describe, expect, test, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { DeploymentPropertyContext } from "@/hooks/api/useDeploymentProperty";
import { makeMockContainer } from "@/stores/models/__tests__/ContainerModel/mocking";
import { makeMockSubSample } from "@/stores/models/__tests__/SubSampleModel/mocking";
import { makeMockRootStore } from "@/stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "@/stores/stores-context";
import materialTheme from "@/theme";
import ContextMenu from "../../ContextMenu/ContextMenu";
import StickyMenu from "../StickyMenu";

vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => ({
    unitStore: { getUnit: () => ({ label: "ml" }) },
    searchStore: { activeResult: null },
  }),
}));

afterEach(() => vi.restoreAllMocks());

describe("StickyMenu selection scope", () => {
  test.each(["sample", "container"])("selecting a %s's contents disables only its sticky menu", async (kind) => {
    const user = userEvent.setup();
    // jsdom has no layout; give the menus room to display their actions.
    vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(function (this: HTMLElement) {
      return new DOMRect(0, 0, this.hasAttribute("data-idx") ? 100 : 1000, 40);
    });
    server.use(http.get("/deploymentproperties/ajax/property", () => HttpResponse.json("ALLOWED")));
    const child = makeMockSubSample();
    const parent = kind === "sample" ? child.sample : makeMockContainer();
    const nestedSearch = "contentSearch" in parent ? parent.contentSearch : parent.search;
    nestedSearch.fetcher.setResults([child]);
    const rootStore = makeMockRootStore({
      searchStore: {
        activeResult: parent,
        search: { processingContextActions: false, fetcher: { basketSearch: false }, editLoading: "no" },
      },
    });
    const Menus = observer(() => (
      <>
        <section aria-label="Search results">
          <ContextMenu
            selectedResults={[parent]}
            menuID="results"
            onSelectOptions={[{ text: "Select all", selection: () => {} }]}
            paddingTop={false}
            basketSearch={false}
          />
        </section>
        <section aria-label="Active record">
          <StickyMenu />
        </section>
        <label>
          {"Select child"}
          <input type="checkbox" checked={child.selected} onChange={() => child.toggleSelected()} />
        </label>
        {child.selected && (
          <section aria-label="Selected child">
            <ContextMenu selectedResults={[child]} menuID="content" paddingTop={false} basketSearch={false} />
          </section>
        )}
      </>
    ));
    const view = (
      <ThemeProvider theme={materialTheme}>
        <DeploymentPropertyContext.Provider value={new Map()}>
          <storesContext.Provider value={rootStore}>
            <Menus />
          </storesContext.Provider>
        </DeploymentPropertyContext.Provider>
      </ThemeProvider>
    );
    render(view);
    const edit = (region: string) =>
      within(screen.getByRole("region", { name: region })).getByRole("button", { name: "common:actions.edit" });
    expect(edit("Search results")).toBeEnabled();
    expect(edit("Active record")).toBeEnabled();
    await user.click(screen.getByRole("checkbox", { name: "Select child" }));
    expect(edit("Search results")).toBeEnabled();
    expect(edit("Active record")).toBeDisabled();
    expect(edit("Selected child")).toBeEnabled();
    await user.click(screen.getByRole("checkbox", { name: "Select child" }));
    expect(edit("Active record")).toBeEnabled();
    act(() => {
      runInAction(() => {
        parent.historicalVersion = true;
      });
    });
    expect(edit("Search results")).toBeDisabled();
    expect(edit("Active record")).toBeDisabled();
  });
});
