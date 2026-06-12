import { ThemeProvider } from "@mui/material/styles";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
// biome-ignore lint/correctness/noUnusedImports: initial biome migration
import React from "react";
import { describe, expect, test, vi } from "vitest";
import materialTheme from "../../../theme";
import ScopeField, { type Scope } from "../ScopeField";

describe("ScopeField", () => {
  test.each(["Mine", "Public", "Both"])("getDMPs is called correctly when the scope is %s", (scope: string) => {
    cleanup();

    const getDMPs = vi.fn<(scope: Scope) => void>();
    render(
      <ThemeProvider theme={materialTheme}>
        <ScopeField getDMPs={getDMPs} />
      </ThemeProvider>,
    );
    fireEvent.click(screen.getByRole("radio", { name: scope }));
    expect(getDMPs).toHaveBeenCalledTimes(1);
    expect(getDMPs).toHaveBeenCalledWith(scope.toUpperCase());
  });
});
