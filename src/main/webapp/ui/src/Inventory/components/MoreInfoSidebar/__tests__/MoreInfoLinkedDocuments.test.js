/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { cleanup, screen, fireEvent } from "@testing-library/react";
import "@testing-library/jest-dom";
import LinkedDocuments from "../LinkedDocuments";
import { mockFactory } from "../../../../stores/definitions/__tests__/Factory/mocking";
import InvApiService from "../../../../common/InvApiService";
import { newDocument } from "../../../../stores/models/Document";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import { render, within } from "../../../../__tests__/customQueries";

jest.mock("../../../../common/InvApiService", () => ({
  get: () => ({}),
}));

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("LinkedDocuments", () => {
  test("Assert that correct API endpoint is called with Global ID", async () => {
    const spy = jest
      .spyOn(InvApiService, "get")
      .mockImplementation(() => Promise.reject(new Error("An error")));
    render(<LinkedDocuments factory={mockFactory()} globalId="IC1" />);
    fireEvent.click(
      screen.getByRole("button", { name: "Show Linked Documents" })
    );
    expect(await screen.findByText("An error")).toBeVisible();
    expect(spy).toHaveBeenCalledWith("listOfMaterials/forInventoryItem/IC1");
  });

  test("When there is an error loading the data, an alert should be shown.", async () => {
    jest
      .spyOn(InvApiService, "get")
      .mockImplementation(() => Promise.reject(new Error("An error")));
    render(<LinkedDocuments factory={mockFactory()} globalId="IC1" />);
    fireEvent.click(
      screen.getByRole("button", { name: "Show Linked Documents" })
    );
    expect(await screen.findByRole("alert")).toHaveTextContent("An error");
  });

  test("Two different documents should render as two table rows", async () => {
    jest.spyOn(InvApiService, "get").mockImplementation(() => {
      return Promise.resolve({
        data: [
          { elnDocument: { globalId: "SD1", id: 1, name: "Foo", owner: null } },
          { elnDocument: { globalId: "SD2", id: 2, name: "Bar", owner: null } },
        ],
      });
    });
    render(
      <ThemeProvider theme={materialTheme}>
        <LinkedDocuments
          factory={mockFactory({
            newDocument: (x) => newDocument(x),
          })}
          globalId="IC1"
        />
      </ThemeProvider>
    );
    fireEvent.click(
      screen.getByRole("button", { name: "Show Linked Documents" })
    );
    expect(
      within(await screen.findByRole("table")).getAllByRole("row")
    ).toHaveLength(3);

    expect(
      await within(screen.getByRole("table")).findTableCell({
        columnHeading: "Name",
        rowIndex: 0,
      })
    ).toHaveTextContent("Foo");
    expect(
      await within(screen.getByRole("table")).findTableCell({
        columnHeading: "Name",
        rowIndex: 1,
      })
    ).toHaveTextContent("Bar");
  });

  test("Two of the same document should render as one table row", async () => {
    jest.spyOn(InvApiService, "get").mockImplementation(() => {
      return Promise.resolve({
        data: [
          { elnDocument: { globalId: "SD1", id: 1, name: "Foo", owner: null } },
          { elnDocument: { globalId: "SD1", id: 1, name: "Foo", owner: null } },
        ],
      });
    });
    render(
      <ThemeProvider theme={materialTheme}>
        <LinkedDocuments
          factory={mockFactory({
            newDocument: (x) => newDocument(x),
          })}
          globalId="IC1"
        />
      </ThemeProvider>
    );
    fireEvent.click(
      screen.getByRole("button", { name: "Show Linked Documents" })
    );
    const rows = within(await screen.findByRole("table")).getAllByRole("row");
    expect(rows).toHaveLength(2);
    expect(
      await within(screen.getByRole("table")).findTableCell({
        columnHeading: "Name",
        rowIndex: 0,
      })
    ).toHaveTextContent("Foo");
  });

  test("Opening the dialog twice should trigger two network calls", async () => {
    const spy = jest.spyOn(InvApiService, "get").mockImplementation(() => {
      return Promise.reject(new Error("An error"));
    });
    render(<LinkedDocuments factory={mockFactory()} globalId="IC1" />);
    fireEvent.click(
      screen.getByRole("button", { name: "Show Linked Documents" })
    );
    expect(await screen.findByRole("button", { name: "Close" })).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Close" }));
    expect(
      await screen.findByRole("button", { name: "Show Linked Documents" })
    ).toBeVisible();
    fireEvent.click(
      screen.getByRole("button", { name: "Show Linked Documents" })
    );
    expect(await screen.findByText("An error")).toBeVisible();
    expect(spy).toHaveBeenCalledTimes(2);
  });
});
