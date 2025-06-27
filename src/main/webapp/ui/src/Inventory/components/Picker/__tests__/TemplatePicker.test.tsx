/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom";
import InvApiService from "../../../../common/InvApiService";
import TemplatePicker from "../TemplatePicker";
import materialTheme from "../../../../theme";
import { ThemeProvider } from "@mui/material/styles";
import { templateAttrs } from "../../../../stores/models/__tests__/TemplateModel/mocking";
import { makeMockRootStore } from "../../../../stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "../../../../stores/stores-context";
import "__mocks__/resizeObserver";
import "../../../../../__mocks__/matchMedia";
import userEvent from "@testing-library/user-event";
import { type AxiosResponse } from "@/common/axios";

jest.mock("../../../../common/InvApiService", () => ({
  get: () => ({}),
  query: () => ({}),
}));
jest.mock("../../../../stores/stores/RootStore", () => () => ({
  searchStore: {
    savedSearches: [{ name: "Dummy saved search", query: "foo" }],
  },
  uiStore: {
    addAlert: () => {},
  },
  peopleStore: {
    currentUser: {
      id: 1,
      username: "jb",
      firstName: "joe",
      lastName: "bloggs",
      email: null,
      workbenchId: 1,
      _links: [],
    },
  },
}));

(window.fetch as jest.Mock) = jest.fn(() =>
  Promise.resolve({
    status: 200,
    ok: true,
    json: () => Promise.resolve(),
    headers: new Headers(),
    redirected: false,
    statusText: "OK",
    type: "basic",
    url: "",
    clone: () => new Response(),
    body: null,
    bodyUsed: false,
    arrayBuffer: () => Promise.resolve(new ArrayBuffer(0)),
    blob: () => Promise.resolve(new Blob()),
    formData: () => Promise.resolve(new FormData()),
    text: () => Promise.resolve(""),
  } as Response)
);

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("TemplatePicker", () => {
  describe("Should support saved searches", () => {
    test("Tapping a saved search should change the templates listed", async () => {
      const user = userEvent.setup();
      const rootStore = makeMockRootStore({
        searchStore: {
          savedSearches: [{ name: "Dummy saved search", query: "foo" }],
          savedBaskets: [],
          getBaskets: () => {},
        },
      });

      jest.spyOn(InvApiService, "query").mockImplementation((endpoint) => {
        if (endpoint === "sampleTemplates")
          return Promise.resolve({
            data: {
              templates: [
                templateAttrs({ name: "foo", id: 1, globalId: "IT1" }),
                templateAttrs({ name: "bar", id: 2, globalId: "IT2" }),
              ],
              totalHits: 2,
            },
            status: 200,
            statusText: "OK",
            headers: {},
            config: {},
          } as AxiosResponse);
        if (endpoint === "search")
          return Promise.resolve({
            data: {
              records: [templateAttrs({ name: "foo", id: 1, globalId: "IT1" })],
              totalHits: 1,
            },
            status: 200,
            statusText: "OK",
            headers: {},
            config: {},
          } as AxiosResponse);
        throw new Error(`Endpoint not supported: ${endpoint}`);
      });

      render(
        <ThemeProvider theme={materialTheme}>
          <storesContext.Provider value={rootStore}>
            <TemplatePicker setTemplate={() => {}} disabled={false} />
          </storesContext.Provider>
        </ThemeProvider>
      );

      await waitFor(() => {
        expect(screen.getByRole("table")).toHaveTextContent("foo");
      });
      expect(screen.getByRole("table")).toHaveTextContent("bar");

      await user.click(screen.getByRole("button", { name: "Saved Searches" }));
      await user.click(
        screen.getByRole("menuitem", { name: /^Dummy saved search/ })
      );

      await waitFor(() => {
        expect(screen.getByRole("table")).toHaveTextContent("foo");
      });
      expect(screen.getByRole("table")).not.toHaveTextContent("bar");
    });
  });
});
