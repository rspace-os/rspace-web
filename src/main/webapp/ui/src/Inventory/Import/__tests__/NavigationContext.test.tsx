/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React, { useContext } from "react";
import { render, cleanup, screen, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom";
import each from "jest-each";
import NavigateContext from "../../../stores/contexts/Navigate";
import NavigationContext from "../NavigationContext";
import { storesContext } from "../../../stores/stores-context";
import { makeMockRootStore } from "../../../stores/stores/__tests__/RootStore/mocking";
import userEvent from "@testing-library/user-event";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

type NavigateToProps = {
  url: string;
};

const NavigateTo = ({ url }: NavigateToProps) => {
  const { useNavigate } = useContext(NavigateContext);
  const navigate = useNavigate();
  return (
    <button
      onClick={() => {
        navigate(url);
      }}
    >
      Click me
    </button>
  );
};

describe("NavigationContext", () => {
  describe("Performs correctly", () => {
    each`
      url                    | areChanges | userDiscards | expectToNavigate
      ${"/inventory/import"} | ${false}   | ${false}     | ${true}
      ${"/inventory/import"} | ${false}   | ${true}      | ${true}
      ${"/inventory/import"} | ${true}    | ${false}     | ${true}
      ${"/inventory/import"} | ${true}    | ${true}      | ${true}
      ${"/inventory/search"} | ${false}   | ${false}     | ${true}
      ${"/inventory/search"} | ${false}   | ${true}      | ${true}
      ${"/inventory/search"} | ${true}    | ${false}     | ${false}
      ${"/inventory/search"} | ${true}    | ${true}      | ${true}
    `.test(
      "{url = $url, areChanges = $areChanges, userDiscards = $userDiscards}",
      async ({
        url,
        areChanges,
        userDiscards,
        expectToNavigate,
      }: {
        url: string;
        areChanges: boolean;
        userDiscards: boolean;
        expectToNavigate: boolean;
      }) => {
        const user = userEvent.setup();
        const dummyUseLocation = {
          hash: "",
          pathname: "",
          search: "",
          state: {},
          key: "",
        };
        const navFn = jest.fn();
        render(
          <storesContext.Provider
            value={makeMockRootStore({
              uiStore: {
                dirty: areChanges,
                confirmDiscardAnyChanges: () =>
                  Promise.resolve(!areChanges || userDiscards),
              },
            })}
          >
            <NavigateContext.Provider
              value={{
                useNavigate: () => navFn,
                useLocation: () => dummyUseLocation,
              }}
            >
              <NavigationContext>
                <NavigateTo url={url} />
              </NavigationContext>
            </NavigateContext.Provider>
          </storesContext.Provider>
        );
        await user.click(screen.getByText("Click me"));
        await waitFor(() => {
          if (expectToNavigate) {
            expect(navFn).toHaveBeenCalled();
          } else {
            expect(navFn).not.toHaveBeenCalled();
          }
        });
      }
    );
  });
});
