/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import { Heading, HeadingContext } from "../DynamicHeadingLevel";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("DynamicHeadingLevel", () => {
  test("Should default to level 1", () => {
    render(<Heading>Test</Heading>);

    expect(
      screen.getByRole("heading", { name: /Test/, level: 1 })
    ).toBeInTheDocument();
  });

  test("Using a HeadingContext should result in a level 2", () => {
    render(
      <HeadingContext>
        <Heading>Test</Heading>
      </HeadingContext>
    );

    expect(
      screen.getByRole("heading", { name: /Test/, level: 2 })
    ).toBeInTheDocument();
  });

  test("Nesting HeadingContexts should increment the level.", () => {
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

  test("Specifying level allows skipping levels.", () => {
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

  test("Specifying level on a nested HeadingContext is not allowed.", () => {
    expect(() => {
      render(
        <HeadingContext>
          <HeadingContext level={1}>
            <Heading>Test</Heading>
          </HeadingContext>
        </HeadingContext>
      );
    }).toThrow();
  });

  test("Nesting should max out at 6.", () => {
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

  test("Variant should change css styles but leave element type untouched.", () => {
    render(
      <HeadingContext>
        <Heading variant="h5">Test</Heading>
      </HeadingContext>
    );

    expect(
      screen.getByRole("heading", { name: /Test/, level: 2 }).className
    ).toMatch(/MuiTypography-h5/);
  });

  test("Variant should default to the level.", () => {
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
