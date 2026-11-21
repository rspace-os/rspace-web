import { describe, expect, test, vi } from "vitest";
import type { AxiosRequestConfig, AxiosResponse } from "@/common/axios";
import InvApiService from "../../../../common/InvApiService";
import ContainerModel from "../../ContainerModel";
import AlwaysNewFactory from "../../Factory/AlwaysNewFactory";
import { containerAttrs, makeMockContainer } from "./mocking";

const childLocation = {
  id: 2,
  coordX: 1,
  coordY: 1,
  content: containerAttrs({ id: 2, globalId: "IC2" }),
};

vi.mock("../../../../common/InvApiService", () => ({
  default: {
    query: vi.fn(() => {}),
  },
}));
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    peopleStore: { currentUser: null },
    uiStore: {
      addAlert: () => {},
      setPageNavigationConfirmation: () => {},
      setDirty: () => {},
    },
  }),
}));
describe("fetchAdditionalInfo", () => {
  test("Subsequent invocations await the completion of prior in-progress invocations.", async () => {
    const container = makeMockContainer();
    vi.spyOn(InvApiService, "query").mockImplementation(() =>
      Promise.resolve({
        data: containerAttrs(),
        status: 200,
        statusText: "OK",
        headers: {},
        config: {} as AxiosRequestConfig,
      } as AxiosResponse),
    );
    let firstCallDone = false;
    await container.fetchAdditionalInfo().then(() => {
      firstCallDone = true;
    });
    await container.fetchAdditionalInfo();
    /*
     * The second call should not have resolved until the first resolved and
     * set firstCallDone to true
     */
    expect(firstCallDone).toBe(true);
  });
});

test("LIST opens lean and a tree request still loads ordered locations while lean is pending", async () => {
  const container = new AlwaysNewFactory().newRecord({ ...containerAttrs(), globalId: "IC1", locations: null });
  if (!(container instanceof ContainerModel)) throw new Error("Expected a LIST container");
  const lean = Promise.withResolvers<AxiosResponse>();
  const response = (data: object) =>
    ({
      data,
      status: 200,
      statusText: "OK",
      headers: {},
      config: {} as AxiosRequestConfig,
    }) as AxiosResponse;
  const query = vi.spyOn(InvApiService, "query").mockClear();
  query.mockImplementation((_endpoint, params) =>
    params?.get("includeContent") === "true"
      ? Promise.resolve(
          response(
            containerAttrs({
              locations: [3, 2].map((id, index) => ({
                id,
                coordX: index + 1,
                coordY: 1,
                content: containerAttrs({ id, globalId: id === 3 ? "IC3" : "IC2" }),
              })),
            }),
          ),
        )
      : lean.promise,
  );

  const opening = container.fetchAdditionalInfo();
  expect(query.mock.calls.map(([, params]) => params?.get("includeContent"))).toEqual(["false"]);
  await container.loadChildren();
  expect(query.mock.calls.map(([, params]) => params?.get("includeContent"))).toEqual(["false", "true"]);
  expect(container.children.map((child) => child.globalId)).toEqual(["IC3", "IC2"]);

  lean.resolve(response({ ...containerAttrs(), locations: null }));
  await opening;
  expect(container.initializedLocations).toBe(true);
  expect(container.children.map((child) => child.globalId)).toEqual(["IC3", "IC2"]);
});

test("a restricted response clears previously loaded locations and tree results", async () => {
  const container = new AlwaysNewFactory().newRecord({ ...containerAttrs(), globalId: "IC1", locations: null });
  if (!(container instanceof ContainerModel)) throw new Error("Expected a LIST container");
  const query = vi.spyOn(InvApiService, "query");
  query.mockResolvedValueOnce({ data: containerAttrs({ locations: [childLocation] }) } as AxiosResponse);
  await container.loadChildren();
  expect(container.children.map((child) => child.globalId)).toEqual(["IC2"]);
  query.mockResolvedValueOnce({
    data: { ...containerAttrs({ permittedActions: ["LIMITED_READ"] }), locations: null },
  } as AxiosResponse);

  await container.fetchAdditionalInfo();

  expect(container.locations).toEqual([]);
  expect(container.hasSelectedLocation).toBe(false);
  expect(container.children.map((child) => child.globalId)).toEqual([]);
  expect(container.contentSearch.cacheFetcher.results.map((child) => child.globalId)).toEqual([]);
});

test("refreshing an expanded LIST tree removes a child moved out of the container", async () => {
  const container = new AlwaysNewFactory().newRecord({ ...containerAttrs(), globalId: "IC1", locations: null });
  if (!(container instanceof ContainerModel)) throw new Error("Expected a LIST container");
  const query = vi.spyOn(InvApiService, "query").mockClear();
  let movedOut = false;
  query.mockImplementation((_resource, params) =>
    Promise.resolve({
      data: {
        ...containerAttrs(),
        locations: params.get("includeContent") === "true" ? (movedOut ? [] : [childLocation]) : null,
      },
    } as AxiosResponse),
  );
  await container.loadChildren();
  expect(container.children.map((child) => child.globalId)).toEqual(["IC2"]);

  movedOut = true;
  await container.fetchAdditionalInfo();

  expect(container.children.map((child) => child.globalId)).toEqual([]);
  expect(container.contentSearch.cacheFetcher.results.map((child) => child.globalId)).toEqual([]);
  expect(query.mock.calls.map(([, params]) => params.get("includeContent"))).toEqual(["true", "true"]);
});
