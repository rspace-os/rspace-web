import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useContext } from "react";
import type { Location } from "react-router";
import { describe, expect, test, vi } from "vitest";
import NavigateContext from "../../../stores/contexts/Navigate";
import { makeMockRootStore } from "../../../stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "../../../stores/stores-context";
import NavigationContext from "../NavigationContext";

type NavigateToProps = {
  url: string;
};
const NavigateTo = ({ url }: NavigateToProps) => {
  const { useNavigate } = useContext(NavigateContext);
  const navigate = useNavigate();
  return (
    // biome-ignore lint/a11y/useButtonType: initial biome migration
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
    test.each`
      url                    | areChanges | userDiscards | expectToNavigate
      ${"/inventory/import"} | ${false}   | ${false}     | ${true}
      ${"/inventory/import"} | ${false}   | ${true}      | ${true}
      ${"/inventory/import"} | ${true}    | ${false}     | ${true}
      ${"/inventory/import"} | ${true}    | ${true}      | ${true}
      ${"/inventory/search"} | ${false}   | ${false}     | ${true}
      ${"/inventory/search"} | ${false}   | ${true}      | ${true}
      ${"/inventory/search"} | ${true}    | ${false}     | ${false}
      ${"/inventory/search"} | ${true}    | ${true}      | ${true}
    `(
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
        } as Location;
        const navFn = vi.fn();
        render(
          <storesContext.Provider
            value={makeMockRootStore({
              uiStore: {
                dirty: areChanges,
                confirmDiscardAnyChanges: () => Promise.resolve(!areChanges || userDiscards),
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
          </storesContext.Provider>,
        );
        await user.click(screen.getByText("Click me"));
        await waitFor(() => {
          if (expectToNavigate) {
            expect(navFn).toHaveBeenCalled();
          } else {
            expect(navFn).not.toHaveBeenCalled();
          }
        });
      },
    );
  });
});
