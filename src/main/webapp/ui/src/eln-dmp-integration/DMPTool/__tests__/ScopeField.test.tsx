/*
 */
import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";
import React from "react";
import { render, cleanup, screen, fireEvent } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import ScopeField, { type Scope } from "../ScopeField";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(cleanup);

describe("ScopeField", () => {
  test.each(["Mine", "Public", "Both"])(
    "getDMPs is called correctly when the scope is %s",
    (scope: string) => {
      cleanup();
      const getDMPs = vi.fn<[Scope], unknown[]>();

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

