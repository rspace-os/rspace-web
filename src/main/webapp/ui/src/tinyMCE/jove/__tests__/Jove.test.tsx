import { test, describe, expect, beforeEach, vi } from 'vitest';
import Jove from "../Jove";
import React from "react";
import axios from "@/common/axios";
import {
  render,
  screen,
  act,
  within,
} from "@testing-library/react";
import MockAdapter from "axios-mock-adapter";
import JoveSearchResult from "./joveSearchResult.json";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
const mockAxios = new MockAdapter(axios);
const localStorageMock = {
  getItem: vi.fn(),
  setItem: vi.fn(),
};
const rsMock = {
  trackEvent: vi.fn(),
};
Object.defineProperty(window, "localStorage", { value: localStorageMock });
Object.defineProperty(window, "RS", { value: rsMock });
beforeEach(() => {
  act(() => {
    mockAxios
      .onPost("/apps/jove/search", {
        queryString: "",
        pageNumber: 0,
        pageSize: 20,
      })
      .reply(200, JoveSearchResult);
  });
});
describe("Renders page with jove data", () => {
  test("displays jove table headers", async () => {
    render(<Jove />);
    await screen.findByText("Title");
  });
  test("displays jove search bar", async () => {
    render(<Jove />);
    await screen.findByLabelText("Search");
  });
  test('displays table headers for jove search results"', async () => {
    render(<Jove />);
    await screen.findByText("Thumbnail");
    await screen.findByText("Title");
    await screen.findByText("Section");
  });
  test("displays jove search results", async () => {
    render(<Jove />);
    await screen.findByText("Title");
    await screen.findByText(
      "Induction and Validation of Cellular Senescence in Primary Human Cells"
    );
    expect(screen.getByTestId("title0")).toHaveTextContent(
      "Induction and Validation of Cellular Senescence in Primary Human Cells"
    );
    expect(
      within(screen.getByTestId("thumbnail0")).getByRole("img")
    ).toHaveAttribute(
      "src",
      "https://cloudfront.jove.com/files/thumbs/57782_t.png"
    );
    expect(screen.getByTestId("section0")).toHaveTextContent(
      "Developmental Biology"
    );
  });
  test("displays error message if 404 returned", async () => {
    const restoreConsole = silenceConsole(["log"], [/./]);
    mockAxios
      .onPost("/apps/jove/search", {
        queryString: "",
        pageNumber: 0,
        pageSize: 20,
      })
      .reply(404, []);
    render(<Jove />);
    await screen.findByText("Error");
    expect(
      screen.getByText("Unable to retrieve any relevant results.")
    ).toBeInTheDocument();
    restoreConsole();
  });
  test("displays error message if 500 returned", async () => {
    const restoreConsole = silenceConsole(["log"], [/./]);
    mockAxios
      .onPost("/apps/jove/search", {
        queryString: "",
        pageNumber: 0,
        pageSize: 20,
      })
      .reply(500, []);
    render(<Jove />);
    await screen.findByText("Error");
    expect(
      screen.getByText("Unknown issue, please attempt to relogin to RSpace.")
    ).toBeInTheDocument();
    restoreConsole();
  });
});
