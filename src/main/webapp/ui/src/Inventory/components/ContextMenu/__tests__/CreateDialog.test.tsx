import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import AlertContext from "../../../../stores/contexts/Alert";
import { makeMockContainer } from "../../../../stores/models/__tests__/ContainerModel/mocking";
import { makeMockSample } from "../../../../stores/models/__tests__/SampleModel/mocking";
import { makeMockSubSample, subsampleAttrs } from "../../../../stores/models/__tests__/SubSampleModel/mocking";
import { makeMockTemplate } from "../../../../stores/models/__tests__/TemplateModel/mocking";
import materialTheme from "../../../../theme";
import CreateDialog from "../CreateDialog";

vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    unitStore: {
      getUnit: () => ({ label: "ml" }),
    },
    searchStore: {
      search: null,
      createNewContainer: () => {},
      createNewSample: () => {},
    },
    uiStore: {
      addAlert: () => {},
    },
    authStore: {
      isSynchronizing: false,
    },
  }),
}));
// Mock AlertContext

const mockAddAlert = vi.fn();
const createButton = () => screen.getByRole("button", { name: "common:actions.create" });
describe("CreateDialog", () => {
  describe("Splitting", () => {
    test("Subsamples", async () => {
      const user = userEvent.setup();
      const subsample = makeMockSubSample({});
      vi.spyOn(subsample, "fetchAdditionalInfo").mockImplementation(() => Promise.resolve());
      render(
        <ThemeProvider theme={materialTheme}>
          <CreateDialog existingRecord={subsample} open={true} onClose={() => {}} />
        </ThemeProvider>,
      );
      await user.click(await screen.findByRole("radio", { name: /inventory:subsample\.createOptions\.split\.label/ }));
      expect(
        screen.getByRole("spinbutton", { name: /createOptions\.(split|newSubsamples)\.countLabel/ }),
      ).toBeVisible();
      expect(createButton()).toBeVisible();
    });
    test("Subsamples, with too many copies", async () => {
      const user = userEvent.setup();
      const subsample = makeMockSubSample({});
      vi.spyOn(subsample, "fetchAdditionalInfo").mockImplementation(() => Promise.resolve());
      render(
        <ThemeProvider theme={materialTheme}>
          <AlertContext.Provider value={{ addAlert: mockAddAlert, removeAlert: vi.fn() }}>
            <CreateDialog existingRecord={subsample} open={true} onClose={() => {}} />
          </AlertContext.Provider>
        </ThemeProvider>,
      );
      await user.click(await screen.findByRole("radio", { name: /inventory:subsample\.createOptions\.split\.label/ }));
      await user.type(
        screen.getByRole("spinbutton", { name: /createOptions\.(split|newSubsamples)\.countLabel/ }),
        "200",
      );
      expect(createButton()).toBeDisabled();
    });
    test("Samples, when there is one subsample", async () => {
      const user = userEvent.setup();
      const sample = makeMockSample({});
      vi.spyOn(sample, "fetchAdditionalInfo").mockImplementation(() => Promise.resolve());
      render(
        <ThemeProvider theme={materialTheme}>
          <CreateDialog existingRecord={sample} open={true} onClose={() => {}} />
        </ThemeProvider>,
      );
      expect(
        await screen.findByRole("radio", {
          name: /inventory:sample\.createOptions\.split\.label/,
        }),
      ).toBeEnabled();
      await user.click(
        screen.getByRole("radio", {
          name: /inventory:sample\.createOptions\.split\.label/,
        }),
      );
      expect(
        screen.getByRole("spinbutton", { name: /createOptions\.(split|newSubsamples)\.countLabel/ }),
      ).toBeVisible();
      expect(createButton()).toBeVisible();
    });
    test("Samples, with too many copies", async () => {
      const user = userEvent.setup();
      const sample = makeMockSample({});
      vi.spyOn(sample, "fetchAdditionalInfo").mockImplementation(() => Promise.resolve());
      render(
        <ThemeProvider theme={materialTheme}>
          <AlertContext.Provider value={{ addAlert: mockAddAlert, removeAlert: vi.fn() }}>
            <CreateDialog existingRecord={sample} open={true} onClose={() => {}} />
          </AlertContext.Provider>
        </ThemeProvider>,
      );
      await user.click(
        await screen.findByRole("radio", {
          name: /inventory:sample\.createOptions\.split\.label/,
        }),
      );
      await user.type(
        screen.getByRole("spinbutton", { name: /createOptions\.(split|newSubsamples)\.countLabel/ }),
        "200",
      );
      expect(createButton()).toBeDisabled();
    });
    test("Samples, when there are multiple subsamples", async () => {
      const sample = makeMockSample({
        subSamples: [subsampleAttrs(), subsampleAttrs()],
      });
      vi.spyOn(sample, "fetchAdditionalInfo").mockImplementation(() => Promise.resolve());
      render(
        <ThemeProvider theme={materialTheme}>
          <AlertContext.Provider value={{ addAlert: mockAddAlert, removeAlert: vi.fn() }}>
            <CreateDialog existingRecord={sample} open={true} onClose={() => {}} />
          </AlertContext.Provider>
        </ThemeProvider>,
      );
      expect(
        await screen.findByRole("radio", {
          name: /inventory:sample\.createOptions\.split\.label/,
        }),
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
      vi.spyOn(container, "fetchAdditionalInfo").mockImplementation(() => Promise.resolve());
      render(
        <ThemeProvider theme={materialTheme}>
          <AlertContext.Provider value={{ addAlert: mockAddAlert, removeAlert: vi.fn() }}>
            <CreateDialog existingRecord={container} open={true} onClose={() => {}} />
          </AlertContext.Provider>
        </ThemeProvider>,
      );
      expect(
        await screen.findByRole("radio", {
          name: /Container/,
        }),
      ).toBeEnabled();
      await user.click(
        screen.getByRole("radio", {
          name: /Container/,
        }),
      );
      expect(screen.getByText("inventory:container.createOptions.location.listExplanation")).toBeVisible();

      expect(createButton()).toBeEnabled();
      await user.click(createButton());
    });
    /*
     * Writing a test for picking locations in grid and visual containers is
     * near enough impossible due to the need for extensive mocking and
     * accessibility issues
     */
    test("Cannot store containers", async () => {
      const container = makeMockContainer({
        canStoreContainers: false,
        canStoreSamples: true,
      });
      vi.spyOn(container, "fetchAdditionalInfo").mockImplementation(() => Promise.resolve());
      render(
        <ThemeProvider theme={materialTheme}>
          <AlertContext.Provider value={{ addAlert: mockAddAlert, removeAlert: vi.fn() }}>
            <CreateDialog existingRecord={container} open={true} onClose={() => {}} />
          </AlertContext.Provider>
        </ThemeProvider>,
      );
      expect(
        await screen.findByRole("radio", {
          name: /Container/,
        }),
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
      vi.spyOn(container, "fetchAdditionalInfo").mockImplementation(() => Promise.resolve());
      render(
        <ThemeProvider theme={materialTheme}>
          <AlertContext.Provider value={{ addAlert: vi.fn(), removeAlert: vi.fn() }}>
            <CreateDialog existingRecord={container} open={true} onClose={() => {}} />
          </AlertContext.Provider>
        </ThemeProvider>,
      );
      expect(
        await screen.findByRole("radio", {
          name: /inventory:container\.createOptions\.newSample\.label/,
        }),
      ).toBeEnabled();
      await user.click(
        screen.getByRole("radio", {
          name: /inventory:container\.createOptions\.newSample\.label/,
        }),
      );
      expect(screen.getByText("inventory:container.createOptions.location.listExplanation")).toBeVisible();
      expect(createButton()).toBeEnabled();
      await user.click(createButton());
    });
    /*
     * Writing a test for picking locations in grid and visual containers is
     * near enough impossible due to the need for extensive mocking and
     * accessibility issues
     */
    test("Cannot store samples", async () => {
      const container = makeMockContainer({
        canStoreContainers: true,
        canStoreSamples: false,
      });
      vi.spyOn(container, "fetchAdditionalInfo").mockImplementation(() => Promise.resolve());
      render(
        <ThemeProvider theme={materialTheme}>
          <AlertContext.Provider value={{ addAlert: mockAddAlert, removeAlert: vi.fn() }}>
            <CreateDialog existingRecord={container} open={true} onClose={() => {}} />
          </AlertContext.Provider>
        </ThemeProvider>,
      );
      expect(
        await screen.findByRole("radio", {
          name: /inventory:container\.createOptions\.newSample\.label/,
        }),
      ).toBeDisabled();
    });
  });
  describe("New sample from template", () => {
    test("Success case", async () => {
      const user = userEvent.setup();
      const template = makeMockTemplate({});
      vi.spyOn(template, "fetchAdditionalInfo").mockImplementation(() => Promise.resolve());
      render(
        <ThemeProvider theme={materialTheme}>
          <AlertContext.Provider value={{ addAlert: mockAddAlert, removeAlert: vi.fn() }}>
            <CreateDialog existingRecord={template} open={true} onClose={() => {}} />
          </AlertContext.Provider>
        </ThemeProvider>,
      );
      expect(
        await screen.findByRole("radio", {
          name: /inventory:template\.createOptions\.sample\.label/,
        }),
      ).toBeEnabled();
      await user.click(
        screen.getByRole("radio", {
          name: /inventory:template\.createOptions\.sample\.label/,
        }),
      );
    });
  });
  describe("New template from sample", () => {
    test("No fields", async () => {
      const user = userEvent.setup();
      const sample = makeMockSample({});
      vi.spyOn(sample, "fetchAdditionalInfo").mockImplementation(() => Promise.resolve());
      render(
        <ThemeProvider theme={materialTheme}>
          <AlertContext.Provider value={{ addAlert: mockAddAlert, removeAlert: vi.fn() }}>
            <CreateDialog existingRecord={sample} open={true} onClose={() => {}} />
          </AlertContext.Provider>
        </ThemeProvider>,
      );
      expect(
        await screen.findByRole("radio", {
          name: /inventory:sample\.createOptions\.template\.label/,
        }),
      ).toBeEnabled();
      await user.click(
        screen.getByRole("radio", {
          name: /inventory:sample\.createOptions\.template\.label/,
        }),
      );
      await user.type(screen.getByRole("textbox", { name: /name/i }), "New template");

      await user.click(screen.getByRole("button", { name: /next/i }));
      expect(screen.getByText("inventory:contextMenu.createDialog.noFields")).toBeVisible();
      expect(createButton()).toBeEnabled();
    });
    test("Name that's too short", async () => {
      const user = userEvent.setup();
      const sample = makeMockSample({});
      vi.spyOn(sample, "fetchAdditionalInfo").mockImplementation(() => Promise.resolve());
      render(
        <ThemeProvider theme={materialTheme}>
          <AlertContext.Provider value={{ addAlert: mockAddAlert, removeAlert: vi.fn() }}>
            <CreateDialog existingRecord={sample} open={true} onClose={() => {}} />
          </AlertContext.Provider>
        </ThemeProvider>,
      );
      expect(
        await screen.findByRole("radio", {
          name: /inventory:sample\.createOptions\.template\.label/,
        }),
      ).toBeEnabled();
      await user.click(
        screen.getByRole("radio", {
          name: /inventory:sample\.createOptions\.template\.label/,
        }),
      );

      await user.type(screen.getByRole("textbox", { name: /name/i }), "x");
      expect(screen.getByRole("button", { name: /next/i })).toBeDisabled();
    });
  });
  describe("New subsamples without splitting", () => {
    test("Success case", async () => {
      const user = userEvent.setup();
      const sample = makeMockSample({});
      vi.spyOn(sample, "fetchAdditionalInfo").mockImplementation(() => Promise.resolve());
      render(
        <ThemeProvider theme={materialTheme}>
          <AlertContext.Provider value={{ addAlert: mockAddAlert, removeAlert: vi.fn() }}>
            <CreateDialog existingRecord={sample} open={true} onClose={() => {}} />
          </AlertContext.Provider>
        </ThemeProvider>,
      );
      expect(
        await screen.findByRole("radio", {
          name: /inventory:sample\.createOptions\.newSubsamples\.label/,
        }),
      ).toBeEnabled();
      await user.click(
        screen.getByRole("radio", {
          name: /inventory:sample\.createOptions\.newSubsamples\.label/,
        }),
      );
      expect(
        screen.getByRole("spinbutton", { name: /createOptions\.(split|newSubsamples)\.countLabel/ }),
      ).toBeVisible();
      await user.type(
        screen.getByRole("spinbutton", { name: /createOptions\.(split|newSubsamples)\.countLabel/ }),
        "4",
      );
      expect(createButton()).toBeVisible();

      expect(createButton()).toBeDisabled();
      expect(screen.getByRole("button", { name: /next/i })).toBeVisible();

      await user.click(screen.getByRole("button", { name: /next/i }));
      expect(
        screen.getByRole("spinbutton", { name: /inventory:sample\.createOptions\.newSubsamples\.quantityLabel/i }),
      ).toBeVisible();
      expect(createButton()).toBeEnabled();
      await user.type(
        screen.getByRole("spinbutton", { name: /inventory:sample\.createOptions\.newSubsamples\.quantityLabel/i }),
        "4",
      );
    });
    test("Clearing the quantity field disables the submit button", async () => {
      const user = userEvent.setup();
      const sample = makeMockSample({});
      vi.spyOn(sample, "fetchAdditionalInfo").mockImplementation(() => Promise.resolve());
      render(
        <ThemeProvider theme={materialTheme}>
          <CreateDialog existingRecord={sample} open={true} onClose={() => {}} />
        </ThemeProvider>,
      );
      expect(
        await screen.findByRole("radio", {
          name: /inventory:sample\.createOptions\.newSubsamples\.label/,
        }),
      ).toBeEnabled();
      await user.click(
        screen.getByRole("radio", {
          name: /inventory:sample\.createOptions\.newSubsamples\.label/,
        }),
      );

      await user.click(screen.getByRole("button", { name: /next/i }));
      expect(createButton()).toBeEnabled();
      await user.clear(
        screen.getByRole("spinbutton", { name: /inventory:sample\.createOptions\.newSubsamples\.quantityLabel/i }),
      );
      expect(createButton()).toBeDisabled();
    });
  });
});
