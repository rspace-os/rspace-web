/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen, fireEvent } from "@testing-library/react";
import "@testing-library/jest-dom";
import NavigateContext from "../../../stores/contexts/Navigate";
import SearchContext from "../../../stores/contexts/Search";
import AlwaysNewWindowNavigationContext from "../../../components/AlwaysNewWindowNavigationContext";
import InnerSearchNavigationContext from "../InnerSearchNavigationContext";
import Search from "../../../stores/models/Search";
import AlwaysNewFactory from "../../../stores/models/Factory/AlwaysNewFactory";
import ApiServiceBase from "../../../common/ApiServiceBase";
import { AxiosResponse } from "axios";
import "../../../__tests__/assertUrlSearchParams";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

type TriggersSearchNavigateArgs = {
  skipToParentContext?: boolean;
};

const TriggersSearchNavigate = ({
  skipToParentContext,
}: TriggersSearchNavigateArgs) => {
  const { useNavigate } = React.useContext(NavigateContext);
  const navigate = useNavigate();

  return (
    <button
      onClick={() =>
        navigate("/inventory/search?query=foo", { skipToParentContext })
      }
    >
      Click me!
    </button>
  );
};

const TriggersPermalinkNavigate = () => {
  const { useNavigate } = React.useContext(NavigateContext);
  const navigate = useNavigate();

  return (
    <button onClick={() => navigate("/inventory/container/1")}>
      Click me!
    </button>
  );
};

describe("InnerSearchNavigationContext", () => {
  test("navigate calls should update the search parameters.", () => {
    const querySpy = jest
      .spyOn(ApiServiceBase.prototype, "query")
      .mockImplementation(() =>
        Promise.resolve({
          data: { records: [] },
          status: 200,
          statusText: "OK",
          headers: {},
          config: {},
        } as AxiosResponse)
      );

    const search = new Search({
      factory: new AlwaysNewFactory(),
    });

    render(
      <InnerSearchNavigationContext>
        <SearchContext.Provider
          value={{
            search,
            differentSearchForSettingActiveResult: search,
          }}
        >
          <TriggersSearchNavigate />
        </SearchContext.Provider>
      </InnerSearchNavigationContext>
    );

    fireEvent.click(screen.getByRole("button", { name: /Click me!/ }));

    expect(querySpy).toHaveBeenLastCalledWith(
      "search",
      expect.urlSearchParamContaining({ query: "foo" })
    );
  });

  describe("when the parent context is AlwaysNewWindowNavigationContext", () => {
    test("navigate calls with skipToParentContext set to true should open /inventory/search calls in a new window.", () => {
      const openSpy = jest.spyOn(window, "open").mockImplementation(() => null);

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
        </AlwaysNewWindowNavigationContext>
      );

      fireEvent.click(screen.getByRole("button", { name: /Click me!/ }));

      expect(openSpy).toHaveBeenCalled();
    });
    test("navigate calls to permalink pages should always open in a new window.", () => {
      const openSpy = jest.spyOn(window, "open").mockImplementation(() => null);

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
        </AlwaysNewWindowNavigationContext>
      );

      fireEvent.click(screen.getByRole("button", { name: /Click me!/ }));

      expect(openSpy).toHaveBeenCalled();
    });
  });

  test("Pre-existing search parameters are kept, enforcing the parentGlobalId restriction", () => {
    const querySpy = jest
      .spyOn(ApiServiceBase.prototype, "query")
      .mockImplementation(() =>
        Promise.resolve({
          data: { records: [] },
          status: 200,
          statusText: "OK",
          headers: {},
          config: {},
        } as AxiosResponse)
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
      </SearchContext.Provider>
    );

    fireEvent.click(screen.getByRole("button", { name: /Click me!/ }));

    expect(querySpy).toHaveBeenLastCalledWith(
      "search",
      expect.urlSearchParamContaining({ query: "foo", parentGlobalId: "SA1" })
    );
  });
});
