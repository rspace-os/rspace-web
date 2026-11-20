import { StyledEngineProvider, ThemeProvider } from "@mui/material/styles";
import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, test } from "vitest";
import { page, userEvent } from "vitest/browser";
import materialTheme from "@/theme";
import FieldCard from "../FieldCard";

afterEach(() => {
  cleanup();
  document.getElementById("legacy-focus-reset")?.remove();
});

/** Legacy pages load admin.css, whose unlayered rule would otherwise beat MUI's layered styles. */
function addLegacyFocusReset(): void {
  const style = document.createElement("style");
  style.id = "legacy-focus-reset";
  style.textContent = "button:focus { outline: none; }";
  document.head.append(style);
}

describe("FieldCard header buttons", () => {
  test("show a 2px keyboard focus outline even under the legacy focus reset", async () => {
    addLegacyFocusReset();
    render(
      <StyledEngineProvider injectFirst enableCssLayer>
        <ThemeProvider theme={materialTheme}>
          <FieldCard
            name="Notes"
            onNameChange={() => {}}
            type="text"
            typeOptions={[{ value: "text", label: "Text" }]}
            onTypeChange={() => {}}
            canChangeType={false}
            mandatory={false}
            onMandatoryChange={() => {}}
            position={0}
            count={2}
            onMove={() => {}}
            canDelete
            onDelete={() => {}}
            editable
          />
        </ThemeProvider>
      </StyledEngineProvider>,
    );
    await page.getByRole("textbox", { name: /name/i }).click();
    const deleteButton = page.getByRole("button", { name: /delete/i });
    for (let presses = 0; presses < 6 && document.activeElement !== deleteButton.element(); presses++) {
      await userEvent.keyboard("{Tab}");
    }
    await expect.element(deleteButton).toHaveFocus();
    const focused = getComputedStyle(deleteButton.element());
    expect([focused.outlineStyle, focused.outlineWidth]).toEqual(["solid", "2px"]);
  });
});
