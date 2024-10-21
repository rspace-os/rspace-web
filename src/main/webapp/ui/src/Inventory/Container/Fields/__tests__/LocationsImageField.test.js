/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import { act } from "react-dom/test-utils";
import { storesContext } from "../../../../stores/stores-context";
import "@testing-library/jest-dom";
import ContainerModel from "../../../../stores/models/ContainerModel";
import { type StoreContainer } from "../../../../stores/stores/RootStore";
import {
  makeMockRootStore,
  type MockStores,
} from "../../../../stores/stores/__tests__/RootStore/mocking";
import { makeMockContainer } from "../../../../stores/models/__tests__/ContainerModel/mocking";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";

import LocationsImageField from "../LocationsImageField";
import ImageField from "../../../../components/Inputs/ImageField";
import LocationsImageMarkersDialog from "../LocationsImageMarkersDialog";
import userEvent from "@testing-library/user-event";

let storeImageFunction;

jest.mock("../../../../stores/stores/RootStore", () => jest.fn(() => ({})));
jest.mock("../../../../components/Inputs/ImageField", () =>
  jest.fn(({ storeImage, endAdornment }) => {
    storeImageFunction = storeImage;
    return <div data-testid="ImageField">{endAdornment}</div>;
  })
);
jest.mock("../LocationsImageMarkersDialog", () =>
  jest.fn(({ close }) => {
    return (
      <div>
        <button onClick={close}>Close</button>
      </div>
    );
  })
);

class ResizeObserver {
  observe() {}
  unobserve() {}
}
window.ResizeObserver = ResizeObserver;

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

const mockRootStore = (
  mockedStores: ?MockStores
): [StoreContainer, ContainerModel] => {
  const activeResult = makeMockContainer({
    cType: "IMAGE",
  });
  activeResult.editing = true;
  activeResult.updateFieldsState();
  return [
    makeMockRootStore({
      ...mockedStores,
      uiStore: {
        isSingleColumnLayout: false,
        addAlert: jest.fn(),
        removeAlert: jest.fn(),
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
        </ThemeProvider>
      );

      expect(screen.getByTestId("ImageField")).toBeInTheDocument();
      expect(ImageField).toHaveBeenCalledWith(
        expect.objectContaining({
          warningAlert:
            "Visual containers require an image to add locations to. Click on 'Add Image' (above) to provide one.",
        }),
        expect.anything()
      );
    });

    test("be a button labelled 'Edit Locations' that can't be tapped.", () => {
      const rootStore = mockRootStore()[0];

      render(
        <ThemeProvider theme={materialTheme}>
          <storesContext.Provider value={rootStore}>
            <LocationsImageField />
          </storesContext.Provider>
        </ThemeProvider>
      );

      expect(
        screen.getByRole("button", {
          name: /Edit Locations/,
        })
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
      const setImageSpy = jest.spyOn(
        rootStore.searchStore.activeResult,
        "setImage"
      );

      render(
        <ThemeProvider theme={materialTheme}>
          <storesContext.Provider value={rootStore}>
            <LocationsImageField />
          </storesContext.Provider>
        </ThemeProvider>
      );

      expect(screen.getByTestId("ImageField")).toBeInTheDocument();
      expect(ImageField).toHaveBeenCalledWith(
        expect.objectContaining({
          storeImage: expect.any(Function),
        }),
        expect.anything()
      );

      act(() => {
        storeImageFunction({ dataUrl: "", file: new Blob() });
      });

      expect(setImageSpy).toHaveBeenCalledWith(
        "locationsImage",
        expect.any(String)
      );
    });

    test("be an alert to update the preview image, if the container doesn't have a preview image.", () => {
      const rootStore = mockRootStore()[0];
      const addScopedToastSpy = jest.spyOn(
        rootStore.searchStore.activeResult,
        "addScopedToast"
      );
      const setImageSpy = jest.spyOn(
        rootStore.searchStore.activeResult,
        "setImage"
      );

      let setPreviewImageFunction;
      const addAlertMock = jest
        .spyOn(rootStore.uiStore, "addAlert")
        .mockImplementation(({ onActionClick }) => {
          setPreviewImageFunction = onActionClick;
        });

      render(
        <ThemeProvider theme={materialTheme}>
          <storesContext.Provider value={rootStore}>
            <LocationsImageField />
          </storesContext.Provider>
        </ThemeProvider>
      );
      act(() => {
        storeImageFunction({ dataUrl: "", file: new Blob() });
      });

      expect(addScopedToastSpy).toHaveBeenCalled();
      expect(addAlertMock).toHaveBeenCalledWith(
        expect.objectContaining({ message: "Set preview image too?" })
      );
      act(() => {
        setPreviewImageFunction();
      });
      expect(setImageSpy).toHaveBeenCalledWith("image", expect.any(String));
    });

    test("not be an alert, if the container already has a preview image.", () => {
      const [rootStore, container] = mockRootStore();
      const addScopedToastSpy = jest.spyOn(
        rootStore.searchStore.activeResult,
        "addScopedToast"
      );
      container.image = "theBlobUrlOfSomeImage";

      const addAlertMock = jest.spyOn(rootStore.uiStore, "addAlert");

      render(
        <ThemeProvider theme={materialTheme}>
          <storesContext.Provider value={rootStore}>
            <LocationsImageField />
          </storesContext.Provider>
        </ThemeProvider>
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
        </ThemeProvider>
      );

      expect(
        screen.getByRole("button", {
          name: /Edit Locations/,
        })
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
        </ThemeProvider>
      );

      expect(screen.getByTestId("ImageField")).toBeInTheDocument();
      expect(ImageField).toHaveBeenCalledWith(
        expect.objectContaining({
          warningAlert:
            "Click on ‘Edit Locations’ to add locations and start using the visual container.",
        }),
        expect.anything()
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
        </ThemeProvider>
      );

      const editLocationsButtons = screen.getByText("Edit Locations");
      await user.click(editLocationsButtons);
      expect(LocationsImageMarkersDialog).toHaveBeenLastCalledWith(
        { open: true, close: expect.any(Function) },
        expect.anything()
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
        </ThemeProvider>
      );

      const editLocationsButtons = screen.getByText("Edit Locations");
      await user.click(editLocationsButtons);
      await user.click(screen.getByText("Close"));
      expect(LocationsImageMarkersDialog).toHaveBeenLastCalledWith(
        { open: false, close: expect.any(Function) },
        expect.anything()
      );
    });
  });
});
