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

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("ImageEditingDialog", () => {
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
});
