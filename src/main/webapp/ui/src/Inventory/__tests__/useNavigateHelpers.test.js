/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import { makeMockContainer } from "../../stores/models/__tests__/ContainerModel/mocking";
import NavigateContext from "../../stores/contexts/Navigate";
import Search from "../../stores/models/Search";
import useNavigateHelpers from "../useNavigateHelpers";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("useNavigateHelpers", () => {
  describe("navigateToRecord should", () => {
    test("call setActiveResult", () => {
      const mockContainer = makeMockContainer();
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

      const setActiveResultSpy = jest.spyOn(
        Search.prototype,
        "setActiveResult"
      );

      render(
        <NavigateContext.Provider
          value={{ useNavigate: jest.fn(), useLocation: jest.fn() }}
        >
          <FunctionComponent />
        </NavigateContext.Provider>
      );
      screen.getByText("Click me.").click();

      expect(setActiveResultSpy).toBeCalledWith(mockContainer);
    });
  });

  describe("navigateToSearch should", () => {
    test("not call setActiveResult", () => {
      const mockSearchParams = { query: "foo" };
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

      const setActiveResultSpy = jest.spyOn(
        Search.prototype,
        "setActiveResult"
      );

      render(
        <NavigateContext.Provider
          value={{
            useNavigate: jest
              .fn<[], () => void>()
              .mockImplementation(() => () => {}),
            useLocation: jest.fn(),
          }}
        >
          <FunctionComponent />
        </NavigateContext.Provider>
      );
      screen.getByText("Click me.").click();

      expect(setActiveResultSpy).not.toBeCalled();
    });
  });
});
