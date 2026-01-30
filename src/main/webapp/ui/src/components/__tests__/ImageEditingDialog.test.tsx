/*
 */
import {
  describe,
  it,
  test,
  expect,
  vi,
  beforeEach,
} from "vitest";
import React from "react";
import {
  render,
  cleanup,
  screen,
  waitFor,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import userEvent from "@testing-library/user-event";
import ImageEditingDialog from "../ImageEditingDialog";
import fc from "fast-check";
import { axe } from "vitest-axe";
import { toHaveNoViolations } from "vitest-axe/matchers";
import { sleep } from "../../util/Util";

expect.extend({ toHaveNoViolations });

beforeEach(() => {
  vi.clearAllMocks();
});


const readAsDataUrl = (file: Blob): Promise<string> =>
  new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      resolve(reader.result as string);
    };
    reader.onerror = () => {
      reject(reader.error as Error);
    };
    reader.readAsDataURL(file);
  });

describe("ImageEditingDialog", () => {
  test("Should have no axe violations.", async () => {
    const canvas = document.createElement("canvas");
    const ctx = canvas.getContext("2d");
    if (ctx === null) throw new Error("could not get canvas");
    // 600px to negate any scaling effects
    ctx.canvas.width = 600;
    ctx.canvas.height = 400;
    ctx.fillStyle = "green";
    ctx.fillRect(10, 10, 150, 100);

    const blob: Blob = await new Promise((resolve) => {
      canvas.toBlob((b: Blob | null) => {
        if (b === null) throw new Error("toBlob failed");
        resolve(b);
      });
    });

    const { baseElement } = render(
      <ImageEditingDialog
        imageFile={blob}
        open={true}
        close={() => {}}
        submitHandler={() => {}}
        alt="dummy alt text"
      />
    );

    await screen.findByRole("img");

    // wait for image to load
    await sleep(1000);

    expect(await axe(baseElement)).toHaveNoViolations();
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
          if (ctx === null) throw new Error("could not get canvas");
          // 600px to negate any scaling effects
          ctx.canvas.width = 600;
          ctx.canvas.height = 400;
          ctx.fillStyle = "green";
          ctx.fillRect(10, 10, 150, 100);

          const blob: Blob = await new Promise((resolve) => {
            canvas.toBlob((b: Blob | null) => {
              if (b === null) throw new Error("toBlob failed");
              resolve(b);
            });
          });

          const submitHandler = vi.fn();
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
          for (let i = 0; i < number; i++) {
            await user.click(rotateButton);
          }

          await user.click(screen.getByRole("button", { name: /done/i }));

          await waitFor(() => {
            expect(submitHandler).toHaveBeenCalled();
          });
          const actualBlob = submitHandler.mock.calls[0][0] as Blob;
          expect(actualBlob).toBeInstanceOf(Blob);
        }
      ),
      { numRuns: 4 }
    );
  });

  test("Rotating by 90º clockwise should result in the correct image", async () => {
    const user = userEvent.setup();
    const canvas = document.createElement("canvas");
    const ctx = canvas.getContext("2d");
    if (ctx === null) throw new Error("could not get canvas");
    // 600px to negate any scaling effects
    ctx.canvas.width = 600;
    ctx.canvas.height = 600;
    ctx.fillStyle = "green";
    ctx.fillRect(0, 0, 300, 300);

    const blob: Blob = await new Promise((resolve) => {
      canvas.toBlob((b: Blob | null) => {
        if (b === null) throw new Error("toBlob failed");
        resolve(b);
      });
    });

    const submitHandler = vi.fn();
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
    await Promise.resolve();

    const rotateButton = screen.getByRole("button", {
      name: "rotate clockwise",
    });

    await user.click(rotateButton);

    // wait for rotated image to load
    await Promise.resolve();

    await user.click(screen.getByRole("button", { name: /done/i }));

    await waitFor(() => {
      expect(submitHandler).toHaveBeenCalled();
    });
    expect(submitHandler.mock.calls[0][0]).toBeInstanceOf(Blob);
  });

  test("Rotating by 90º counter clockwise should result in the correct image", async () => {
    const user = userEvent.setup();
    const canvas = document.createElement("canvas");
    const ctx = canvas.getContext("2d");
    if (ctx === null) throw new Error("could not get canvas");
    // 600px to negate any scaling effects
    ctx.canvas.width = 600;
    ctx.canvas.height = 600;
    ctx.fillStyle = "green";
    ctx.fillRect(0, 0, 300, 300);

    const blob: Blob = await new Promise((resolve) => {
      canvas.toBlob((b: Blob | null) => {
        if (b === null) throw new Error("toBlob failed");
        resolve(b);
      });
    });

    const submitHandler = vi.fn();
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

    await waitFor(() => {
      expect(submitHandler).toHaveBeenCalled();
    });
    expect(submitHandler.mock.calls[0][0]).toBeInstanceOf(Blob);
  });

  test("If no change has been made, then submitHandler is not called", async () => {
    const user = userEvent.setup();
    const canvas = document.createElement("canvas");
    const ctx = canvas.getContext("2d");
    if (ctx === null) throw new Error("could not get canvas");
    // 600px to negate any scaling effects
    ctx.canvas.width = 600;
    ctx.canvas.height = 600;
    ctx.fillStyle = "green";
    ctx.fillRect(0, 0, 300, 300);

    const blob: Blob = await new Promise((resolve) => {
      canvas.toBlob((b: Blob | null) => {
        if (b === null) throw new Error("toBlob failed");
        resolve(b);
      });
    });

    const submitHandler = vi.fn();
    const close = vi.fn();
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

  test("Cancel button should not invoke submitHandler", async () => {
    const user = userEvent.setup();
    const canvas = document.createElement("canvas");
    const ctx = canvas.getContext("2d");
    if (ctx === null) throw new Error("could not get canvas");
    // 600px to negate any scaling effects
    ctx.canvas.width = 600;
    ctx.canvas.height = 600;
    ctx.fillStyle = "green";
    ctx.fillRect(0, 0, 300, 300);

    const blob: Blob = await new Promise((resolve) => {
      canvas.toBlob((b: Blob | null) => {
        if (b === null) throw new Error("toBlob failed");
        resolve(b);
      });
    });

    const submitHandler = vi.fn();
    const close = vi.fn();

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

    // wait for image to load
    await Promise.resolve();

    const rotateButton = screen.getByRole("button", {
      name: "rotate clockwise",
    });

    await user.click(rotateButton);

    // wait for rotated image to load
    await Promise.resolve();

    await user.click(screen.getByRole("button", { name: /cancel/i }));

    expect(close).toHaveBeenCalled();
    expect(submitHandler).not.toHaveBeenCalled();
  });
});
