/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "../../../../__mocks__/matchMedia";
import React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import userEvent from "@testing-library/user-event";
import FieldmarkImportDialog from "../FieldmarkImportDialog";
import mockFieldmarkNotebooksJson from "./fieldmark_notebooks.json";
import mockFieldmarkImportJson from "./fieldmark_import.json";
import Alerts from "../../../components/Alerts/Alerts";
import materialTheme from "../../../theme";
import { ThemeProvider } from "@mui/material/styles";

jest.mock("../../../stores/stores/RootStore", () => () => ({
  uiStore: {
    removeAlert: () => {},
    addAlert: () => {},
  },
  authStore: {
    isAuthenticated: true,
    isSynchronizing: false,
  },
}));

jest.mock("../../../common/InvApiService", () => ({
  get: (resource) => {
    if (resource === "/fieldmark/notebooks")
      return Promise.resolve({ data: mockFieldmarkNotebooksJson });
    if (
      resource === "/fieldmark/import/notebook/1726126204618-rspace-igsn-demo"
    )
      return Promise.resolve({ data: mockFieldmarkImportJson });
    throw new Error("Unknown resource");
  },
}));

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("FieldmarkImportDialog", () => {
  test("The success toast should link to the new container.", async () => {
    const user = userEvent.setup();

    render(
      <ThemeProvider theme={materialTheme}>
        <Alerts>
          <FieldmarkImportDialog open={true} onClose={() => {}} />
        </Alerts>
      </ThemeProvider>
    );

    await user.click(await screen.findByRole("radio"));
    await user.click(screen.getByRole("button", { name: /import/i }));

    await screen.findByText("Successfully imported notebook.");

    await user.click(
      screen.getByRole("button", { name: "1 sub-messages. Toggle to show" })
    );

    expect(
      screen.getByText("Container RSpace IGSN Demo - 2024-11-14 10:32:03")
    ).toBeInTheDocument();

    expect(screen.getByRole("link", { name: "IC98304" })).toHaveAttribute(
      "href",
      "/globalId/IC98304"
    );
  });
});
