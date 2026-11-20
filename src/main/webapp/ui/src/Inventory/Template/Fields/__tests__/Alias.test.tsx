import { ThemeProvider } from "@mui/material/styles";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test } from "vitest";
import type { HasEditableFields } from "../../../../stores/definitions/Editable";
import type { Alias as AliasType } from "../../../../stores/definitions/Sample";
import materialTheme from "../../../../theme";
import Alias from "../Alias";

describe("Alias", () => {
  test("Typing a custom alias should not select one of the radio buttons", async () => {
    const user = userEvent.setup();
    const template: HasEditableFields<{ subSampleAlias: AliasType }> = {
      fieldValues: { subSampleAlias: { alias: "foo", plural: "foos" } },
      isFieldEditable: () => true,
      setFieldsDirty: ({ subSampleAlias }: { subSampleAlias: AliasType }) => {
        template.fieldValues.subSampleAlias = subSampleAlias;
      },
      canChooseWhichToEdit: false,
      setFieldEditable: () => {},
      noValueLabel: { subSampleAlias: "" },
    };
    render(
      <ThemeProvider theme={materialTheme}>
        <Alias fieldOwner={template} onErrorStateChange={() => {}} />
      </ThemeProvider>,
    );

    await user.click(screen.getByText("inventory:fields.templateFields.alias.custom"));
    const pluralInput = within(screen.getByTestId("aliasField_pluralBox")).getByRole("textbox");

    await user.clear(pluralInput);
    await user.type(pluralInput, "x");
    const singleInput = within(screen.getByTestId("aliasField_singleBox")).getByRole("textbox");
    // entering "unit" as a custom alias...

    await user.clear(singleInput);
    await user.type(singleInput, "unit");
    await waitFor(() => {
      expect(template.fieldValues.subSampleAlias.alias).toEqual("unit");
    });

    expect(template.fieldValues.subSampleAlias.plural).toEqual("x");
    // ...should not select the unit radio button
    expect(
      screen.getAllByRole("radio").filter((radio) => (radio as HTMLInputElement).value === "unit")[0],
    ).not.toBeChecked();
  });
});
