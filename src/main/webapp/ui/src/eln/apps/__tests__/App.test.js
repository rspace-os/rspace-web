/*
 * @jest-environment jsdom
 */
//@flow strict
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom";
import { axe, toHaveNoViolations } from "jest-axe";
import App from "../App";
import "../../../__tests__/assertSemanticHeadings";
import MockAdapter from "axios-mock-adapter";
import * as axios from "axios";
import materialTheme from "../../../theme";
import { ThemeProvider } from "@mui/material/styles";
import "../../../../__mocks__/matchMedia";

const mockAxios = new MockAdapter(axios);

expect.extend(toHaveNoViolations);

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("Apps page", () => {
  describe("Accessibility", () => {
    test("Should have no axe violations.", async () => {
      mockAxios.onPost("integration/allIntegrations").reply(200, {
        success: false,
        data: null,
        error: "",
      });
      mockAxios.onGet("livechatProperties").reply(200, {
        livechatEnabled: false,
      });

      const { container } = render(
        <ThemeProvider theme={materialTheme}>
          <App />
        </ThemeProvider>
      );

      await screen.findAllByText(/Something went wrong!/i);

      // $FlowExpectedError[incompatible-call] See expect.extend above
      expect(await axe(container)).toHaveNoViolations();
    });
  });

  test("Has all of the correct headings.", async () => {
    mockAxios.onPost("integration/allIntegrations").reply(200, {
      success: false,
      data: null,
      error: "",
    });

    const { container } = render(
      <ThemeProvider theme={materialTheme}>
        <App />
      </ThemeProvider>
    );

    await screen.findAllByText(/Something went wrong!/i);

    // $FlowExpectedError[incompatible-call] See expect.extend in assertSemanticHeadings
    expect(container).assertHeadings([
      { level: 1, content: "Apps" },
      { level: 2, content: "Enabled" },
      { level: 2, content: "Disabled" },
      { level: 2, content: "Unavailable" },
    ]);
  });
});
