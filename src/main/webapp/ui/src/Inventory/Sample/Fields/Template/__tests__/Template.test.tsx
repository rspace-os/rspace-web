import {
  describe,
  expect,
  beforeEach,
  it,
  vi,
} from "vitest";
import "../../../../../../__mocks__/matchMedia";
import React from "react";
import {
  render,
  waitFor,
  screen,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import {
  makeMockTemplate,
  templateAttrs,
} from "../../../../../stores/models/__tests__/TemplateModel/mocking";
import { fieldAttrs } from "../../../../../stores/models/__tests__/FieldModel/mocking";
import { makeMockSample } from "../../../../../stores/models/__tests__/SampleModel/mocking";
import Template from "../Template";
import { makeMockRootStore } from "../../../../../stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "../../../../../stores/stores-context";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../../theme";
import ApiService from "../../../../../common/InvApiService";
import { sleep } from "../../../../../util/Util";
import "__mocks__/resizeObserver";
import userEvent from "@testing-library/user-event";
import { type AxiosResponse } from "@/common/axios";

vi.mock("../../../../../common/InvApiService", () => ({
  default: {
    query: vi.fn(() => {}),
    get: vi.fn(() => {}),
  },
}));
vi.mock("../../../../../stores/stores/RootStore", () => ({
  default: () => ({
    searchStore: {
      search: null,
      savedSearches: [],
    },
    uiStore: {
      addAlert: () => {},
      setVisiblePanel: () => {},
    },
    unitStore: {
      getUnit: () => ({ label: "ml" }),
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
  }),
}));
vi.mock("../../../../Container/Content/ImageView/PreviewImage", () => ({
  default: vi.fn(() => <></>),
}));

// Mock fetch
window.fetch = vi.fn().mockImplementation(() =>
  Promise.resolve({
    status: 200,
    ok: true,
    json: () => Promise.resolve({}),
  })
);

beforeEach(() => {
  vi.clearAllMocks();
});


describe("Template", () => {
  describe("When the sample is deleted, Template field should", () => {
    it("not allow the user to update to the latest version of the template.", () => {
      const oldVersionOfTemplate = makeMockTemplate({
        historicalVersion: true,
      });
      vi
        .spyOn(oldVersionOfTemplate, "getLatest")
        .mockImplementation(() => {});
      const sample = makeMockSample({
        deleted: true,
      });
      sample.template = oldVersionOfTemplate;

      const rootStore = makeMockRootStore({
        searchStore: {
          activeResult: sample,
          getBaskets: () => {},
        },
        uiStore: {
          setVisiblePanel: () => {},
        },
      });

      render(
        <ThemeProvider theme={materialTheme}>
          <storesContext.Provider value={rootStore}>
            <Template />
          </storesContext.Provider>
        </ThemeProvider>
      );

      expect(
        screen.getByRole("button", {
          name: /Update/,
        })
      ).toBeDisabled();
    });
  });
  describe("When a template is chosen", () => {
    it("all of the template's fields should be copied to the sample.", async () => {
      const user = userEvent.setup();
      vi.spyOn(ApiService, "query").mockImplementation((endpoint, params) => {
        if (params.get("resultType") === "TEMPLATE") {
          return Promise.resolve({
            data: {
              templates: [templateAttrs()],
              totalHits: 1,
            },
            status: 200,
            statusText: "OK",
            headers: {},
            config: {},
          } as AxiosResponse);
        }
        return Promise.resolve({
          data: undefined,
          status: 200,
          statusText: "OK",
          headers: {},
          config: {},
        } as AxiosResponse);
      });
      vi.spyOn(ApiService, "get").mockImplementation(async (endpoint) => {
        if (endpoint === "sampleTemplates") {
          await sleep(500);
          return {
            data: {
              ...templateAttrs(),
              fields: [
                fieldAttrs({
                  type: "number",
                  content: "1",
                }),
              ],
            },
            status: 200,
            statusText: "OK",
            headers: {},
            config: {},
          } as AxiosResponse;
        }
        return {
          data: undefined,
          status: 200,
          statusText: "OK",
          headers: {},
          config: {},
        } as AxiosResponse;
      });

      const sample = makeMockSample({
        id: null,
        globalId: null,
      });
      vi.spyOn(sample, "checkLock").mockImplementation(() => {
        return Promise.resolve({
          status: "LOCKED_OK",
          remainingTimeInSeconds: 100,
          lockOwner: {
            firstName: "foo",
            lastName: "foo",
            username: "foo",
          },
        });
      });
      vi.spyOn(sample, "handleLockExpiry").mockImplementation(() => {});

      const rootStore = makeMockRootStore({
        searchStore: {
          activeResult: sample,
          savedSearches: [],
          savedBaskets: [],
          getBaskets: () => {},
        },
        uiStore: {
          setVisiblePanel: () => {},
        },
      });

      render(
        <ThemeProvider theme={materialTheme}>
          <storesContext.Provider value={rootStore}>
            <Template />
          </storesContext.Provider>
        </ThemeProvider>
      );
      await waitFor(() => {
        expect(screen.getByText("A template")).toBeVisible();
      });
      await user.click(screen.getByText("A template"));

      await waitFor(() => {
        expect(sample.fields.length).toBe(1);
      });
    });
  });
});

