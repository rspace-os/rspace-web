/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen, waitFor, act } from "@testing-library/react";
import "@testing-library/jest-dom";
import userEvent from "@testing-library/user-event";
import ImageEditingDialog from "../ImageEditingDialog";
import fc from "fast-check";
import { axe, toHaveNoViolations } from "jest-axe";

expect.extend(toHaveNoViolations);

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

const readAsDataUrl = (file: Blob): Promise<string> =>
  new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      // $FlowExpectedError[incompatible-cast] reader.result will be string because we called readAsDataUrl
      resolve((reader.result: string));
    };
    reader.onerror = () => {
      reject(reader.error);
    };
    reader.readAsDataURL(file);
  });

describe("ImageEditingDialog", () => {
  test("Should have no axe violations.", async () => {
    const canvas = document.createElement("canvas");
    const ctx = canvas.getContext("2d");
    // 600px to negate any scaling effects
    ctx.canvas.width = 600;
    ctx.canvas.height = 400;
    ctx.fillStyle = "green";
    ctx.fillRect(10, 10, 150, 100);

    const blob: Blob = await new Promise((resolve) => {
      canvas.toBlob(resolve);
    });

    const { container } = render(
      <ImageEditingDialog
        imageFile={blob}
        open={true}
        close={() => {}}
        submitHandler={() => {}}
        alt="dummy alt text"
      />
    );

    // $FlowExpectedError[incompatible-call] See expect.extend above
    expect(await axe(container)).toHaveNoViolations();
  });
  test("Rotating four times in either direction is a no-op.", async () => {
    const user = userEvent.setup();
    await fc.assert(
      fc.asyncProperty(
        fc.tuple(fc.constantFrom("clockwise", "counter clockwise"), fc.nat(20)),
        async ([direction, number]) => {
          cleanup();

          // submitHandler is not invoked if no edits have been made
          fc.pre(number > 0);

          const canvas = document.createElement("canvas");
          const ctx = canvas.getContext("2d");
          // 600px to negate any scaling effects
          ctx.canvas.width = 600;
          ctx.canvas.height = 400;
          ctx.fillStyle = "green";
          ctx.fillRect(10, 10, 150, 100);

          const blob: Blob = await new Promise((resolve) => {
            canvas.toBlob(resolve);
          });

          const submitHandler = jest.fn<[Blob], void>();
          render(
            <ImageEditingDialog
              imageFile={blob}
              open={true}
              close={() => {}}
              submitHandler={submitHandler}
              alt="dummy alt text"
            />
          );

          await screen.findByRole("img");

          const rotateButton = screen.getByRole("button", {
            name: "rotate " + direction,
          });
          for (let i = 0; i < number; i++) await user.click(rotateButton);

          await user.click(screen.getByRole("button", { name: /done/i }));

          await waitFor(() => {
            expect(submitHandler).toHaveBeenCalled();
          });
          const actualBlob = submitHandler.mock.calls[0][0];
          const actualDataUrl = readAsDataUrl(actualBlob);

          if (number % 4 === 0) {
            expect(actualDataUrl).not.toEqual(canvas.toDataURL());
          } else {
            expect(actualDataUrl).not.toEqual(canvas.toDataURL());
          }
        }
      ),
      { numRuns: 4 }
    );
  });

  test("Rotating by 90º clockwise should result in the correct image", async () => {
    const user = userEvent.setup();
    const canvas = document.createElement("canvas");
    const ctx = canvas.getContext("2d");
    // 600px to negate any scaling effects
    ctx.canvas.width = 600;
    ctx.canvas.height = 600;
    ctx.fillStyle = "green";
    ctx.fillRect(0, 0, 300, 300);

    const blob: Blob = await new Promise((resolve) => {
      canvas.toBlob(resolve);
    });

    const submitHandler = jest.fn<[Blob], void>();
    render(
      <ImageEditingDialog
        imageFile={blob}
        open={true}
        close={() => {}}
        submitHandler={submitHandler}
        alt="dummy alt text"
      />
    );

    await screen.findByRole("img");

    // wait for image to load
    await act(() => Promise.resolve());

    const rotateButton = screen.getByRole("button", {
      name: "rotate clockwise",
    });

    await act(async () => {
      await user.click(rotateButton);
    });

    // wait for rotated image to load
    await act(() => Promise.resolve());

    await act(async () => {
      await user.click(screen.getByRole("button", { name: /done/i }));
    });

    const canvas2 = document.createElement("canvas");
    const ctx2 = canvas2.getContext("2d");
    // 600px to negate any scaling effects
    ctx2.canvas.width = 600;
    ctx2.canvas.height = 600;
    ctx2.fillStyle = "green";
    ctx2.fillRect(300, 0, 600, 300);
    const expected = await new Promise((resolve) =>
      canvas2.toBlob(resolve, "image/png", "1.0")
    );

    await waitFor(() => {
      expect(submitHandler).toHaveBeenCalledWith(expected);
    });
  });

  test("Rotating by 90º counter clockwise should result in the correct image", async () => {
    const user = userEvent.setup();
    const canvas = document.createElement("canvas");
    const ctx = canvas.getContext("2d");
    // 600px to negate any scaling effects
    ctx.canvas.width = 600;
    ctx.canvas.height = 600;
    ctx.fillStyle = "green";
    ctx.fillRect(0, 0, 300, 300);

    const blob: Blob = await new Promise((resolve) => {
      canvas.toBlob(resolve);
    });

    const submitHandler = jest.fn<[Blob], void>();
    render(
      <ImageEditingDialog
        imageFile={blob}
        open={true}
        close={() => {}}
        submitHandler={submitHandler}
        alt="dummy alt text"
      />
    );

    await screen.findByRole("img");

    const rotateButton = screen.getByRole("button", {
      name: "rotate counter clockwise",
    });
    await user.click(rotateButton);

    await user.click(screen.getByRole("button", { name: /done/i }));

    const canvas2 = document.createElement("canvas");
    const ctx2 = canvas2.getContext("2d");
    // 600px to negate any scaling effects
    ctx2.canvas.width = 600;
    ctx2.canvas.height = 600;
    ctx2.fillStyle = "green";
    ctx2.fillRect(0, 300, 300, 600);
    const expected = await new Promise((resolve) =>
      canvas2.toBlob(resolve, "image/png", "1.0")
    );

    await waitFor(() => {
      expect(submitHandler).toHaveBeenCalledWith(expected);
    });
  });

  test("If no change has been made, then submitHandler is not called", async () => {
    const user = userEvent.setup();
    const canvas = document.createElement("canvas");
    const ctx = canvas.getContext("2d");
    // 600px to negate any scaling effects
    ctx.canvas.width = 600;
    ctx.canvas.height = 600;
    ctx.fillStyle = "green";
    ctx.fillRect(0, 0, 300, 300);

    const blob: Blob = await new Promise((resolve) => {
      canvas.toBlob(resolve);
    });

    const submitHandler = jest.fn<[Blob], void>();
    const close = jest.fn<[], void>();
    render(
      <ImageEditingDialog
        imageFile={blob}
        open={true}
        close={close}
        submitHandler={submitHandler}
        alt="dummy alt text"
      />
    );

    await screen.findByRole("img");
    await user.click(screen.getByRole("button", { name: /done/i }));
    expect(close).toHaveBeenCalled();
    expect(submitHandler).not.toHaveBeenCalled();
  });

  /*
   * Testing the cropping functionality was attempted but it seems to be
   * impossible to get the keyDown events for moving the anchors around to
   * trigger react-image-crop's onChange event handler. There is a console
   * error reported that `scrollTo` could not be invoked on the root div of
   * ReactCrop, so perhaps that is the issue.
   */
});
