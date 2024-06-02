/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import FormField from "../FormField";
import StringField from "../StringField";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("FormField", () => {
  test("Should render HTMLLabelElement with `for` attribute that points to HTMLInputElement.", () => {
    render(
      <FormField
        label="Test"
        value="foo"
        renderInput={(props) => <StringField {...props} />}
      />
    );

    expect(screen.getAllByLabelText("Test")).toContain(
      screen.getByRole("textbox")
    );
  });

  test("Should not render HTMLLabelElement with `for` attribute that points to HTMLInputElement, when doNotAttachIdToLabel is true.", () => {
    render(
      <FormField
        label="Test"
        value="foo"
        renderInput={(props) => <StringField {...props} />}
        doNotAttachIdToLabel
      />
    );

    expect(screen.getAllByLabelText("Test")).not.toContain(
      screen.getByRole("textbox")
    );
  });
  test("When disabled is true, a heading should be rendered instead of a label", () => {
    render(
      <FormField
        label="Test"
        value="foo"
        renderInput={(props) => <StringField {...props} />}
        disabled
      />
    );

    expect(screen.getByRole("heading")).toBeInTheDocument();
  });
});
