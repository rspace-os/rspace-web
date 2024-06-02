/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import {
  render,
  cleanup,
  waitFor,
  fireEvent,
  screen,
  within,
} from "@testing-library/react";
import "@testing-library/jest-dom";
import { makeMockSample } from "../../../stores/models/__tests__/SampleModel/mocking";
import FromSampleDialog from "../FromSampleDialog";
import SearchContext from "../../../stores/contexts/Search";
import { mockFactory } from "../../../stores/definitions/__tests__/Factory/mocking";
import Search from "../../../stores/models/Search";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";

jest.mock("../../../common/InvApiService", () => () => ({}));
jest.mock("../../../stores/stores/RootStore", () => () => ({
  searchStore: {
    search: null,
  },
  uiStore: {
    isVerySmall: false,
    addAlert: () => {},
  },
}));

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("FromSampleDialog", () => {
  describe("New template's name is valid.", () => {
    test("A name that is too long displays an error.", async () => {
      const name =
        "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
      const search = new Search({
        factory: mockFactory(),
      });
      const sample = makeMockSample();
      jest.spyOn(sample, "fetchAdditionalInfo").mockImplementation(() => {});
      render(
        <ThemeProvider theme={materialTheme}>
          <SearchContext.Provider
            value={{
              search,
              differentSearchForSettingActiveResult: search,
            }}
          >
            <FromSampleDialog
              open={true}
              onCancel={() => {}}
              onSubmit={() => {}}
              sample={sample}
            />
          </SearchContext.Provider>
        </ThemeProvider>
      );

      const input = within(screen.getByTestId("FromSampleNameField")).getByRole(
        "textbox"
      );
      fireEvent.input(input, { target: { value: name } });

      await waitFor(() => {
        expect(screen.getByTestId("FromSampleDialog")).toHaveTextContent(
          "Template name is too long."
        );
      });
      /*
       * This passes but Flow does not like these data-test-ids
       */
    });
  });
});
