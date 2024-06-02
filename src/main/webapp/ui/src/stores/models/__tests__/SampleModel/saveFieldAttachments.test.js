/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import { makeMockSample } from "./mocking";
import InvApiService from "../../../../common/InvApiService";

jest.mock("../../../../stores/stores/RootStore", () => () => ({}));
jest.mock("../../../../common/InvApiService", () => ({
  post: jest.fn(),
}));

describe("saveFieldAttachments", () => {
  describe("When the sample has an attachment field with an existing file", () => {
    /*
     * The endpoint only needs to be called when there is a new file/change of file.
     */
    test("there should be no calls to the /files endpoint.", async () => {
      const sample = makeMockSample({
        fields: [
          {
            id: 1,
            type: "attachment",
            columnIndex: 1,
            mandatory: false,
            selectedOptions: null,
            attachment: {
              contentMimeType: "text/plain",
              globalId: "IF3",
              id: 3,
              name: "loremIpsem20para.txt",
              size: 10295,
              _links: [
                {
                  link: "http://localhost:8080/api/inventory/v1/files/3",
                  rel: "self",
                },
                {
                  link: "http://localhost:8080/api/inventory/v1/files/3/file",
                  rel: "enclosure",
                },
              ],
            },
          },
        ],
      });

      const spy = jest.spyOn(InvApiService, "post");

      await sample.saveFieldAttachments();

      expect(spy).not.toHaveBeenCalled();
    });
  });
});
