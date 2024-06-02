/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import fc from "fast-check";
import ContextMenuButton from "../ContextMenuButton";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("ContextMenuButton", () => {
  describe("Disabled state", () => {
    test("When disabled, should render aria-disabled.", () => {
      fc.assert(
        fc.property(fc.string(), (disabledHelp) => {
          cleanup();
          render(
            <ContextMenuButton
              icon={null}
              disabledHelp={disabledHelp}
              label="Foo"
            />
          );
          expect(screen.getByRole("button", { name: "Foo" })).toHaveAttribute(
            "aria-disabled",
            (disabledHelp !== "").toString()
          );
        })
      );
    });
  });
});
