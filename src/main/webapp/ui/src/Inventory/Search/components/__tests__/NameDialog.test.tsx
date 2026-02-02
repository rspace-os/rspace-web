import { test, describe, expect, beforeEach, vi } from 'vitest';
import React from "react";
import {
  render,
  screen,
} from "@testing-library/react";
import NameDialog from "../NameDialog";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";




describe("NameDialog", () => {
  test("Naming a new saved search the same name as an existing saved search should be an error.", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <NameDialog
          open={true}
          setOpen={() => {}}
          name="foo"
          setName={() => {}}
          existingNames={["foo"]}
          onChange={() => {}}
        />
      </ThemeProvider>
    );

    expect(
      screen.getByText("This name is already taken. Please modify it.")
    ).toBeVisible();
  });
});


