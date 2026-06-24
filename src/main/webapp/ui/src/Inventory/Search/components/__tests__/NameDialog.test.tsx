import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import materialTheme from "../../../../theme";
import NameDialog from "../NameDialog";

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
      </ThemeProvider>,
    );
    expect(screen.getByText("search.controls.nameDialog.duplicateName")).toBeVisible();
  });
});
