import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
// biome-ignore lint/correctness/noUnusedImports: initial biome migration
import React from "react";
import { afterEach, beforeAll, describe, expect, test, vi } from "vitest";
import NavigateContext from "../../stores/contexts/Navigate";
import { containerAttrs, makeMockContainer } from "../../stores/models/__tests__/ContainerModel/mocking";

const setActiveResult = vi.fn(() => Promise.resolve());
const generateNewQuery = vi.fn(() => new URLSearchParams("query=foo"));
const toggleSidebar = vi.fn();
const setVisiblePanel = vi.fn();
vi.mock("../../stores/use-stores", () => ({
  default: () => ({
    searchStore: {
      search: {
        setActiveResult,
      },
      fetcher: {
        generateNewQuery,
      },
    },
    uiStore: {
      isVerySmall: false,
      toggleSidebar,
      setVisiblePanel,
    },
  }),
}));
let useNavigateHelpers: typeof import("../useNavigateHelpers").default;
beforeAll(async () => {
  ({ default: useNavigateHelpers } = await import("../useNavigateHelpers"));
});
describe("useNavigateHelpers", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });
  describe("navigateToRecord should", () => {
    test("call setActiveResult", async () => {
      const user = userEvent.setup();
      const mockContainer = makeMockContainer({
        parentContainers: [containerAttrs({ globalId: "IC2" })],
      });
      const FunctionComponent = () => {
        const { navigateToRecord } = useNavigateHelpers();
        return (
          // biome-ignore lint/a11y/useButtonType: initial biome migration
          <button
            onClick={() => {
              void navigateToRecord(mockContainer);
            }}
          >
            Click me.
          </button>
        );
      };
      render(
        <NavigateContext.Provider
          value={{
            useNavigate: vi.fn().mockImplementation(() => () => {}),
            useLocation: vi.fn(),
          }}
        >
          <FunctionComponent />
        </NavigateContext.Provider>,
      );
      await user.click(screen.getByText("Click me."));

      await waitFor(() => expect(setActiveResult).toHaveBeenCalledWith(mockContainer));
    });
  });
  describe("navigateToSearch should", () => {
    test("not call setActiveResult", async () => {
      const user = userEvent.setup();
      const mockSearchParams = { query: "foo" } as const;
      const FunctionComponent = () => {
        const { navigateToSearch } = useNavigateHelpers();
        return (
          // biome-ignore lint/a11y/useButtonType: initial biome migration
          <button
            onClick={() => {
              navigateToSearch(mockSearchParams);
            }}
          >
            Click me.
          </button>
        );
      };
      render(
        <NavigateContext.Provider
          value={{
            useNavigate: vi.fn().mockImplementation(() => () => {}),
            useLocation: vi.fn(),
          }}
        >
          <FunctionComponent />
        </NavigateContext.Provider>,
      );
      await user.click(screen.getByText("Click me."));

      expect(setActiveResult).not.toHaveBeenCalled();
    });
  });
});
