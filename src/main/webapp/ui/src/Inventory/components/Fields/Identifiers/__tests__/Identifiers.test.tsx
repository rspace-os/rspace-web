import { render, screen } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import type { InventoryRecord } from "../../../../../stores/definitions/InventoryRecord";
import { makeMockContainer } from "../../../../../stores/models/__tests__/ContainerModel/mocking";
import { makeMockSample } from "../../../../../stores/models/__tests__/SampleModel/mocking";
import { IdentifiersList } from "../Identifiers";
import { mockIGSNIdentifier } from "./mocking";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";

import materialTheme from "../../../../../theme";

const sample1: InventoryRecord = makeMockSample();
sample1.identifiers = [mockIGSNIdentifier("sample")];
const container1: InventoryRecord = makeMockContainer();

container1.identifiers = [mockIGSNIdentifier("container")];
describe("Identifiers section", () => {
  describe("When an identifier exists", () => {
    test("Identifier fields sections are rendered", () => {
      const { container } = render(
        <ThemeProvider theme={materialTheme}>
          <IdentifiersList activeResult={sample1} />
        </ThemeProvider>,
      );
      expect(container).toHaveTextContent("fields.identifiers.wrapper.required.title");
      expect(container).toHaveTextContent("fields.identifiers.wrapper.recommended.title");
    });
  });
  describe("When an identifier exists for container", () => {
    test("Required fields are rendered", () => {
      const { container } = render(
        <ThemeProvider theme={materialTheme}>
          <IdentifiersList activeResult={container1} />
        </ThemeProvider>,
      );
      expect(container).toHaveTextContent("Material Sample");
    });
  });
  describe("When viewing a historical version", () => {
    test("Preview, Publish/Republish and Delete/Retract are all disabled", () => {
      const historicalSample: InventoryRecord = makeMockSample({
        version: 1,
        historicalVersion: true,
      });
      historicalSample.identifiers = [mockIGSNIdentifier("sample")];
      render(
        <ThemeProvider theme={materialTheme}>
          <IdentifiersList activeResult={historicalSample} />
        </ThemeProvider>,
      );
      expect(screen.getByRole("button", { name: "inventory:fields.identifiers.list.preview" })).toBeDisabled();
      expect(screen.getByRole("button", { name: /republish|publish/i })).toBeDisabled();
      expect(screen.getByRole("button", { name: /delete|retract/i })).toBeDisabled();
    });
  });
});
