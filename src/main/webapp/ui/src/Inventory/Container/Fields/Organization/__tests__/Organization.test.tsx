/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import { makeMockContainer } from "../../../../../stores/models/__tests__/ContainerModel/mocking";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../../theme";
import Organization from "../Organization";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("Organization", () => {
  test("The text 'well plate' should be shown to give the user a hint to use a grid container.", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <Organization container={makeMockContainer({ id: null })} />
      </ThemeProvider>
    );

    expect(screen.getByRole("group", { name: "Type" })).toHaveTextContent(
      "well plate"
    );
  });
});
