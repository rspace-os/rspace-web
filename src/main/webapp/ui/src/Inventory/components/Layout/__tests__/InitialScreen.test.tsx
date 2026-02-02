import { test, describe, expect, vi } from 'vitest';
import React from "react";
import {
  render,
  screen,
} from "@testing-library/react";
import InitialScreen from "../InitialScreen";
import Header from "../Header";
import Sidebar from "../Sidebar";
import { BrowserRouter } from "react-router-dom";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
vi.mock("../Header", () => ({
  default: vi.fn(() => <></>),
}));
vi.mock("../Sidebar", () => ({
  default: vi.fn(() => <></>),
}));
describe("InitialScreen", () => {
  test("Renders correctly", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <BrowserRouter>
          <InitialScreen />
        </BrowserRouter>
      </ThemeProvider>
    );
    expect(Header).toHaveBeenCalled();
    expect(Sidebar).toHaveBeenCalled();
    expect(screen.getByText("My Bench")).toBeInTheDocument();
    expect(screen.getByText("Containers")).toBeInTheDocument();
    expect(screen.getByText("Samples")).toBeInTheDocument();
  });
});
