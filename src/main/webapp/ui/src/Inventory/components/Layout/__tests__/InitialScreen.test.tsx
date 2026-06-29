import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import { BrowserRouter } from "react-router";
import { describe, expect, test, vi } from "vitest";
import materialTheme from "../../../../theme";
import Header from "../Header";
import InitialScreen from "../InitialScreen";
import Sidebar from "../Sidebar";

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
      </ThemeProvider>,
    );
    expect(Header).toHaveBeenCalled();
    expect(Sidebar).toHaveBeenCalled();
    expect(screen.getByText("inventory:layout.sidebar.myBench")).toBeInTheDocument();
    expect(screen.getByText("inventory:layout.sidebar.containers")).toBeInTheDocument();
    expect(screen.getByText("inventory:layout.sidebar.samples")).toBeInTheDocument();
  });
});
