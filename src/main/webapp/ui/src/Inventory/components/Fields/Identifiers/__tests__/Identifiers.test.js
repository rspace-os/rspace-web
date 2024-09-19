/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup } from "@testing-library/react";
import "@testing-library/jest-dom";
import { mockIGSNIdentifier } from "./mocking";
import { IdentifiersList } from "../Identifiers";
import type { InventoryRecord } from "../../../../../stores/definitions/InventoryRecord";
import { makeMockSample } from "../../../../../stores/models/__tests__/SampleModel/mocking";
import { makeMockContainer } from "../../../../../stores/models/__tests__/ContainerModel/mocking";
import "../../../../../../__mocks__/createObjectURL.js";
import "../../../../../../__mocks__/matchMedia.js";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../../theme";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

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
