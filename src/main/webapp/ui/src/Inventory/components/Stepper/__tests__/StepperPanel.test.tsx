import { render, screen } from "@testing-library/react";
import { useState } from "react";
import { describe, expect, test, vi } from "vitest";
import StepperPanel from "../StepperPanel";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import userEvent from "@testing-library/user-event";
import FormSectionsContext from "../../../../stores/contexts/FormSections";
import materialTheme from "../../../../theme";
import SynchroniseFormSections from "../SynchroniseFormSections";

vi.mock("../../../../common/InvApiService", () => ({ default: {} }));
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({}),
}));
describe("StepperPanel", () => {
  describe("Renders correctly", () => {
    test("When expanded", () => {
      render(
        <ThemeProvider theme={materialTheme}>
          <FormSectionsContext.Provider
            value={{
              isExpanded: () => true,
              setExpanded: () => {},
              setAllExpanded: () => {},
            }}
          >
            <StepperPanel title="Bar" sectionName="bar" recordType="container">
              <div data-testid="content"></div>
            </StepperPanel>
          </FormSectionsContext.Provider>
        </ThemeProvider>,
      );
      expect(screen.getByTestId("content")).toBeVisible();
    });
    test("When not expanded", () => {
      render(
        <ThemeProvider theme={materialTheme}>
          <FormSectionsContext.Provider
            value={{
              isExpanded: () => false,
              setExpanded: () => {},
              setAllExpanded: () => {},
            }}
          >
            <StepperPanel title="Bar" sectionName="bar" recordType="container">
              <div data-testid="content"></div>
            </StepperPanel>
          </FormSectionsContext.Provider>
        </ThemeProvider>,
      );
      expect(screen.getByTestId("content")).not.toBeVisible();
    });
  });
  describe("Expands and collapses properly", () => {
    test("Expand button works correctly", async () => {
      const user = userEvent.setup();
      const setExpanded = vi.fn();
      render(
        <ThemeProvider theme={materialTheme}>
          <FormSectionsContext.Provider
            value={{
              isExpanded: () => false,
              setExpanded,
              setAllExpanded: () => {},
            }}
          >
            <StepperPanel title="Bar" sectionName="bar" recordType="container">
              <div />
            </StepperPanel>
          </FormSectionsContext.Provider>
        </ThemeProvider>,
      );
      await user.click(screen.getByLabelText("inventory:formSections.expandSection"));
      expect(setExpanded).toHaveBeenCalledWith("container", "bar", true);
    });
    test("Collapse button works correctly", async () => {
      const user = userEvent.setup();
      const setExpanded = vi.fn();
      render(
        <ThemeProvider theme={materialTheme}>
          <FormSectionsContext.Provider
            value={{
              isExpanded: () => true,
              setExpanded,
              setAllExpanded: () => {},
            }}
          >
            <StepperPanel title="Bar" sectionName="bar" recordType="container">
              <div />
            </StepperPanel>
          </FormSectionsContext.Provider>
        </ThemeProvider>,
      );
      await user.click(screen.getByLabelText("inventory:formSections.collapseSection"));
      expect(setExpanded).toHaveBeenCalledWith("container", "bar", false);
    });
  });
  describe("Expand/Collapse all appears after performing the operation once", () => {
    function TestComponent({
      setAllExpanded,
      openInit,
    }: {
      setAllExpanded: (recordType: string, newValue: boolean) => void;
      openInit: boolean;
    }) {
      const [open, setOpen] = useState(openInit);
      return (
        <ThemeProvider theme={materialTheme}>
          <FormSectionsContext.Provider
            value={{
              isExpanded: () => open,
              setExpanded: (_recordType, _sectionName, value) => {
                setOpen(value);
              },
              setAllExpanded,
            }}
          >
            <StepperPanel title="Bar" sectionName="bar" recordType="container">
              <div />
            </StepperPanel>
          </FormSectionsContext.Provider>
        </ThemeProvider>
      );
    }
    test("Collapse all", async () => {
      const user = userEvent.setup();
      const setAllExpanded = vi.fn();

      render(<TestComponent setAllExpanded={setAllExpanded} openInit={true} />);
      await user.click(screen.getByLabelText("inventory:formSections.collapseSection"));
      await user.click(screen.getByRole("button", { name: "inventory:formSections.collapseAll" }));
      expect(setAllExpanded).toHaveBeenCalledWith("container", false);
    });
    test("Expand all", async () => {
      const user = userEvent.setup();
      const setAllExpanded = vi.fn();
      render(<TestComponent setAllExpanded={setAllExpanded} openInit={false} />);
      await user.click(screen.getByLabelText("inventory:formSections.expandSection"));
      await user.click(screen.getByRole("button", { name: "inventory:formSections.expandAll" }));
      expect(setAllExpanded).toHaveBeenCalledWith("container", true);
    });
  });
  describe("Accessibility", () => {
    test("Has role region", () => {
      render(
        <ThemeProvider theme={materialTheme}>
          <SynchroniseFormSections>
            <StepperPanel title="Bar" sectionName="bar" recordType="container">
              <div />
            </StepperPanel>
          </SynchroniseFormSections>
        </ThemeProvider>,
      );
      screen.getByRole("region", { name: "Bar" });
    });
  });
});
