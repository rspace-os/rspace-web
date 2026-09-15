import { ThemeProvider } from "@mui/material/styles";
import { render, screen, within } from "@testing-library/react";
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
  describe("link fields' allowed relationship types explanation text", () => {
    it("shows the sample explanation by default", () => {
      const field = makeMockField({ type: "link", allowedRelationTypes: [] });

      render(
        <ThemeProvider theme={materialTheme}>
          <DefaultValueField field={field} editing />
        </ThemeProvider>,
      );

      expect(
        screen.getByRole("group", {
          name: "inventory:fields.templateFields.defaultValue.allowedRelationshipTypes",
        }),
      ).toHaveAccessibleDescription("inventory:fields.templateFields.defaultValue.allowedRelationshipTypesExplanation");
    });

    it("shows the instrument explanation when recordTypeName is instrument", () => {
      const field = makeMockField({ type: "link", allowedRelationTypes: [] });

      render(
        <ThemeProvider theme={materialTheme}>
          <DefaultValueField field={field} editing recordTypeName="instrument" />
        </ThemeProvider>,
      );

      expect(
        screen.getByRole("group", {
          name: "inventory:fields.templateFields.defaultValue.allowedRelationshipTypes",
        }),
      ).toHaveAccessibleDescription(
        "inventory:fields.templateFields.defaultValue.allowedRelationshipTypesExplanationInstrument",
      );
    });
  });

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

      await user.click(screen.getByRole("combobox", { name: "inventory:fields.extraFields.fields.relationType" }));
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

      expect(
        screen.queryByRole("combobox", { name: "inventory:fields.extraFields.fields.relationType" }),
      ).not.toBeInTheDocument();
    });
  });
});

describe("DefaultValueField default-link section", () => {
  function renderLinkField() {
    const field = makeMockField({ type: "link", allowedRelationTypes: ["IsCitedBy"] });
    return render(
      <ThemeProvider theme={materialTheme}>
        <DefaultValueField field={field} editing />
      </ThemeProvider>,
    );
  }

  function section(container: HTMLElement): HTMLElement {
    const found = container.querySelector<HTMLElement>('[data-test-id="DefaultLinkSection"]');
    if (!found) throw new Error('no element with data-test-id="DefaultLinkSection"');
    return found;
  }

  it("calls the target group 'Default link target' rather than the bare 'Target'", () => {
    renderLinkField();

    expect(
      screen.getByRole("heading", { name: "inventory:fields.templateFields.defaultValue.defaultLinkTarget" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "inventory:fields.link.editor.target" })).not.toBeInTheDocument();
  });

  it("puts that subheading after the Default link heading and its explanation", () => {
    renderLinkField();

    const defaultLink = screen.getByRole("heading", {
      name: "inventory:fields.templateFields.defaultValue.defaultLink",
    });
    const explanation = screen.getByText("inventory:fields.templateFields.defaultValue.defaultLinkExplanation");
    const target = screen.getByRole("heading", {
      name: "inventory:fields.templateFields.defaultValue.defaultLinkTarget",
    });

    expect(defaultLink.compareDocumentPosition(explanation) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(explanation.compareDocumentPosition(target) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("delineates the whole default-link section, which holds the heading and the editor", () => {
    const { container } = renderLinkField();

    const delineated = section(container);
    expect(
      within(delineated).getByRole("heading", {
        name: "inventory:fields.templateFields.defaultValue.defaultLink",
      }),
    ).toBeInTheDocument();
    expect(
      within(delineated).getByRole("heading", {
        name: "inventory:fields.templateFields.defaultValue.defaultLinkTarget",
      }),
    ).toBeInTheDocument();
    // the allowed-types control belongs to the section above, not inside the delineated one
    expect(
      within(delineated).queryByRole("combobox", {
        name: "inventory:fields.templateFields.defaultValue.allowedRelationshipTypes",
      }),
    ).not.toBeInTheDocument();
  });
});
