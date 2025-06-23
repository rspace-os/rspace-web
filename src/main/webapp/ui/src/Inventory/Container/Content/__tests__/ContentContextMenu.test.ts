/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import * as React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import SearchContext from "../../../../stores/contexts/Search";
import Search from "../../../../stores/models/Search";
import {
  makeMockContainer,
  containerAttrs,
} from "../../../../stores/models/__tests__/ContainerModel/mocking";
import { mockFactory } from "../../../../stores/definitions/__tests__/Factory/mocking";
import LocationModel from "../../../../stores/models/LocationModel";
import ContainerModel from "../../../../stores/models/ContainerModel";
import ContentContextMenu from "../ContentContextMenu";
import materialTheme from "../../../../theme";
import { ThemeProvider } from "@mui/material/styles";
import Chip from "@mui/material/Chip";
import * as ArrayUtils from "../../../../util/ArrayUtils";
import { take, incrementForever } from "../../../../util/iterators";
import fc from "fast-check";

jest.mock("@mui/material/Chip", () =>
  jest.fn(() => React.createElement("div"))
);

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("ContentContextMenu", () => {
  describe("Has an open button that should", () => {
    test("be disabled when the only location selected is empty.", () => {
      const container = makeMockContainer({
        name: "A visual container",
        locations: [],
        cType: "IMAGE",
        locationsCount: 1,
        contentSummary: {
          totalCount: 0,
          subSampleCount: 0,
          containerCount: 0,
        },
      });
      container.locations = [
        new LocationModel({
          id: null,
          coordX: 2,
          coordY: 2,
          content: null,
          parentContainer: container,
        }),
      ];
      container.locations[0].toggleSelected(true);
      const search = new Search({
        factory: mockFactory(),
      });
      render(
        React.createElement(
          ThemeProvider,
          { theme: materialTheme },
          React.createElement(
            SearchContext.Provider,
            {
              value: {
                search,
                differentSearchForSettingActiveResult: search,
                scopedResult: container,
              },
            },
            React.createElement(ContentContextMenu)
          )
        )
      );

      expect(Chip).toHaveBeenCalledWith(
        expect.objectContaining({
          disabled: true,
          label: "Open",
        }),
        expect.anything()
      );
    });
  });
  test("When all locations are selected, the badge on the select all button should show the number of locations.", () => {
    fc.assert(
      fc.property(
        fc.tuple(
          fc.integer({ min: 1, max: 24 }),
          fc.integer({ min: 1, max: 24 })
        ),
        ([width, height]: [number, number]) => {
          cleanup();
          const container: ContainerModel = makeMockContainer({
            name: "A visual container",
            locations: ArrayUtils.outerProduct(
              [...take(incrementForever(), width)],
              [...take(incrementForever(), height)],
              (coordX: number, coordY: number) => ({
                coordX,
                coordY,
                content: containerAttrs(),
                id: null,
              })
            ).flat(),
            cType: "GRID",
            gridLayout: {
              rowsNumber: height,
              columnsNumber: width,
              columnsLabelType: "ABC",
              rowsLabelType: "ABC",
            },
            locationsCount: width * height,
            contentSummary: {
              totalCount: width * height,
              subSampleCount: 0,
              containerCount: width * height,
            },
          });
          for (const loc of container.locations ?? []) {
            loc.toggleSelected(true);
          }
          const search = new Search({
            factory: mockFactory(),
          });
          render(
            React.createElement(
              ThemeProvider,
              { theme: materialTheme },
              React.createElement(
                SearchContext.Provider,
                {
                  value: {
                    search,
                    differentSearchForSettingActiveResult: search,
                    scopedResult: container,
                  },
                },
                React.createElement(ContentContextMenu)
              )
            )
          );

          expect(screen.getByText(`${width * height}`)).toBeVisible();
        }
      ),
      { numRuns: 10 }
    );
  });
});
