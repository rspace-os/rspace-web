import { ThemeProvider } from "@mui/material/styles";
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@/__tests__/customQueries";
import { FieldTypes } from "@/stores/models/FieldTypes";
import materialTheme from "@/theme";
import FieldTypeMenuItem from "../FieldTypeMenuItem";

describe("FieldTypeMenuItem", () => {
  it("renders standalone (inMenu=false) without crashing", () => {
    // MUI 9 MenuItems throw "MenuListContext is missing" unless wrapped in a
    // Menu or MenuList; the closed-state trigger renders outside the Menu, so
    // it must provide its own MenuList (clicking "add new field" blanked the
    // whole template page via the error boundary)
    render(
      <ThemeProvider theme={materialTheme}>
        <FieldTypeMenuItem field={FieldTypes.plain_text} onClick={vi.fn()} />
      </ThemeProvider>,
    );
    expect(screen.getByText("Plain text")).toBeInTheDocument();
  });
});
