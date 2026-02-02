import { test, describe, expect, vi } from 'vitest';
import React from "react";
import {
  render,
} from "@testing-library/react";
import { mockIGSNIdentifier } from "./mocking";
import { IdentifiersList } from "../Identifiers";
import type { InventoryRecord } from "../../../../../stores/definitions/InventoryRecord";
import { makeMockSample } from "../../../../../stores/models/__tests__/SampleModel/mocking";
import { makeMockContainer } from "../../../../../stores/models/__tests__/ContainerModel/mocking";
import "../../../../../../__mocks__/matchMedia";
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
        </ThemeProvider>
      );
      expect(container).toHaveTextContent("Required Identifier Properties");
      expect(container).toHaveTextContent("Recommended Identifier Properties");
    });
  });
  describe("When an identifier exists for container", () => {
    test("Required fields are rendered", () => {
      const { container } = render(
        <ThemeProvider theme={materialTheme}>
          <IdentifiersList activeResult={container1} />
        </ThemeProvider>
      );
      expect(container).toHaveTextContent("Material Sample");
    });
  });
});


