import { beforeEach, describe, expect, test, vi } from "vitest";
import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";

const mockAxios = new MockAdapter(axios);

if (!("withResolvers" in Promise)) {
  Object.assign(Promise, {
    withResolvers<T>() {
      let resolve!: (value: T | PromiseLike<T>) => void;
      let reject!: (reason?: unknown) => void;
      const promise = new Promise<T>((res, rej) => {
        resolve = res;
        reject = rej;
      });
      return { promise, resolve, reject };
    },
  });
}

beforeEach(() => {
  mockAxios.reset();
  vi.stubGlobal(
    "matchMedia",
    vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener() {},
      removeListener() {},
      addEventListener() {},
      removeEventListener() {},
      dispatchEvent() {
        return false;
      },
    })),
  );
  vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:test");
  mockAxios.onGet("/collaboraOnline/supportedExts").reply(200, {});
  mockAxios.onGet("/officeOnline/supportedExts").reply(200, {});
  mockAxios.onGet(/\/deploymentproperties\/ajax\/property.*/).reply(200, false);
  mockAxios.onGet(/\/api\/v1\/files\/\d+\/file/).reply(200, new Blob(["x"], { type: "image/png" }));
});

describe("Carousel", () => {
  test("shows progress through the listing", async () => {
    const user = userEvent.setup();
    const { SimpleCarousel } = await import("./Carousel.story");
    render(<SimpleCarousel />);

    expect(await screen.findByRole("status", { name: "Current file index" })).toHaveTextContent("1 / 8");
    await user.click(screen.getByRole("button", { name: /next/i }));
    expect(screen.getByRole("status", { name: "Current file index" })).toHaveTextContent("2 / 8");
  });

  test("resets the zoom level when moving to a different file", async () => {
    const user = userEvent.setup();
    const { SimpleCarousel } = await import("./Carousel.story");
    render(<SimpleCarousel />);

    await screen.findByRole("status", { name: "Current file index" });
    await user.click(screen.getByRole("button", { name: /zoom in/i }));
    await waitFor(() => {
      expect(screen.getByRole("button", { name: /reset zoom/i })).toBeEnabled();
    });
    await user.click(screen.getByRole("button", { name: /next/i }));
    await waitFor(() => {
      expect(screen.getByRole("button", { name: /reset zoom/i })).toBeDisabled();
    });
  });

  test("is accessible", async () => {
    const { SimpleCarousel } = await import("./Carousel.story");
    const { container } = render(<SimpleCarousel />);
    await screen.findByRole("status", { name: "Current file index" });
    /* eslint-disable @typescript-eslint/no-unsafe-call */
    // @ts-expect-error toBeAccessible is provided by @sa11y/vitest
    await expect(container).toBeAccessible();
    /* eslint-enable @typescript-eslint/no-unsafe-call */
  });
});
