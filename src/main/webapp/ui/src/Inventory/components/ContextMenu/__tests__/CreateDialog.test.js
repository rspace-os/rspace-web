/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen, fireEvent } from "@testing-library/react";
import "@testing-library/jest-dom";
import CreateDialog from "../CreateDialog";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import {
  makeMockSubSample,
  subsampleAttrs,
} from "../../../../stores/models/__tests__/SubSampleModel/mocking";
import { makeMockSample } from "../../../../stores/models/__tests__/SampleModel/mocking";
import { makeMockContainer } from "../../../../stores/models/__tests__/ContainerModel/mocking";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("CreateDialog", () => {
  describe("Splitting", () => {
    test("Subsamples", () => {
      const subsample = makeMockSubSample({});
      render(
        <ThemeProvider theme={materialTheme}>
          <CreateDialog
            existingRecord={subsample}
            open={true}
            onClose={() => {}}
          />
        </ThemeProvider>
      );

      fireEvent.click(
        screen.getByRole("radio", { name: /Subsample, by splitting/ })
      );

      expect(
        screen.getByRole("spinbutton", { name: /Number of new subsamples/i })
      ).toBeVisible();
      expect(screen.getByRole("button", { name: /create/i })).toBeVisible();
    });
    test("Samples, when there is one subsample", () => {
      const sample = makeMockSample({});
      render(
        <ThemeProvider theme={materialTheme}>
          <CreateDialog
            existingRecord={sample}
            open={true}
            onClose={() => {}}
          />
        </ThemeProvider>
      );

      expect(
        screen.getByRole("radio", {
          name: /Subsample, by splitting the current subsample/,
        })
      ).toBeEnabled();

      fireEvent.click(
        screen.getByRole("radio", {
          name: /Subsample, by splitting the current subsample/,
        })
      );

      expect(
        screen.getByRole("spinbutton", { name: /Number of new subsamples/i })
      ).toBeVisible();
      expect(screen.getByRole("button", { name: /create/i })).toBeVisible();
    });
    test("Samples, when there are multiple subsamples", () => {
      const sample = makeMockSample({
        subSamples: [subsampleAttrs(), subsampleAttrs()],
      });
      render(
        <ThemeProvider theme={materialTheme}>
          <CreateDialog
            existingRecord={sample}
            open={true}
            onClose={() => {}}
          />
        </ThemeProvider>
      );

      expect(
        screen.getByRole("radio", {
          name: /Subsample, by splitting the current subsample/,
        })
      ).toBeDisabled();
    });
  });
  describe("New container in container", () => {
    test("Success case", () => {
      const container = makeMockContainer({
        canStoreContainers: true,
        canStoreSamples: true,
      });
      render(
        <ThemeProvider theme={materialTheme}>
          <CreateDialog
            existingRecord={container}
            open={true}
            onClose={() => {}}
          />
        </ThemeProvider>
      );

      expect(
        screen.getByRole("radio", {
          name: /Container/,
        })
      ).toBeEnabled();

      fireEvent.click(
        screen.getByRole("radio", {
          name: /Container/,
        })
      );
    });
    test("Cannot store containers", () => {
      const container = makeMockContainer({
        canStoreContainers: false,
        canStoreSamples: true,
      });
      render(
        <ThemeProvider theme={materialTheme}>
          <CreateDialog
            existingRecord={container}
            open={true}
            onClose={() => {}}
          />
        </ThemeProvider>
      );

      expect(
        screen.getByRole("radio", {
          name: /Container/,
        })
      ).toBeDisabled();
    });
  });
  describe("New sample in container", () => {
    test("Success case", () => {
      const container = makeMockContainer({
        canStoreContainers: true,
        canStoreSamples: true,
      });
      render(
        <ThemeProvider theme={materialTheme}>
          <CreateDialog
            existingRecord={container}
            open={true}
            onClose={() => {}}
          />
        </ThemeProvider>
      );

      expect(
        screen.getByRole("radio", {
          name: /Sample/,
        })
      ).toBeEnabled();

      fireEvent.click(
        screen.getByRole("radio", {
          name: /Sample/,
        })
      );
    });
    test("Cannot store samples", () => {
      const container = makeMockContainer({
        canStoreContainers: true,
        canStoreSamples: false,
      });
      render(
        <ThemeProvider theme={materialTheme}>
          <CreateDialog
            existingRecord={container}
            open={true}
            onClose={() => {}}
          />
        </ThemeProvider>
      );

      expect(
        screen.getByRole("radio", {
          name: /Sample/,
        })
      ).toBeDisabled();
    });
  });
});
