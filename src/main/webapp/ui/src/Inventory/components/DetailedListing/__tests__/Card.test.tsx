/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import * as React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import materialTheme from "../../../../theme";
import { ThemeProvider } from "@mui/material/styles";

import { makeMockContainer } from "../../../../stores/models/__tests__/ContainerModel/mocking";
import { personAttrs } from "../../../../stores/models/__tests__/PersonModel/mocking";

import Card from "../Card";

jest.mock("../../../../stores/stores/RootStore", () => () => ({
  searchStore: {
    search: {
      alwaysFilterOut: () => false,
      fetcher: { basketSearch: false },
    },
  },
}));
jest.mock("react-router", () => ({
  useNavigate: () => ({}),
}));

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("Card", () => {
  describe("When the passed record has been deleted,", () => {
    test("the card's name should be crossed through.", () => {
      const mockContainer = makeMockContainer({
        owner: personAttrs(),
      });
      mockContainer.deleted = true;

      render(
        <ThemeProvider theme={materialTheme}>
          <Card record={mockContainer} />
        </ThemeProvider>
      );

      const titleText = screen.getByText("A list container");
      const decorationLineStyle = window
        .getComputedStyle(titleText)
        .getPropertyValue("text-decoration-line");
      expect(decorationLineStyle).toEqual("line-through");
    });
  });
});
