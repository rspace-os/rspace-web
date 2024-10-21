/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen } from "@testing-library/react";
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
import { makeMockTemplate } from "../../../../stores/models/__tests__/TemplateModel/mocking";
import userEvent from "@testing-library/user-event";

jest.mock("../../../../stores/stores/RootStore", () => () => ({
  unitStore: {
    getUnit: () => ({ label: "ml" }),
  },
  searchStore: {
    search: null,
    createNewContainer: () => {},
    createNewSample: () => {},
  },
}));

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("CreateDialog", () => {
  describe("Splitting", () => {
    test("Subsamples", async () => {
      const user = userEvent.setup();
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

      await user.click(
        screen.getByRole("radio", { name: /Subsample, by splitting/ })
      );

      expect(
        screen.getByRole("spinbutton", { name: /Number of new subsamples/i })
      ).toBeVisible();
      expect(screen.getByRole("button", { name: /create/i })).toBeVisible();
    });
    test("Subsamples, with too many copies", async () => {
      const user = userEvent.setup();
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

      await user.click(
        screen.getByRole("radio", { name: /Subsample, by splitting/ })
      );

      await user.type(
        screen.getByRole("spinbutton", { name: /Number of new subsamples/i }),
        "200"
      );

      expect(screen.getByRole("button", { name: /create/i })).toBeDisabled();
    });
    test("Samples, when there is one subsample", async () => {
      const user = userEvent.setup();
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
          name: /Subsamples, by splitting the existing subsample/,
        })
      ).toBeEnabled();

      await user.click(
        screen.getByRole("radio", {
          name: /Subsamples, by splitting the existing subsample/,
        })
      );

      expect(
        screen.getByRole("spinbutton", { name: /Number of new subsamples/i })
      ).toBeVisible();
      expect(screen.getByRole("button", { name: /create/i })).toBeVisible();
    });
    test("Samples, with too many copies", async () => {
      const user = userEvent.setup();
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

      await user.click(
        screen.getByRole("radio", {
          name: /Subsamples, by splitting the existing subsample/,
        })
      );

      await user.type(
        screen.getByRole("spinbutton", { name: /Number of new subsamples/i }),
        "200"
      );

      expect(screen.getByRole("button", { name: /create/i })).toBeDisabled();
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
          name: /Subsamples, by splitting the existing subsample/,
        })
      ).toBeDisabled();
    });
  });
  describe("New container in container", () => {
    test("Success case for list container", async () => {
      const user = userEvent.setup();
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

      await user.click(
        screen.getByRole("radio", {
          name: /Container/,
        })
      );

      expect(
        screen.getByText("No location selection required for list containers.")
      ).toBeVisible();
      expect(screen.getByRole("button", { name: /create/i })).toBeEnabled();

      await user.click(screen.getByRole("button", { name: /create/i }));
    });
    /*
     * Writing a test for picking locations in grid and visual containers is
     * near enough impossible due to the need for extensive mocking and
     * accessibility issues
     */
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
    test("Success case for list containers", async () => {
      const user = userEvent.setup();
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

      await user.click(
        screen.getByRole("radio", {
          name: /Sample/,
        })
      );

      expect(
        screen.getByText("No location selection required for list containers.")
      ).toBeVisible();
      expect(screen.getByRole("button", { name: /create/i })).toBeEnabled();
      await user.click(screen.getByRole("button", { name: /create/i }));
    });
    /*
     * Writing a test for picking locations in grid and visual containers is
     * near enough impossible due to the need for extensive mocking and
     * accessibility issues
     */
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
  describe("New sample from template", () => {
    test("Success case", async () => {
      const user = userEvent.setup();
      const template = makeMockTemplate({});
      render(
        <ThemeProvider theme={materialTheme}>
          <CreateDialog
            existingRecord={template}
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

      await user.click(
        screen.getByRole("radio", {
          name: /Sample/,
        })
      );
    });
  });
  describe("New template from sample", () => {
    test("No fields", async () => {
      const user = userEvent.setup();
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
          name: /Template/,
        })
      ).toBeEnabled();

      await user.click(
        screen.getByRole("radio", {
          name: /Template/,
        })
      );

      await user.type(
        screen.getByRole("textbox", { name: /name/i }),
        "New template"
      );
      await user.click(screen.getByRole("button", { name: /next/i }));

      expect(screen.getByText("No fields.")).toBeVisible();
      expect(screen.getByRole("button", { name: /create/i })).toBeEnabled();
    });
    test("Name that's too short", async () => {
      const user = userEvent.setup();
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
          name: /Template/,
        })
      ).toBeEnabled();

      await user.click(
        screen.getByRole("radio", {
          name: /Template/,
        })
      );

      await user.type(screen.getByRole("textbox", { name: /name/i }), "x");

      expect(screen.getByRole("button", { name: /next/i })).toBeDisabled();
    });
  });
  describe("New subsamples without splitting", () => {
    test("Success case", async () => {
      const user = userEvent.setup();
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
          name: /Subsamples, by creating new ones/,
        })
      ).toBeEnabled();

      await user.click(
        screen.getByRole("radio", {
          name: /Subsamples, by creating new ones/,
        })
      );

      expect(
        screen.getByRole("spinbutton", { name: /Number of new subsamples/i })
      ).toBeVisible();
      await user.type(
        screen.getByRole("spinbutton", { name: /Number of new subsamples/i }),
        "4"
      );

      expect(screen.getByRole("button", { name: /create/i })).toBeVisible();
      expect(screen.getByRole("button", { name: /create/i })).toBeDisabled();

      expect(screen.getByRole("button", { name: /next/i })).toBeVisible();
      await user.click(screen.getByRole("button", { name: /next/i }));

      expect(
        screen.getByRole("spinbutton", { name: /Quantity per subsample/i })
      ).toBeVisible();

      expect(screen.getByRole("button", { name: /create/i })).toBeEnabled();
      await user.type(
        screen.getByRole("spinbutton", { name: /Quantity per subsample/i }),
        "4"
      );
    });
  });
});
