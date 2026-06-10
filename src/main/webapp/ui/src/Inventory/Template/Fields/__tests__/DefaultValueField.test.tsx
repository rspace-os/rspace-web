import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import { makeMockField } from "../../../../stores/models/__tests__/FieldModel/mocking";
import DefaultValueField from "../DefaultValueField";

vi.mock("../../../../common/InvApiService", () => ({ default: {} }));

// jsdom does not implement matchMedia, which MUI components rely on
Object.defineProperty(window, "matchMedia", {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

describe("DefaultValueField", () => {
  describe("link fields' allowed relationship types", () => {
    it("greys out already-selected relation types in the dropdown instead of letting them toggle off", async () => {
      const user = userEvent.setup();
      const field = makeMockField({
        type: "link",
        allowedRelationTypes: ["IsCitedBy"],
      });

      render(
        <ThemeProvider theme={materialTheme}>
          <DefaultValueField field={field} editing />
        </ThemeProvider>,
      );

      await user.click(
        screen.getByRole("combobox", { name: /allowed relationship types/i }),
      );
      const chosen = await screen.findByRole("option", { name: "IsCitedBy" });
      expect(chosen).toHaveAttribute("aria-disabled", "true");

      // a real pointer cannot reach the disabled option (pointer-events: none),
      // so the chosen set cannot be toggled off from the dropdown
      await expect(user.click(chosen)).rejects.toThrow(/pointer-events: none/);
      expect(field.allowedRelationTypes).toEqual(["IsCitedBy"]);
    });

    it("leaves unchosen relation types selectable", async () => {
      const user = userEvent.setup();
      const field = makeMockField({
        type: "link",
        allowedRelationTypes: ["IsCitedBy"],
      });

      render(
        <ThemeProvider theme={materialTheme}>
          <DefaultValueField field={field} editing />
        </ThemeProvider>,
      );

      await user.click(
        screen.getByRole("combobox", { name: /allowed relationship types/i }),
      );
      const unchosen = await screen.findByRole("option", { name: "Cites" });
      expect(unchosen).not.toHaveAttribute("aria-disabled", "true");

      await user.click(unchosen);
      expect(field.allowedRelationTypes).toEqual(["IsCitedBy", "Cites"]);
    });
  });
});
