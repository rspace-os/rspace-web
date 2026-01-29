/*
 */
import {
  describe,
  test,
  expect,
  vi,
  beforeEach,
  afterEach,
  beforeAll,
} from "vitest";
import React from "react";
import { render, cleanup, screen, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import {
  makeMockContainer,
  containerAttrs,
} from "../../stores/models/__tests__/ContainerModel/mocking";
import NavigateContext from "../../stores/contexts/Navigate";
import userEvent from "@testing-library/user-event";

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

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(cleanup);

describe("useNavigateHelpers", () => {
  describe("navigateToRecord should", () => {
    test("call setActiveResult", async () => {
      const user = userEvent.setup();
      const mockContainer = makeMockContainer({
        parentContainers: [containerAttrs({ globalId: "IC2" })],
      });
      const FunctionComponent = () => {
        const { navigateToRecord } = useNavigateHelpers();
        return (
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
        </NavigateContext.Provider>
      );
      await user.click(screen.getByText("Click me."));

      await waitFor(() =>
        expect(setActiveResult).toHaveBeenCalledWith(mockContainer)
      );
    });
  });

  describe("navigateToSearch should", () => {
    test("not call setActiveResult", async () => {
      const user = userEvent.setup();
      const mockSearchParams = { query: "foo" } as const;
      const FunctionComponent = () => {
        const { navigateToSearch } = useNavigateHelpers();
        return (
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
        </NavigateContext.Provider>
      );
      await user.click(screen.getByText("Click me."));

      expect(setActiveResult).not.toHaveBeenCalled();
    });
  });
});
