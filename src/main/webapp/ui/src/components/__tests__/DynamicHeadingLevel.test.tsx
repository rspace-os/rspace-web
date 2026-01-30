import {
  describe,
  expect,
  beforeEach,
  it,
  vi,
} from "vitest";
import React from "react";
import {
  render,
  screen,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { Heading, HeadingContext } from "../DynamicHeadingLevel";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";

beforeEach(() => {
  vi.clearAllMocks();
});


describe("DynamicHeadingLevel", () => {
  it("Should default to level 1", () => {
    render(<Heading>Test</Heading>);

    expect(
      screen.getByRole("heading", { name: /Test/, level: 1 })
    ).toBeInTheDocument();
  });

  it("Using a HeadingContext should result in a level 2", () => {
    render(
      <HeadingContext>
        <Heading>Test</Heading>
      </HeadingContext>
    );

    expect(
      screen.getByRole("heading", { name: /Test/, level: 2 })
    ).toBeInTheDocument();
  });

  it("Nesting HeadingContexts should increment the level.", () => {
    render(
      <HeadingContext>
        <HeadingContext>
          <Heading>Test</Heading>
        </HeadingContext>
      </HeadingContext>
    );

    expect(
      screen.getByRole("heading", { name: /Test/, level: 3 })
    ).toBeInTheDocument();
  });

  it("Specifying level allows skipping levels.", () => {
    render(
      <>
        <h1>Top-level heading</h1>
        <HeadingContext level={2}>
          <Heading>Test</Heading>
        </HeadingContext>
      </>
    );

    expect(
      screen.getByRole("heading", { name: /Test/, level: 2 })
    ).toBeInTheDocument();
  });

  it("Specifying level on a nested HeadingContext is not allowed.", () => {
    const restoreConsole = silenceConsole(["error"], [/./]);
    expect(() => {
      render(
        <HeadingContext>
          <HeadingContext level={1}>
            <Heading>Test</Heading>
          </HeadingContext>
        </HeadingContext>
      );
    }).toThrow();
    restoreConsole();
  });

  it("Nesting should max out at 6.", () => {
    render(
      <HeadingContext>
        <HeadingContext>
          <HeadingContext>
            <HeadingContext>
              <HeadingContext>
                <HeadingContext>
                  <HeadingContext>
                    <HeadingContext>
                      <HeadingContext>
                        <HeadingContext>
                          <HeadingContext>
                            <HeadingContext>
                              <Heading>Test</Heading>
                            </HeadingContext>
                          </HeadingContext>
                        </HeadingContext>
                      </HeadingContext>
                    </HeadingContext>
                  </HeadingContext>
                </HeadingContext>
              </HeadingContext>
            </HeadingContext>
          </HeadingContext>
        </HeadingContext>
      </HeadingContext>
    );

    expect(
      screen.getByRole("heading", { name: /Test/, level: 6 })
    ).toBeInTheDocument();
  });

  it("Variant should change css styles but leave element type untouched.", () => {
    render(
      <HeadingContext>
        <Heading variant="h5">Test</Heading>
      </HeadingContext>
    );

    expect(
      screen.getByRole("heading", { name: /Test/, level: 2 }).className
    ).toMatch(/MuiTypography-h5/);
  });

  it("Variant should default to the level.", () => {
    render(
      <HeadingContext>
        <HeadingContext>
          <Heading>Test</Heading>
        </HeadingContext>
      </HeadingContext>
    );

    expect(
      screen.getByRole("heading", { name: /Test/, level: 3 }).className
    ).toMatch(/MuiTypography-h3/);
  });
});
