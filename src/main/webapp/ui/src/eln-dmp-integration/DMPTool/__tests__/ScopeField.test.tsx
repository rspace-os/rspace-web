import { ThemeProvider } from "@mui/material/styles";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import materialTheme from "../../../theme";
import ScopeField, { type Scope } from "../ScopeField";

describe("ScopeField", () => {
  test.each([
    ["apps:dmpIntegrations.scope.mine", "MINE"],
    ["apps:dmpIntegrations.scope.public", "PUBLIC"],
    ["apps:dmpIntegrations.scope.both", "BOTH"],
  ])("getDMPs is called correctly when the scope key is %s", async (key: string, scopeValue: string) => {
    const user = userEvent.setup();
    cleanup();

    const getDMPs = vi.fn<(scope: Scope) => void>();
    render(
      <ThemeProvider theme={materialTheme}>
        <ScopeField getDMPs={getDMPs} />
      </ThemeProvider>,
    );
    await user.click(screen.getByRole("radio", { name: key }));
    expect(getDMPs).toHaveBeenCalledTimes(1);
    expect(getDMPs).toHaveBeenCalledWith(scopeValue);
  });
});
