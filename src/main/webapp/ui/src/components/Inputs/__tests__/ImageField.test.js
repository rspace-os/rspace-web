/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import CameraAltIcon from "@mui/icons-material/CameraAlt";
import ImageIcon from "@mui/icons-material/Image";
import { __setIsMobile } from "react-device-detect";

import ImageField from "../ImageField";
import DynamicallyLoadedImageEditor from "../DynamicallyLoadedImageEditor";
import userEvent from "@testing-library/user-event";

jest.mock("@mui/icons-material/CameraAlt", () => jest.fn(() => <div></div>));
jest.mock("@mui/icons-material/Image", () => jest.fn(() => <div></div>));
jest.mock("../FileField", () =>
  jest.fn(({ icon, InputProps: { endAdornment } }) => {
    return (
      <>
        <div id="icon">{icon}</div>
        <div id="endAdornment">{endAdornment}</div>
      </>
    );
  })
);
jest.mock("@mui/material/Button", () =>
  jest.fn(({ children, onClick }) => {
    return <div onClick={onClick}>{children}</div>;
  })
);
jest.mock("react-device-detect");
jest.mock("../DynamicallyLoadedImageEditor", () =>
  jest.fn(() => {
    return <div></div>;
  })
);

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

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
          <ImageField storeImage={() => {}} imageAsObjectURL={null} id="foo" />
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
          <ImageField storeImage={() => {}} imageAsObjectURL={null} id="foo" />
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
        <ImageField storeImage={() => {}} imageAsObjectURL={null} id="foo" />
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
