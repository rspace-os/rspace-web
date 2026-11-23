import "@/stores/stores/RootStore";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { AxiosResponse } from "axios";
import React from "react";
import { describe, expect, test, vi } from "vitest";
import ApiServiceBase from "../../../common/ApiServiceBase";
import AlwaysNewWindowNavigationContext from "../../../components/AlwaysNewWindowNavigationContext";
import NavigateContext from "../../../stores/contexts/Navigate";
import SearchContext from "../../../stores/contexts/Search";
import AlwaysNewFactory from "../../../stores/models/Factory/AlwaysNewFactory";
import Search from "../../../stores/models/Search";
import InnerSearchNavigationContext from "../InnerSearchNavigationContext";

import "@/__tests__/assertUrlSearchParams";

type TriggersSearchNavigateArgs = {
  skipToParentContext?: boolean;
};
const TriggersSearchNavigate = ({ skipToParentContext }: TriggersSearchNavigateArgs) => {
  const { useNavigate } = React.useContext(NavigateContext);

  const navigate = useNavigate();
  return (
    <button type="button" onClick={() => navigate("/inventory/search?query=foo", { skipToParentContext })}>
      {"Click me!"}
    </button>
  );
};
const TriggersPermalinkNavigate = () => {
  const { useNavigate } = React.useContext(NavigateContext);

  const navigate = useNavigate();
  // biome-ignore lint/a11y/useButtonType: initial biome migration
  return <button onClick={() => navigate("/inventory/container/1")}>{"Click me!"}</button>;
};
describe("InnerSearchNavigationContext", () => {
  test("navigate calls should update the search parameters.", async () => {
    const user = userEvent.setup();
    const querySpy = vi.spyOn(ApiServiceBase.prototype, "query").mockImplementation(() =>
      Promise.resolve({
        data: { records: [] },
        status: 200,
        statusText: "OK",
        headers: {},
        config: {},
      } as AxiosResponse),
    );
    const search = new Search({
      factory: new AlwaysNewFactory(),
    });
    render(
      <SearchContext.Provider
        value={{
          search,
          differentSearchForSettingActiveResult: search,
        }}
      >
        <InnerSearchNavigationContext>
          <TriggersSearchNavigate />
        </InnerSearchNavigationContext>
      </SearchContext.Provider>,
    );

    await user.click(screen.getByRole("button", { name: "Click me!" }));
    expect(querySpy).toHaveBeenCalledWith("search", expect.urlSearchParamContaining({ query: "foo" }));
  });
  describe("when the parent context is AlwaysNewWindowNavigationContext", () => {
    test("navigate calls with skipToParentContext set to true should open /inventory/search calls in a new window.", async () => {
      const user = userEvent.setup();
      const openSpy = vi.spyOn(window, "open").mockImplementation(() => null);
      const search = new Search({
        factory: new AlwaysNewFactory(),
      });
      render(
        <AlwaysNewWindowNavigationContext>
          <InnerSearchNavigationContext>
            <SearchContext.Provider
              value={{
                search,
                differentSearchForSettingActiveResult: search,
              }}
            >
              <TriggersSearchNavigate skipToParentContext={true} />
            </SearchContext.Provider>
          </InnerSearchNavigationContext>
        </AlwaysNewWindowNavigationContext>,
      );

      await user.click(screen.getByRole("button", { name: "Click me!" }));
      expect(openSpy).toHaveBeenCalled();
    });
    test("navigate calls to permalink pages should always open in a new window.", async () => {
      const user = userEvent.setup();
      const openSpy = vi.spyOn(window, "open").mockImplementation(() => null);
      const search = new Search({
        factory: new AlwaysNewFactory(),
      });
      render(
        <AlwaysNewWindowNavigationContext>
          <InnerSearchNavigationContext>
            <SearchContext.Provider
              value={{
                search,
                differentSearchForSettingActiveResult: search,
              }}
            >
              <TriggersPermalinkNavigate />
            </SearchContext.Provider>
          </InnerSearchNavigationContext>
        </AlwaysNewWindowNavigationContext>,
      );

      await user.click(screen.getByRole("button", { name: "Click me!" }));
      expect(openSpy).toHaveBeenCalled();
    });
  });
  test("Pre-existing search parameters are kept, enforcing the parentGlobalId restriction", async () => {
    const user = userEvent.setup();
    const querySpy = vi.spyOn(ApiServiceBase.prototype, "query").mockImplementation(() =>
      Promise.resolve({
        data: { records: [] },
        status: 200,
        statusText: "OK",
        headers: {},
        config: {},
      } as AxiosResponse),
    );
    const search = new Search({
      factory: new AlwaysNewFactory(),
      fetcherParams: {
        parentGlobalId: "SA1",
      },
    });
    render(
      <SearchContext.Provider
        value={{
          search,
          differentSearchForSettingActiveResult: search,
        }}
      >
        <InnerSearchNavigationContext>
          <TriggersSearchNavigate />
        </InnerSearchNavigationContext>
      </SearchContext.Provider>,
    );

    await user.click(screen.getByRole("button", { name: "Click me!" }));
    expect(querySpy).toHaveBeenCalledWith(
      "search",
      expect.urlSearchParamContaining({ query: "foo", parentGlobalId: "SA1" }),
    );
  });
});
