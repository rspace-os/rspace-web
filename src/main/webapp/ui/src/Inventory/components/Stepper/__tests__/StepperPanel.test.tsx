/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React, { useState } from "react";
import { render, cleanup, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import StepperPanel from "../StepperPanel";
import "../../../../../__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import SynchroniseFormSections from "../SynchroniseFormSections";
import FormSectionsContext from "../../../../stores/contexts/FormSections";
import userEvent from "@testing-library/user-event";

jest.mock("../../../../common/InvApiService", () => {});
jest.mock("../../../../stores/stores/RootStore", () => () => ({}));

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

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
        </ThemeProvider>
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
        </ThemeProvider>
      );
      expect(screen.getByTestId("content")).not.toBeVisible();
    });
  });

  describe("Expands and collapses properly", () => {
    test("Expand button works correctly", async () => {
      const user = userEvent.setup();
      const setExpanded = jest.fn();
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
              <></>
            </StepperPanel>
          </FormSectionsContext.Provider>
        </ThemeProvider>
      );

      await user.click(screen.getByLabelText("Expand section"));
      expect(setExpanded).toHaveBeenCalledWith("container", "bar", true);
    });
    test("Collapse button works correctly", async () => {
      const user = userEvent.setup();
      const setExpanded = jest.fn();
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
              <></>
            </StepperPanel>
          </FormSectionsContext.Provider>
        </ThemeProvider>
      );

      await user.click(screen.getByLabelText("Collapse section"));
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
              setExpanded: (recordType, sectionName, value) => {
                setOpen(value);
              },
              setAllExpanded,
            }}
          >
            <StepperPanel title="Bar" sectionName="bar" recordType="container">
              <></>
            </StepperPanel>
          </FormSectionsContext.Provider>
        </ThemeProvider>
      );
    }

    test("Collapse all", async () => {
      const user = userEvent.setup();
      const setAllExpanded = jest.fn();
      render(<TestComponent setAllExpanded={setAllExpanded} openInit={true} />);

      await user.click(screen.getByLabelText("Collapse section"));
      await user.click(screen.getByRole("button", { name: "Collapse All" }));
      expect(setAllExpanded).toHaveBeenCalledWith("container", false);
    });
    test("Expand all", async () => {
      const user = userEvent.setup();
      const setAllExpanded = jest.fn();
      render(
        <TestComponent setAllExpanded={setAllExpanded} openInit={false} />
      );

      await user.click(screen.getByLabelText("Expand section"));
      await user.click(screen.getByRole("button", { name: "Expand All" }));
      expect(setAllExpanded).toHaveBeenCalledWith("container", true);
    });
  });

  describe("Accessibility", () => {
    test("Has role region", () => {
      render(
        <ThemeProvider theme={materialTheme}>
          <SynchroniseFormSections>
            <StepperPanel title="Bar" sectionName="bar" recordType="container">
              <></>
            </StepperPanel>
          </SynchroniseFormSections>
        </ThemeProvider>
      );
      screen.getByRole("region", { name: "Bar" });
    });
  });
});
