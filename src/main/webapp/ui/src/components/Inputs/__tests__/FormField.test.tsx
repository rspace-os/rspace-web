import { render, screen } from "@testing-library/react";
// biome-ignore lint/correctness/noUnusedImports: initial biome migration
import React from "react";
import { describe, expect, test } from "vitest";
import FormField from "../FormField";

import StringField from "../StringField";

describe("FormField", () => {
  test("Should render HTMLLabelElement with `for` attribute that points to HTMLInputElement.", () => {
    render(<FormField label="Test" value="foo" renderInput={(props) => <StringField {...props} />} />);
    expect(screen.getAllByLabelText("Test")).toContain(screen.getByRole("textbox"));
  });
  test("Should not render HTMLLabelElement with `for` attribute that points to HTMLInputElement, when doNotAttachIdToLabel is true.", () => {
    render(
      <FormField label="Test" value="foo" renderInput={(props) => <StringField {...props} />} doNotAttachIdToLabel />,
    );
    expect(screen.getAllByLabelText("Test")).not.toContain(screen.getByRole("textbox"));
  });
  test("When disabled is true, a heading should be rendered instead of a label", () => {
    render(<FormField label="Test" value="foo" renderInput={(props) => <StringField {...props} />} disabled />);
    expect(screen.getByRole("heading")).toBeInTheDocument();
  });
});
