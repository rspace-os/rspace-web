import { describe, expect, beforeEach, test, vi } from 'vitest';
import React from "react";
import { render, screen } from "@testing-library/react";
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
  default: vi.fn(
    ({
      icon,
      InputProps,
    }: {
      icon: React.ReactNode;
      InputProps?: { endAdornment?: React.ReactNode };
    }) => {
      return (
        <>
          <div id="icon">{icon}</div>
          <div id="endAdornment">{InputProps?.endAdornment}</div>
        </>
      );
    },
  ),
}));
vi.mock("@mui/material/Button", () => ({
  default: vi.fn(
    ({
      children,
      onClick,
    }: {
      children: React.ReactNode;
      onClick?: () => void;
    }) => {
      return <div onClick={onClick}>{children}</div>;
    },
  ),
}));
let isMobileValue = false;

vi.mock("react-device-detect", () => ({
  get isMobile() {
    return isMobileValue;
  },
  __setIsMobile: vi.fn((value: boolean) => {
    isMobileValue = value;
  }),
}));
vi.mock("../DynamicallyLoadedImageEditor", () => ({
  default: vi.fn(() => {
    return <div></div>;
  }),
}));



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
          />,
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
          />,
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
        />,
      );

      const editImageButton = screen.getByText("Edit Image");
      await user.click(editImageButton);
      expect(DynamicallyLoadedImageEditor).toHaveBeenCalledWith(
        expect.objectContaining({
          editorOpen: true,
          // eslint-disable-next-line @typescript-eslint/no-unsafe-assignment
          close: expect.any(Function),
          // eslint-disable-next-line @typescript-eslint/no-unsafe-assignment
          submitHandler: expect.any(Function),
        }),
        {} as never,
      );
    });
  });
});
