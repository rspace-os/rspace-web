/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen, fireEvent } from "@testing-library/react";
import "@testing-library/jest-dom";
import CreateDialog from "../CreateDialog";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import { makeMockSubSample } from "../../../../stores/models/__tests__/SubSampleModel/mocking";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("CreateDialog", () => {
  describe("Splitting", () => {
    test("Subsamples", () => {
      const subsample = makeMockSubSample({});
      render(
        <ThemeProvider theme={materialTheme}>
          <CreateDialog
            existingRecord={subsample}
            open={true}
            onClose={() => {}}
          />
        </ThemeProvider>
      );

      fireEvent.click(
        screen.getByRole("radio", { name: /Subsample, by splitting/ })
      );

      expect(
        screen.getByRole("spinbutton", { name: /Number of new subsamples/i })
      ).toBeVisible();
      expect(screen.getByRole("button", { name: /create/i })).toBeVisible();
    });
  });
});
