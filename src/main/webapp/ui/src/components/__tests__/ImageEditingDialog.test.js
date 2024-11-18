/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen } from "@testing-library/react";
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

          const submitHandler = jest.fn<[string], void>();
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

          if (number % 4 === 0) {
            expect(submitHandler).toHaveBeenCalledWith(
              canvas.toDataURL("image/png", "1.0")
            );
          } else {
            expect(submitHandler).not.toHaveBeenCalledWith(
              canvas.toDataURL("image/png", "1.0")
            );
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

    const submitHandler = jest.fn<[string], void>();
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
      name: "rotate clockwise",
    });
    await user.click(rotateButton);

    await user.click(screen.getByRole("button", { name: /done/i }));

    const canvas2 = document.createElement("canvas");
    const ctx2 = canvas2.getContext("2d");
    // 600px to negate any scaling effects
    ctx2.canvas.width = 600;
    ctx2.canvas.height = 600;
    ctx2.fillStyle = "green";
    ctx2.fillRect(300, 0, 600, 300);

    expect(submitHandler).toHaveBeenCalledWith(
      canvas2.toDataURL("image/png", "1.0")
    );
  });

  /*
   * Testing the cropping functionality was attempted but it seems to be
   * impossible to get the keyDown events for moving the anchors around to
   * trigger react-image-crop's onChange event handler. There is a console
   * error reported that `scrollTo` could not be invoked on the root div of
   * ReactCrop, so perhaps that is the issue.
   */
});
