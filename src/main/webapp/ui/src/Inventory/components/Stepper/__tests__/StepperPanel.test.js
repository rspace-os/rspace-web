/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React, { useState } from "react";
import { render, cleanup, screen, act } from "@testing-library/react";
import "@testing-library/jest-dom";
import StepperPanel from "../StepperPanel";
import "../../../../../__mocks__/matchMedia.js";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import SynchroniseFormSections from "../SynchroniseFormSections";
import FormSectionsContext, {
  type AllowedFormTypes,
} from "../../../../stores/contexts/FormSections";

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
    test("Expand button works correctly", () => {
      const setExpanded = jest.fn<[AllowedFormTypes, string, boolean], void>();
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

      act(() => {
        screen.getByLabelText("Expand section").click();
      });
      expect(setExpanded).toHaveBeenCalledWith("container", "bar", true);
    });
    test("Collapse button works correctly", () => {
      const setExpanded = jest.fn<[AllowedFormTypes, string, boolean], void>();
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

      act(() => {
        screen.getByLabelText("Collapse section").click();
      });
      expect(setExpanded).toHaveBeenCalledWith("container", "bar", false);
    });
  });

  describe("Expand/Collapse all appears after performing the operation once", () => {
    function TestComponent({
      setAllExpanded,
      openInit,
    }: {
      setAllExpanded: (string, boolean) => void,
      openInit: boolean,
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

    test("Collapse all", () => {
      const setAllExpanded = jest.fn<[string, boolean], void>();
      render(<TestComponent setAllExpanded={setAllExpanded} openInit={true} />);

      act(() => {
        screen.getByLabelText("Collapse section").click();
      });
      act(() => {
        screen.getByRole("button", { name: "Collapse All" }).click();
      });
      expect(setAllExpanded).toHaveBeenCalledWith("container", false);
    });
    test("Expand all", () => {
      const setAllExpanded = jest.fn<[string, boolean], void>();
      render(
        <TestComponent setAllExpanded={setAllExpanded} openInit={false} />
      );

      act(() => {
        screen.getByLabelText("Expand section").click();
      });
      act(() => {
        screen.getByRole("button", { name: "Expand All" }).click();
      });
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
