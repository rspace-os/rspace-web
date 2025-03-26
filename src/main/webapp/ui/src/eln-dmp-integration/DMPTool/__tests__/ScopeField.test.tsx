/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen, fireEvent } from "@testing-library/react";
import "@testing-library/jest-dom";
import ScopeField, { type Scope } from "../ScopeField";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";
import each from "jest-each";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("ScopeField", () => {
  each(["Mine", "Public", "Both"]).test(
    "getDMPs is called correctly when the scope is %s",
    (scope: string) => {
      cleanup();
      const getDMPs = jest.fn<[Scope], unknown[]>();

      render(
        <ThemeProvider theme={materialTheme}>
          <ScopeField getDMPs={getDMPs} />
        </ThemeProvider>
      );

      fireEvent.click(screen.getByRole("radio", { name: scope }));
      expect(getDMPs).toHaveBeenCalledTimes(1);
      expect(getDMPs).toHaveBeenCalledWith(scope.toUpperCase());
    }
  );
});
