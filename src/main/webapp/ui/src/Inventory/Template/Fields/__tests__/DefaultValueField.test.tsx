import { ThemeProvider } from "@mui/material/styles";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@/__tests__/customQueries";
import { makeMockField } from "../../../../stores/models/__tests__/FieldModel/mocking";
import materialTheme from "../../../../theme";
import DefaultValueField from "../DefaultValueField";

vi.mock("../../../../common/InvApiService", () => ({ default: {} }));

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
        screen.getByRole("combobox", { name: "fields.templateFields.defaultValue.allowedRelationshipTypes" }),
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
        screen.getByRole("combobox", { name: "fields.templateFields.defaultValue.allowedRelationshipTypes" }),
      );
      const unchosen = await screen.findByRole("option", { name: "Cites" });
      expect(unchosen).not.toHaveAttribute("aria-disabled", "true");

      await user.click(unchosen);
      expect(field.allowedRelationTypes).toEqual(["IsCitedBy", "Cites"]);
    });
  });
});
