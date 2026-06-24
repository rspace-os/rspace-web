import { ThemeProvider } from "@mui/material/styles";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import materialTheme from "../../../theme";
import ScopeField, { type Scope } from "../ScopeField";

describe("ScopeField", () => {
  test.each([
    ["dmpIntegrations.scope.mine", "MINE"],
    ["dmpIntegrations.scope.public", "PUBLIC"],
    ["dmpIntegrations.scope.both", "BOTH"],
  ])("getDMPs is called correctly when the scope key is %s", (key: string, scopeValue: string) => {
    cleanup();

    const getDMPs = vi.fn<(scope: Scope) => void>();
    render(
      <ThemeProvider theme={materialTheme}>
        <ScopeField getDMPs={getDMPs} />
      </ThemeProvider>,
    );
    fireEvent.click(screen.getByRole("radio", { name: key }));
    expect(getDMPs).toHaveBeenCalledTimes(1);
    expect(getDMPs).toHaveBeenCalledWith(scopeValue);
  });
});
