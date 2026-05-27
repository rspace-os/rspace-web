import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import ScopeField, { type Scope } from "../ScopeField";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";
describe("ScopeField", () => {
  test.each(["Mine", "Public", "Both"])(
    "getDMPs is called correctly when the scope is %s",
    async (scope: string) => {
      cleanup();
      const getDMPs = vi.fn<(scope: Scope) => void>();
      render(
        <ThemeProvider theme={materialTheme}>
          <ScopeField getDMPs={getDMPs} />
        </ThemeProvider>,
      );
      await userEvent.click(
        screen.getByRole("radio", {
          name: scope,
        }),
      );
      expect(getDMPs).toHaveBeenCalledTimes(1);
      expect(getDMPs).toHaveBeenCalledWith(scope.toUpperCase());
    },
  );
});
