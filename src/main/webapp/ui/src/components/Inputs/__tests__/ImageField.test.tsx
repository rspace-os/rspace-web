/*
 */
import {
  describe,
  test,
  expect,
  vi,
  beforeEach,
} from "vitest";
import React from "react";
import {
  render,
  screen,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import CameraAltIcon from "@mui/icons-material/CameraAlt";
import ImageIcon from "@mui/icons-material/Image";
import { __setIsMobile } from "react-device-detect";
import ImageField from "../ImageField";
import DynamicallyLoadedImageEditor from "../DynamicallyLoadedImageEditor";
import userEvent from "@testing-library/user-event";

declare module "react-device-detect" {
  export const __setIsMobile: (value: boolean) => void;
}

vi.mock("@mui/icons-material/CameraAlt", () => ({
  default: vi.fn(() => <div></div>),
}));
vi.mock("@mui/icons-material/Image", () => ({
  default: vi.fn(() => <div></div>),
}));
vi.mock("../FileField", () => ({
  default: vi.fn(({ icon, InputProps: { endAdornment } }) => {
    return (
      <>
        <div id="icon">{icon}</div>
        <div id="endAdornment">{endAdornment}</div>
      </>
    );
  }),
}));
vi.mock("@mui/material/Button", () => ({
  default: vi.fn(({ children, onClick }) => {
    return <div onClick={onClick}>{children}</div>;
  }),
}));
vi.mock("react-device-detect");
vi.mock("../DynamicallyLoadedImageEditor", () => ({
  default: vi.fn(() => {
    return <div></div>;
  }),
}));

beforeEach(() => {
  vi.clearAllMocks();
});


describe("ImageField", () => {
  /*
   * In the button for selecting a file, icons are shown based on what is most
   * intuitive for the particular device.
   */
  describe("When viewed on different devices, there should be different icons shown.", () => {
    describe("On mobile, there should", () => {
      beforeEach(() => {
        __setIsMobile(true);
      });

      test("be a camera icon shown.", () => {
        render(
          <ImageField
            storeImage={() => {}}
            imageAsObjectURL={null}
            id="foo"
            alt="dummy alt text"
          />
        );
        expect(CameraAltIcon).toHaveBeenCalled();
      });
    });

    describe("On desktop, there should", () => {
      beforeEach(() => {
        __setIsMobile(false);
      });

      test("be an image icon shown.", () => {
        render(
          <ImageField
            storeImage={() => {}}
            imageAsObjectURL={null}
            id="foo"
            alt="dummy alt text"
          />
        );
        expect(ImageIcon).toHaveBeenCalled();
      });
    });
  });

  /*
   * Tapping 'Edit Image' should open the image editor
   */
  describe("When the 'Edit Image' button is tapped there should", () => {
    test("be a DynamicallyLoadedImageEditor that opens.", async () => {
      const user = userEvent.setup();
      render(
        <ImageField
          storeImage={() => {}}
          imageAsObjectURL={null}
          id="foo"
          alt="dummy alt text"
        />
      );

      const editImageButton = screen.getByText("Edit Image");
      await user.click(editImageButton);
      expect(DynamicallyLoadedImageEditor).toHaveBeenCalledWith(
        expect.objectContaining({
          editorOpen: true,
          close: expect.any(Function),
          submitHandler: expect.any(Function),
        }),
        expect.anything()
      );
    });
  });
});

