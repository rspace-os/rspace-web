/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import { ExistingAttachment } from "../../AttachmentModel";
import ApiService from "../../../../common/InvApiService";

jest.mock("../../../stores/RootStore", () => {}); // break import cycle

describe("getFile", () => {
  test("Should memoise the result, i.e. only fetch the file once.", async () => {
    const attachment = new ExistingAttachment(
      {
        id: 1,
        name: "foo.txt",
        size: 0,
        globalId: "IF1",
        contentMimeType: "text/plain",
        _links: [{ rel: "enclosure", link: "MOCK_URL" }],
      },
      "",
      () => {}
    );
    const spy = jest.spyOn(ApiService, "query").mockImplementation(() => ({
      data: new Blob(),
    }));

    await attachment.getFile();
    await attachment.getFile();
    expect(spy).toHaveBeenCalledTimes(1);
  });
});
