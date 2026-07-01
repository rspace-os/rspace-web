import { ThemeProvider } from "@mui/material/styles";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import ImageField from "../../../../components/Inputs/ImageField";
import { makeMockContainer } from "../../../../stores/models/__tests__/ContainerModel/mocking";
import type ContainerModel from "../../../../stores/models/ContainerModel";
import { type MockStores, makeMockRootStore } from "../../../../stores/stores/__tests__/RootStore/mocking";
import type { StoreContainer } from "../../../../stores/stores/RootStore";
import { storesContext } from "../../../../stores/stores-context";
import materialTheme from "../../../../theme";
import LocationsImageField from "../LocationsImageField";
import LocationsImageMarkersDialog from "../LocationsImageMarkersDialog";

let storeImageFunction: (arg: { dataUrl: string; file: Blob }) => void;

vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: vi.fn(() => ({
    uiStore: {
      setPageNavigationConfirmation: vi.fn(),
      setDirty: vi.fn(),
    },
  })),
}));
vi.mock("../../../../components/Inputs/ImageField", () => ({
  default: vi.fn(({ storeImage, endAdornment }) => {
    storeImageFunction = storeImage;
    return <div data-testid="ImageField">{endAdornment}</div>;
  }),
}));
vi.mock("../../Content/ImageView/PlaceMarkers/ContentImage", () => ({
  default: vi.fn(() => <div data-testid="ContentImage" />),
}));
vi.mock("../LocationsImageMarkersDialog", () => ({
  default: vi.fn(({ close }) => {
    return (
      <div>
        {/** biome-ignore lint/a11y/useButtonType: initial biome migration */}
        <button onClick={close}>{"Close"}</button>
      </div>
    );
  }),
}));
class ResizeObserver {
  observe(): void {}
  unobserve(): void {}
}

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
window.ResizeObserver = ResizeObserver as any;
const mockRootStore = (mockedStores?: MockStores): [StoreContainer, ContainerModel] => {
  const activeResult = makeMockContainer({
    cType: "IMAGE",
  });
  activeResult.editing = true;
  activeResult.lastEditInput = new Date();
  activeResult.updateFieldsState();
  return [
    makeMockRootStore({
      ...mockedStores,
      uiStore: {
        isSingleColumnLayout: false,
        addAlert: vi.fn(),
        removeAlert: vi.fn(),
        setPageNavigationConfirmation: vi.fn(),
        setDirty: vi.fn(),
      },
      searchStore: {
        activeResult,
      },
    }),
    activeResult,
  ];
};
describe("LocationImageField", () => {
  /*
   * After a visual container has been created, but before a locations image
   * has been upload, the UI should help the user upload an image.
   */
  describe("When there is no locations image there should", () => {
    test("be some help text.", () => {
      render(
        <ThemeProvider theme={materialTheme}>
          <storesContext.Provider value={mockRootStore()[0]}>
            <LocationsImageField />
          </storesContext.Provider>
        </ThemeProvider>,
      );
      expect(screen.getByTestId("ImageField")).toBeInTheDocument();
      expect(ImageField).toHaveBeenCalledWith(
        expect.objectContaining({
          warningAlert: "inventory:container.fields.locationsImage.warningNoImage",
        }),
        undefined,
      );
    });
    test("be a button labelled 'Edit Locations' that can't be tapped.", () => {
      const rootStore = mockRootStore()[0];
      render(
        <ThemeProvider theme={materialTheme}>
          <storesContext.Provider value={rootStore}>
            <LocationsImageField />
          </storesContext.Provider>
        </ThemeProvider>,
      );
      expect(
        screen.getByRole("button", {
          name: /container.fields.locationsImage.editLocations/,
        }),
      ).toBeDisabled();
    });
  });
  /*
   * When a user has uploaded or edited a file the ImageField will call
   * storeImage, which should set the locationsImage and display a toast to
   * suggest changing the preview image too if the container doesn't already
   * have a preview image.
   */
  describe("When an image is uploaded or edited there should", () => {
    test("be a call to setImage.", () => {
      const rootStore = mockRootStore()[0];
      const setImageSpy = vi
        // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
        // biome-ignore lint/style/noNonNullAssertion: initial biome migration
        .spyOn(rootStore.searchStore.activeResult! as any, "setImage")
        .mockImplementation(() => async () => {});
      render(
        <ThemeProvider theme={materialTheme}>
          <storesContext.Provider value={rootStore}>
            <LocationsImageField />
          </storesContext.Provider>
        </ThemeProvider>,
      );
      expect(screen.getByTestId("ImageField")).toBeInTheDocument();
      expect(ImageField).toHaveBeenCalledWith(
        expect.objectContaining({
          storeImage: expect.any(Function),
        }),
        undefined,
      );
      act(() => {
        storeImageFunction({ dataUrl: "", file: new Blob() });
      });
      expect(setImageSpy).toHaveBeenCalledWith("locationsImage");
    });
    test("be an alert to update the preview image, if the container doesn't have a preview image.", () => {
      const rootStore = mockRootStore()[0];
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      // biome-ignore lint/style/noNonNullAssertion: initial biome migration
      const addScopedToastSpy = vi.spyOn(rootStore.searchStore.activeResult! as any, "addScopedToast");
      const setImageSpy = vi
        // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
        // biome-ignore lint/style/noNonNullAssertion: initial biome migration
        .spyOn(rootStore.searchStore.activeResult! as any, "setImage")
        .mockImplementation(() => async () => {});
      let setPreviewImageFunction: () => void;
      const addAlertMock = vi.spyOn(rootStore.uiStore, "addAlert").mockImplementation(({ onActionClick }) => {
        setPreviewImageFunction = onActionClick;
      });
      render(
        <ThemeProvider theme={materialTheme}>
          <storesContext.Provider value={rootStore}>
            <LocationsImageField />
          </storesContext.Provider>
        </ThemeProvider>,
      );
      act(() => {
        storeImageFunction({ dataUrl: "", file: new Blob() });
      });
      expect(addScopedToastSpy).toHaveBeenCalled();
      expect(addAlertMock).toHaveBeenCalledWith(
        expect.objectContaining({ message: "inventory:container.fields.locationsImage.setPreviewImage" }),
      );
      act(() => {
        setPreviewImageFunction();
      });
      expect(setImageSpy).toHaveBeenCalledWith("image");
    });
    test("not be an alert, if the container already has a preview image.", () => {
      const [rootStore, container] = mockRootStore();
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      // biome-ignore lint/style/noNonNullAssertion: initial biome migration
      vi.spyOn(rootStore.searchStore.activeResult! as any, "setImage").mockImplementation(() => async () => {});
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      // biome-ignore lint/style/noNonNullAssertion: initial biome migration
      const addScopedToastSpy = vi.spyOn(rootStore.searchStore.activeResult! as any, "addScopedToast");
      container.image = "theBlobUrlOfSomeImage";

      const addAlertMock = vi.spyOn(rootStore.uiStore, "addAlert");
      render(
        <ThemeProvider theme={materialTheme}>
          <storesContext.Provider value={rootStore}>
            <LocationsImageField />
          </storesContext.Provider>
        </ThemeProvider>,
      );
      act(() => {
        storeImageFunction({ dataUrl: "", file: new Blob() });
      });
      expect(addScopedToastSpy).not.toHaveBeenCalled();
      expect(addAlertMock).not.toHaveBeenCalledWith();
    });
  });
  describe("When a visual container has a locationsImage there should", () => {
    test("be a button labelled 'Edit Locations' that can be tapped.", () => {
      const [rootStore, container] = mockRootStore();

      container.locationsImage = "someImage";
      render(
        <ThemeProvider theme={materialTheme}>
          <storesContext.Provider value={rootStore}>
            <LocationsImageField />
          </storesContext.Provider>
        </ThemeProvider>,
      );
      expect(
        screen.getByRole("button", {
          name: /container.fields.locationsImage.editLocations/,
        }),
      ).toBeEnabled();
    });
  });
  describe("When a visual container has a locationsImage but no markers there should", () => {
    test("be some help text.", () => {
      const [rootStore, container] = mockRootStore();
      container.locationsImage = "someImage";

      container.locationsCount = 0;
      render(
        <ThemeProvider theme={materialTheme}>
          <storesContext.Provider value={rootStore}>
            <LocationsImageField />
          </storesContext.Provider>
        </ThemeProvider>,
      );
      expect(screen.getByTestId("ImageField")).toBeInTheDocument();
      expect(ImageField).toHaveBeenCalledWith(
        expect.objectContaining({
          warningAlert: "inventory:container.fields.locationsImage.warningNoMarkers",
        }),
        undefined,
      );
    });
  });
  /*
   * Once a locationsImage has been set, the user can open the location marking
   * dialog to provide markers as to where items are located in the image.
   */
  describe("When the 'Edit Locations' button is tapped there should", () => {
    test("be a LocationsImageMarkersDialog that opens.", async () => {
      const user = userEvent.setup();
      const [rootStore, container] = mockRootStore();

      container.locationsImage = "someImage";
      render(
        <ThemeProvider theme={materialTheme}>
          <storesContext.Provider value={rootStore}>
            <LocationsImageField />
          </storesContext.Provider>
        </ThemeProvider>,
      );
      const editLocationsButtons = screen.getByText("inventory:container.fields.locationsImage.editLocations");
      await user.click(editLocationsButtons);
      expect(LocationsImageMarkersDialog).toHaveBeenLastCalledWith(
        { open: true, close: expect.any(Function) },
        undefined,
      );
    });
  });
  describe('When the "Close" button inside the LocationsImageMarkersDialog is tapped', () => {
    test("The dialog should close.", async () => {
      const user = userEvent.setup();
      const [rootStore, container] = mockRootStore({
        trackingStore: {
          trackEvent: () => {},
        },
      });

      container.locationsImage = "someImage";
      render(
        <ThemeProvider theme={materialTheme}>
          <storesContext.Provider value={rootStore}>
            <LocationsImageField />
          </storesContext.Provider>
        </ThemeProvider>,
      );
      const editLocationsButtons = screen.getByText("inventory:container.fields.locationsImage.editLocations");
      await user.click(editLocationsButtons);
      await user.click(screen.getByText("Close"));
      expect(LocationsImageMarkersDialog).toHaveBeenLastCalledWith(
        { open: false, close: expect.any(Function) },
        undefined,
      );
    });
  });
});
