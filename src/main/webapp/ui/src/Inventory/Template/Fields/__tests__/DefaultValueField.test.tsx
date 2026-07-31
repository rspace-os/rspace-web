import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { makeMockField } from "../../../../stores/models/__tests__/FieldModel/mocking";
import materialTheme from "../../../../theme";
import DefaultValueField from "../DefaultValueField";

// the default-link editor resolves its target summary through InvApiService
vi.mock("../../../../common/InvApiService", () => ({
  default: { get: vi.fn(() => new Promise(() => {})) },
}));

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
        screen.getByRole("combobox", { name: "inventory:fields.templateFields.defaultValue.allowedRelationshipTypes" }),
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
        screen.getByRole("combobox", { name: "inventory:fields.templateFields.defaultValue.allowedRelationshipTypes" }),
      );
      const unchosen = await screen.findByRole("option", { name: "Cites" });
      expect(unchosen).not.toHaveAttribute("aria-disabled", "true");

      await user.click(unchosen);
      expect(field.allowedRelationTypes).toEqual(["IsCitedBy", "Cites"]);
    });
  });

  describe("link fields' default link", () => {
    it("offers a default link editor whose relation options are constrained to the whitelist", async () => {
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

      await user.click(screen.getByRole("combobox", { name: "Relationship type" }));
      expect(await screen.findByRole("option", { name: "IsCitedBy" })).toBeInTheDocument();
      expect(screen.queryByRole("option", { name: "Cites" })).not.toBeInTheDocument();
    });

    it("shows the committed default link for an existing template field", () => {
      const field = makeMockField({
        type: "link",
        allowedRelationTypes: ["IsCitedBy"],
        link: { relationType: "IsCitedBy", targetGlobalId: "SA2", versionPin: null },
      });

      render(
        <ThemeProvider theme={materialTheme}>
          <DefaultValueField field={field} editing />
        </ThemeProvider>,
      );

      expect(screen.getByText("SA2")).toBeInTheDocument();
    });

    it("does not repeat the field name under the Default link heading", () => {
      // the name is already entered in the Name field above; repeating it here is noise
      const field = makeMockField({
        type: "link",
        name: "Related items",
        allowedRelationTypes: ["IsCitedBy"],
      });

      render(
        <ThemeProvider theme={materialTheme}>
          <DefaultValueField field={field} editing />
        </ThemeProvider>,
      );

      expect(screen.queryByText("Related items")).not.toBeInTheDocument();
    });

    it("does not repeat the field name inside a committed default link card", () => {
      const field = makeMockField({
        type: "link",
        name: "Related items",
        allowedRelationTypes: ["IsCitedBy"],
        link: { relationType: "IsCitedBy", targetGlobalId: "SA2", versionPin: null },
      });

      render(
        <ThemeProvider theme={materialTheme}>
          <DefaultValueField field={field} editing />
        </ThemeProvider>,
      );

      expect(screen.getByText("SA2")).toBeInTheDocument();
      expect(screen.queryByText("Related items")).not.toBeInTheDocument();
    });

    it("renders no default link editor for a non-link field", () => {
      const field = makeMockField({ type: "string", content: "hello" });

      render(
        <ThemeProvider theme={materialTheme}>
          <DefaultValueField field={field} editing />
        </ThemeProvider>,
      );

      expect(screen.queryByRole("combobox", { name: "Relationship type" })).not.toBeInTheDocument();
    });
  });
});
