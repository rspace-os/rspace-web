import { ThemeProvider } from "@mui/material/styles";
import { render } from "@testing-library/react";
import fc from "fast-check";
// biome-ignore lint/correctness/noUnusedImports: initial biome migration
import React from "react";
import { describe, expect, test, vi } from "vitest";
import { makeMockSubSample, subSampleAttrsArbitrary } from "../../../../stores/models/__tests__/SubSampleModel/mocking";
import materialTheme from "../../../../theme";
import SelectAction from "../SelectAction";

vi.mock("../../../../common/InvApiService", () => ({ default: {} }));
describe("SelectAction", () => {
  describe("Shows a badge that should", () => {
    test("display the count of selected records.", () => {
      fc.assert(
        fc.property(
          /*
           * Only needs to be supported up to 100 as a user cannot select more
           * than 100 items in list view, which is the only place that the
           * SelectAction is shown. Elements of array can of any type because
           * SelectAction doesn't actually care.
           */
          fc.array(subSampleAttrsArbitrary.map(makeMockSubSample), {
            minLength: 1,
            maxLength: 100,
          }),
          (selectedItems) => {
            const { container } = render(
              <ThemeProvider theme={materialTheme}>
                <SelectAction as="button" disabled="" selectedResults={selectedItems} onSelectOptions={[]} />
              </ThemeProvider>,
            );
            expect(container).toHaveTextContent(String(selectedItems.length));
          },
        ),
      );
    });
  });
});
