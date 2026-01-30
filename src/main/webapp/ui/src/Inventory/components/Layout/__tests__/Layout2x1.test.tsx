/*
 */
import {
  describe,
  expect,
  beforeEach,
  it,
  vi,
} from "vitest";
import React from "react";
import {
  render,
  screen,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { storesContext } from "../../../../stores/stores-context";
import { makeMockRootStore } from "../../../../stores/stores/__tests__/RootStore/mocking";
import fc from "fast-check";

import Layout2x1 from "../Layout2x1";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";

beforeEach(() => {
  vi.clearAllMocks();
});


const fooBarTest = ({
  isSingleColumnLayout,
  visiblePanel,
  showFoo,
  showBar,
}: {
  isSingleColumnLayout: boolean;
  visiblePanel: "left" | "right";
  showFoo: boolean;
  showBar: boolean;
}) => {
  const rootStore = makeMockRootStore({
    uiStore: {
      isSingleColumnLayout,
      visiblePanel,
    },
  });
  render(
    <ThemeProvider theme={materialTheme}>
      <storesContext.Provider value={rootStore}>
        <Layout2x1 colLeft="foo" colRight="bar" />
      </storesContext.Provider>
    </ThemeProvider>
  );
  expect(screen.getByText("foo")).toBeInTheDocument();
  if (showFoo) expect(screen.getByText("foo")).toBeVisible();
  expect(screen.getByText("bar")).toBeInTheDocument();
  if (showBar) expect(screen.getByText("bar")).toBeVisible();
};

describe("Layout2x1", () => {
  /*
   * When isSingleColumnLayout is false (when the viewport is wide enough) both
   * side of the 2x1 layout should be shown, ignoring `visiblePanel`.
   */
  describe("When isSingleColumnLayout = false, both sides are shown", () => {
    it("Both sides are shown when visiblePanel = 'left'", () =>
      fooBarTest({
        isSingleColumnLayout: false,
        visiblePanel: "left",
        showFoo: true,
        showBar: true,
      }));
    it("Both sides are shown when visiblePanel = 'right'", () =>
      fooBarTest({
        isSingleColumnLayout: false,
        visiblePanel: "right",
        showFoo: true,
        showBar: true,
      }));
  });

  /*
   * When isSingleColumnLayout is true (when the viewport is not wide enough)
   * only one side of the 2x1 layout is shown, based on `visiblePanel`.
   */
  describe("When isSingleColumnLayout = true, one side is shown at a time", () => {
    it("When visiblePanel = 'left', only colLeft is rendered", () =>
      fooBarTest({
        isSingleColumnLayout: true,
        visiblePanel: "left",
        showFoo: true,
        showBar: false,
      }));
    it("When visiblePanel = 'right', only colRight is rendered", () =>
      fooBarTest({
        isSingleColumnLayout: true,
        visiblePanel: "right",
        showFoo: false,
        showBar: true,
      }));
  });

  describe("When isSingleColumnLayout = true and the content is the same", () => {
    it("both sides show the same.", () => {
      fc.assert(
        fc.property(fc.constantFrom("left", "right"), (selectedItems) => {
          const rootStore = makeMockRootStore({
            uiStore: {
              isSingleColumnLayout: true,
              visiblePanel: selectedItems,
            },
          });
          const { container } = render(
            <storesContext.Provider value={rootStore}>
              <Layout2x1 colLeft="foo" colRight="foo" />
            </storesContext.Provider>
          );
          expect(container).toHaveTextContent("foo");
        })
      );
    });
  });
});


