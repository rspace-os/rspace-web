/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React from "react";
import { render, cleanup } from "@testing-library/react";
import "@testing-library/jest-dom";
import { makeMockContainer } from "../../../../stores/models/__tests__/ContainerModel/mocking";
import Search from "../../../../stores/models/Search";
import SearchContext from "../../../../stores/contexts/Search";
import MoveInstructions from "../MoveInstructions";
import { mockFactory } from "../../../../stores/definitions/__tests__/Factory/mocking";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

jest.mock("../../../../common/InvApiService", () => ({
  query: jest.fn(() => {
    return Promise.resolve({
      data: {
        id: 1,
        globalId: "IC1",
        attachments: [],
        _links: [],
        tags: null,
        permittedActions: ["UPDATE"],
        cType: "IMAGE",
        locations: [],
        barcodes: [],
        locationsCount: 0,
        contentSummary: {
          totalCount: 0,
          subSampleCount: 0,
          containerCount: 0,
        },
      },
    });
  }),
}));

describe("MoveInstructions", () => {
  test("Visual container without locations image", async () => {
    const search = new Search({
      factory: mockFactory(),
    });

    const scopedResult = makeMockContainer({});
    jest.spyOn(scopedResult, "fetchImage").mockImplementation((name) => {
      if (name === "locationsImage") scopedResult.locationsImage = null;
      return Promise.resolve(null);
    });
    await scopedResult.fetchAdditionalInfo();

    const { container } = render(
      <SearchContext.Provider
        value={{
          search,
          differentSearchForSettingActiveResult: search,
          scopedResult,
        }}
      >
        <MoveInstructions />
      </SearchContext.Provider>
    );

    expect(container).toHaveTextContent(
      "This visual container doesn't yet have a locations image onto which locations can be marked. Please edit first."
    );
  });

  test("Visual container with locations image but without locations", async () => {
    const search = new Search({
      factory: mockFactory(),
    });

    const scopedResult = makeMockContainer({});
    jest.spyOn(scopedResult, "fetchImage").mockImplementation((name) => {
      if (name === "locationsImage") scopedResult.locationsImage = "foo";
      return Promise.resolve("foo");
    });

    /*
     * fetchAdditionalInfo is called so that _link gets converted to locationsImage
     */
    await scopedResult.fetchAdditionalInfo();

    const { container } = render(
      <SearchContext.Provider
        value={{
          search,
          differentSearchForSettingActiveResult: search,
          scopedResult,
        }}
      >
        <MoveInstructions />
      </SearchContext.Provider>
    );

    expect(container).toHaveTextContent(
      "This visual container doesn't yet have any marked locations into which items can be placed. Please edit first."
    );
  });
});
