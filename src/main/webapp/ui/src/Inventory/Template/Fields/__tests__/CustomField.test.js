/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "../../../../../__mocks__/matchMedia.js";
import React from "react";
import { render, cleanup, screen, act } from "@testing-library/react";
import "@testing-library/jest-dom";
import { makeMockField } from "../../../../stores/models/__tests__/FieldModel/mocking";
import CustomField from "../CustomField";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import userEvent from "@testing-library/user-event";

beforeEach(() => {
  jest.clearAllMocks();
});

jest.mock("../../../../common/InvApiService", () => {});
jest.mock("../../../../stores/stores/RootStore", () => () => ({}));

afterEach(cleanup);

describe("CustomField", () => {
  describe("Should be able to delete the field", () => {
    test("Keep field in existing samples", async () => {
      const user = userEvent.setup();
      const field = makeMockField({
        type: "choice",
        definition: {
          options: ["foo", "bar"],
        },
        selectedOptions: ["foo"],
      });
      const onRemove = jest.fn<[?boolean], void>();

      render(
        <ThemeProvider theme={materialTheme}>
          <CustomField
            field={field}
            i={0}
            editable={true}
            onErrorStateChange={() => {}}
            onRemove={onRemove}
            forceColumnLayout={false}
            onMove={() => {}}
          />
        </ThemeProvider>
      );

      await user.click(screen.getByRole("button", { name: "Delete field" }));
      await user.click(
        screen.getByRole("menuitem", { name: "Keep field in existing samples" })
      );
      expect(onRemove).toHaveBeenCalledWith(false);
    });
    test("Remove field in existing samples", async () => {
      const user = userEvent.setup();
      const field = makeMockField({
        type: "choice",
        definition: {
          options: ["foo", "bar"],
        },
        selectedOptions: ["foo"],
      });
      const onRemove = jest.fn<[?boolean], void>();

      render(
        <ThemeProvider theme={materialTheme}>
          <CustomField
            field={field}
            i={0}
            editable={true}
            onErrorStateChange={() => {}}
            onRemove={onRemove}
            forceColumnLayout={false}
            onMove={() => {}}
          />
        </ThemeProvider>
      );

      await user.click(screen.getByRole("button", { name: "Delete field" }));
      await user.click(
        screen.getByRole("menuitem", {
          name: "Remove field from existing samples",
        })
      );
      expect(onRemove).toHaveBeenCalledWith(true);
    });
  });
});
