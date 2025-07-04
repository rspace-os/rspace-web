/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "../../../../__mocks__/matchMedia";
import React from "react";
import { render, cleanup, screen, act, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom";
import userEvent from "@testing-library/user-event";
import FieldmarkImportDialog from "../FieldmarkImportDialog";
import mockFieldmarkNotebooksJson from "./fieldmark_notebooks.json";
import mockFieldmarkImportJson from "./fieldmark_import.json";
import Alerts from "../../../components/Alerts/Alerts";
import materialTheme from "../../../theme";
import { ThemeProvider } from "@mui/material/styles";
import { axe, toHaveNoViolations } from "jest-axe";

expect.extend(toHaveNoViolations);

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
  get: (resource: string) => {
    if (resource === "/fieldmark/notebooks")
      return Promise.resolve({ data: mockFieldmarkNotebooksJson });
    throw new Error("Unknown resource");
  },
  post: (resource: string) => {
    if (resource === "/import/fieldmark/notebook")
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

    const radio = await screen.findByRole("radio");
    await act(async () => {
      await user.click(radio);
    });
    await act(async () => {
      await user.click(screen.getByRole("button", { name: /import/i }));
    });

    await screen.findByText("Successfully imported notebook.");

    await act(async () => {
      await user.click(
        screen.getByRole("button", { name: "1 sub-messages. Toggle to show" })
      );
    });

    expect(
      screen.getByText("Container RSpace IGSN Demo - 2024-11-14 10:32:03")
    ).toBeInTheDocument();

    expect(screen.getByRole("link", { name: "IC98304" })).toHaveAttribute(
      "href",
      "/globalId/IC98304"
    );
  });
  test("Should have no axe violations.", async () => {
    const { baseElement } = render(
      <ThemeProvider theme={materialTheme}>
        <Alerts>
          <FieldmarkImportDialog open={true} onClose={() => {}} />
        </Alerts>
      </ThemeProvider>
    );

    await waitFor(() => {
      expect(screen.getByRole("radio")).toBeInTheDocument();
    });

    expect(await axe(baseElement)).toHaveNoViolations();
  });
});
