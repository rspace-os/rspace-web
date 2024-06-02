/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen, act, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom";
import { makeMockRootStore } from "../../../../../stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "../../../../../stores/stores-context";
import PreviewAction from "../PreviewAction";
import { mockAttachment } from "../../../../../stores/definitions/__tests__/Attachment/mocking";

jest.mock("../../../../../common/InvApiService", () => {});
jest.mock("../../../../../stores/stores/RootStore", () => () => ({}));

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("PreviewAction", () => {
  test("An error should be shown if the image cannot be fetched.", async () => {
    const rootStore = makeMockRootStore({
      uiStore: {
        addAlert: () => {},
      },
    });
    const addAlertMock = jest
      .spyOn(rootStore.uiStore, "addAlert")
      .mockImplementation(() => {});

    const attachment = mockAttachment({
      setImageLink: () => Promise.reject({ message: "foo" }),
    });

    render(
      <storesContext.Provider value={rootStore}>
        <PreviewAction attachment={attachment} />
      </storesContext.Provider>
    );
    act(() => {
      screen.getByRole("button", { name: "Preview file as image" }).click();
    });

    await waitFor(() => {
      expect(addAlertMock).toHaveBeenCalledWith(
        expect.objectContaining({ message: "foo", variant: "error" })
      );
    });
  });
});
