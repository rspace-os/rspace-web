/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import {
  render,
  cleanup,
  waitFor,
  fireEvent,
  screen,
  within,
} from "@testing-library/react";
import "@testing-library/jest-dom";
import Alias from "../Alias";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import { type Alias as AliasType } from "../../../../stores/definitions/Sample";
import { type HasEditableFields } from "../../../../stores/definitions/Editable";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("Alias", () => {
  test("Typing a custom alias should not select one of the radio buttons", async () => {
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
      </ThemeProvider>
    );

    fireEvent.click(screen.getByText("Custom"));

    const pluralInput = within(
      screen.getByTestId("aliasField_pluralBox")
    ).getByRole("textbox");
    fireEvent.input(pluralInput, { target: { value: "x" } });

    const singleInput = within(
      screen.getByTestId("aliasField_singleBox")
    ).getByRole("textbox");
    // entering "unit" as a custom alias...
    fireEvent.input(singleInput, { target: { value: "unit" } });

    await waitFor(() => {
      expect(template.fieldValues.subSampleAlias.alias).toEqual("unit");
    });
    expect(template.fieldValues.subSampleAlias.plural).toEqual("x");

    // ...should not select the unit radio button
    expect(
      screen.getAllByRole("radio").filter((radio) => radio.value === "unit")[0]
    ).not.toBeChecked();
  });
});
