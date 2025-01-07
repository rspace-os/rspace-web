/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen, act } from "@testing-library/react";
import "@testing-library/jest-dom";
import userEvent from "@testing-library/user-event";
import RsSet from "../../../util/set";
import { useGalleryActions, rootDestination } from "../useGalleryActions";
import { dummyId, Description, LocalGalleryFile } from "../useGalleryListing";
import Alerts from "../../../components/Alerts/Alerts";
import MockAdapter from "axios-mock-adapter";
import * as axios from "axios";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("useGalleryActions", () => {
  describe("duplicateFiles", () => {
    function TestingDuplicateComponent() {
      const { duplicateFiles } = useGalleryActions();
      return (
        <button
          onClick={() => {
            void duplicateFiles(
              new RsSet([
                new LocalGalleryFile({
                  id: dummyId(),
                  globalId: "GF1",
                  name: "Foo",
                  extension: "txt",
                  creationDate: new Date(),
                  modificationDate: new Date(),
                  type: "image",
                  ownerName: "Joe Bloggs",
                  description: new Description({ key: "empty" }),
                  version: 1,
                  size: 1024,
                  path: [],
                  setPath: () => {},
                  thumbnailId: null,
                  gallerySection: "Images",
                  token: "",
                }),
              ])
            );
          }}
        >
          click me
        </button>
      );
    }

    test("errorMessages should result in an error alert", async () => {
      const user = userEvent.setup();
      const mockAxios = new MockAdapter(axios);

      mockAxios.onPost("/gallery/ajax/copyGalleries").reply(200, {
        data: null,
        error: {
          errorMessages: ["This is a test error message."],
        },
        success: false,
        errorMsg: {
          errorMessages: ["This is a test error message."],
        },
      });

      render(
        <Alerts>
          <TestingDuplicateComponent />
        </Alerts>
      );

      await act(async () => {
        await user.click(screen.getByRole("button"));
      });

      const toast = await screen.findByRole("alert");

      expect(toast).toHaveTextContent("This is a test error message.");
    });
    test("exceptionMessage should result in an error alert", async () => {
      const user = userEvent.setup();
      const mockAxios = new MockAdapter(axios);

      mockAxios.onPost("/gallery/ajax/copyGalleries").reply(200, {
        exceptionMessage: "This is a test error message.",
        tstamp: "2024-12-10T15:12:57.591Z",
        errorId: "dvnaxNHumB",
      });

      render(
        <Alerts>
          <TestingDuplicateComponent />
        </Alerts>
      );

      await act(async () => {
        await user.click(screen.getByRole("button"));
      });

      const toast = await screen.findByRole("alert");

      expect(toast).toHaveTextContent("This is a test error message.");
    });
  });
  describe("deleteFiles", () => {
    function TestingDeleteComponent() {
      const { deleteFiles } = useGalleryActions();
      return (
        <button
          onClick={() => {
            void deleteFiles(
              new RsSet([
                new LocalGalleryFile({
                  id: dummyId(),
                  globalId: "GF1",
                  name: "Foo",
                  extension: "txt",
                  creationDate: new Date(),
                  modificationDate: new Date(),
                  type: "image",
                  ownerName: "Joe Bloggs",
                  description: new Description({ key: "empty" }),
                  version: 1,
                  size: 1024,
                  path: [],
                  setPath: () => {},
                  thumbnailId: null,
                  gallerySection: "Images",
                  token: "",
                }),
              ])
            );
          }}
        >
          click me
        </button>
      );
    }

    test("errorMessages should result in an error alert", async () => {
      const user = userEvent.setup();
      const mockAxios = new MockAdapter(axios);

      mockAxios.onPost("/gallery/ajax/deleteElementFromGallery").reply(200, {
        data: null,
        error: {
          errorMessages: ["This is a test error message."],
        },
        success: false,
        errorMsg: {
          errorMessages: ["This is a test error message."],
        },
      });

      render(
        <Alerts>
          <TestingDeleteComponent />
        </Alerts>
      );

      await act(async () => {
        await user.click(screen.getByRole("button"));
      });

      const toast = await screen.findByRole("alert");

      expect(toast).toHaveTextContent("This is a test error message.");
    });
    test("exceptionMessage should result in an error alert", async () => {
      const user = userEvent.setup();
      const mockAxios = new MockAdapter(axios);

      mockAxios.onPost("/gallery/ajax/deleteElementFromGallery").reply(200, {
        exceptionMessage: "This is a test error message.",
        tstamp: "2024-12-10T15:12:57.591Z",
        errorId: "dvnaxNHumB",
      });

      render(
        <Alerts>
          <TestingDeleteComponent />
        </Alerts>
      );

      await act(async () => {
        await user.click(screen.getByRole("button"));
      });

      const toast = await screen.findByRole("alert");

      expect(toast).toHaveTextContent("This is a test error message.");
    });
  });
  describe("moveFiles", () => {
    function TestingMoveComponent() {
      const { moveFiles } = useGalleryActions();
      return (
        <button
          onClick={() => {
            void moveFiles(
              new RsSet([
                new LocalGalleryFile({
                  id: dummyId(),
                  globalId: "GF1",
                  name: "Foo",
                  extension: "txt",
                  creationDate: new Date(),
                  modificationDate: new Date(),
                  type: "image",
                  ownerName: "Joe Bloggs",
                  description: new Description({ key: "empty" }),
                  version: 1,
                  size: 1024,
                  path: [],
                  setPath: () => {},
                  thumbnailId: null,
                  gallerySection: "Images",
                  token: "",
                }),
              ])
            ).to({
              destination: rootDestination(),
              section: "Images",
            });
          }}
        >
          click me
        </button>
      );
    }

    test("errorMessages should result in an error alert", async () => {
      const user = userEvent.setup();
      const mockAxios = new MockAdapter(axios);

      mockAxios.onPost("/gallery/ajax/moveGalleriesElements").reply(200, {
        data: null,
        error: {
          errorMessages: ["This is a test error message."],
        },
        success: false,
        errorMsg: {
          errorMessages: ["This is a test error message."],
        },
      });

      render(
        <Alerts>
          <TestingMoveComponent />
        </Alerts>
      );

      await act(async () => {
        await user.click(screen.getByRole("button"));
      });

      const toast = await screen.findByRole("alert");

      expect(toast).toHaveTextContent("This is a test error message.");
    });
    test("exceptionMessage should result in an error alert", async () => {
      const user = userEvent.setup();
      const mockAxios = new MockAdapter(axios);

      mockAxios.onPost("/gallery/ajax/moveGalleriesElements").reply(200, {
        exceptionMessage: "This is a test error message.",
        tstamp: "2024-12-10T15:12:57.591Z",
        errorId: "dvnaxNHumB",
      });

      render(
        <Alerts>
          <TestingMoveComponent />
        </Alerts>
      );

      await act(async () => {
        await user.click(screen.getByRole("button"));
      });

      const toast = await screen.findByRole("alert");

      expect(toast).toHaveTextContent("This is a test error message.");
    });
  });
});
