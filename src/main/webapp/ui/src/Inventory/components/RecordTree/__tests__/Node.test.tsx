/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import { makeMockContainer } from "../../../../stores/models/__tests__/ContainerModel/mocking";
import SearchContext from "../../../../stores/contexts/Search";
import Search from "../../../../stores/models/Search";
import { mockFactory } from "../../../../stores/definitions/__tests__/Factory/mocking";
import NavigateToNode from "../NavigateToNode";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import { SimpleTreeView } from "@mui/x-tree-view/SimpleTreeView";

import Node from "../Node";

jest.mock("../../../../stores/stores/RootStore", () => () => ({
  searchStore: {
    search: {},
  },
  uiStore: {
    addAlert: () => {},
  },
  authStore: {
    isSynchronizing: false,
  },
}));

// mocking this to avoid testing dependency
jest.mock("../NavigateToNode", () => jest.fn(() => <div></div>));

// jest.mock("../../../../theme", () => ({
//   __esModule: true,
//   default: materialTheme,
//   globalStyles: () => ({
//     greyOut: "MOCK_GREY_OUT_CLASS_NAME",
//   }),
// }));

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("Node", () => {
  describe("When the node in question is a container with contents, there should", () => {
    test("Be a button that navigates to the container's contents", () => {
      const search = new Search({
        factory: mockFactory(),
      });
      search.alwaysFilterOut = () => true;
      const container = makeMockContainer();
      jest.spyOn(container, "loadChildren").mockImplementation(() => {});
      render(
        <ThemeProvider theme={materialTheme}>
          <SearchContext.Provider
            value={{
              search,
              differentSearchForSettingActiveResult: search,
            }}
          >
            <SimpleTreeView>
              <Node node={container} />
            </SimpleTreeView>
          </SearchContext.Provider>
        </ThemeProvider>
      );
      expect(NavigateToNode).toHaveBeenCalledWith(
        { node: container },
        expect.anything()
      );
    });
  });
  test("When the record does not have a preview image, the record's type should be included in the treeitem's accessible name", () => {
    /*
     * If a record does not have a preview image, then an icon denoting the
     * record's type is shown to the left of the record's name in tree view.
     * This icon should have a tooltip, and an aria-label that makes this type
     * information available to users using accessibilty technologies. The
     * accessible name for the treeitem should therefore include both the type
     * and name of the record.
     */
    const search = new Search({
      factory: mockFactory(),
    });
    search.alwaysFilterOut = () => true;
    const container = makeMockContainer();
    render(
      <ThemeProvider theme={materialTheme}>
        <SearchContext.Provider
          value={{
            search,
            differentSearchForSettingActiveResult: search,
          }}
        >
          <SimpleTreeView>
            <Node node={container} />
          </SimpleTreeView>
        </SearchContext.Provider>
      </ThemeProvider>
    );
    expect(screen.getByRole("treeitem", { name: /Container/ })).toBeVisible();
  });
});
