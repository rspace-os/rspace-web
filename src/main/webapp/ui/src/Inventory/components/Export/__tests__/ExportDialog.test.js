/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */

import "@testing-library/jest-dom";
import React from "react";
import { render, screen } from "@testing-library/react";
import materialTheme from "../../../../theme";
import { ThemeProvider } from "@mui/material/styles";
import ExportDialog, { type ExportType } from "../ExportDialog";
import { type InventoryRecord } from "../../../../stores/definitions/InventoryRecord";
import { makeMockSample } from "../../../../stores/models/__tests__/SampleModel/mocking";
import { makeMockContainer } from "../../../../stores/models/__tests__/ContainerModel/mocking";
import { storesContext } from "../../../../stores/stores-context";
import { makeMockRootStore } from "../../../../stores/stores/__tests__/RootStore/mocking";

// break import cycles
jest.mock("../../../../common/InvApiService", () => {});
jest.mock("../../../../stores/stores/RootStore", () => () => ({}));

describe("Export Tests", () => {
  let openDialog = true; // if false, then dialog is null
  const setOpenDialog = (bool: boolean) => {
    openDialog = bool;
  };

  const mockSample = makeMockSample();
  const mockContainer = makeMockContainer();

  const Dialog = ({
    selectedResults = [],
    exportType,
  }: {
    selectedResults?: Array<InventoryRecord>,
    exportType: ExportType,
  }) => {
    return (
      <ThemeProvider theme={materialTheme}>
        <ExportDialog
          openExportDialog={openDialog}
          setOpenExportDialog={setOpenDialog}
          exportType={exportType}
          onExport={() => {}}
          selectedResults={selectedResults}
        />
      </ThemeProvider>
    );
  };

  describe("ExportDialog with no selected results (user data)", () => {
    it("renders, has radio options for exportMode and for containers (plus help text)", () => {
      render(
        <storesContext.Provider
          value={makeMockRootStore({
            uiStore: {
              isTouchDevice: false,
            },
          })}
        >
          <Dialog exportType="userData" selectedResults={[]} />
        </storesContext.Provider>
      );

      expect(screen.getAllByRole("radio")).toHaveLength(6); // containers options rendered when no selected results

      const fullOption = screen.getByLabelText("Full");
      expect(fullOption).toBeInTheDocument();
      expect(fullOption).toBeChecked();
      const compactOption = screen.getByLabelText("Compact");
      expect(compactOption).toBeInTheDocument();
      expect(compactOption).not.toBeChecked();
      const includeContentOption = screen.getByLabelText("Include Content");
      expect(includeContentOption).toBeInTheDocument();
      expect(includeContentOption).not.toBeChecked();

      /* assert help text for default option */
      const defaultContainersHint = "Containers only, without their content.";
      expect(screen.getByText(defaultContainersHint)).toBeInTheDocument();
    });
  });

  describe("ExportDialog with selected results", () => {
    it("renders, has radio options (and help text) for samples", () => {
      render(
        <storesContext.Provider
          value={makeMockRootStore({
            uiStore: {
              isTouchDevice: false,
            },
          })}
        >
          <Dialog exportType="contextMenu" selectedResults={[mockSample]} />
        </storesContext.Provider>
      );

      expect(screen.getAllByRole("radio")).toHaveLength(6);
      const includeOption = screen.getByLabelText("Include Subsamples");
      expect(includeOption).toBeInTheDocument();
      expect(includeOption).toBeChecked();
      const excludeOption = screen.getByLabelText("Exclude Subsamples");
      expect(excludeOption).toBeInTheDocument();
      expect(excludeOption).not.toBeChecked();

      /* assert help text for default option */
      const defaultSamplesHint =
        "All data, including custom and template fields.";
      expect(screen.getByText(defaultSamplesHint)).toBeInTheDocument();
    });

    it("renders, has radio options for exportMode, samples and containers", () => {
      render(
        <storesContext.Provider
          value={makeMockRootStore({
            uiStore: {
              isTouchDevice: false,
            },
          })}
        >
          <Dialog
            exportType="contextMenu"
            selectedResults={[mockSample, mockContainer]}
          />
        </storesContext.Provider>
      );
      expect(screen.getAllByRole("radio")).toHaveLength(8);
    });
  });
});
