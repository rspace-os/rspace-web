import { describe, expect, test, vi } from "vitest";
// biome-ignore lint/style/useImportType: initial biome migration
import { AxiosResponse } from "@/common/axios";
import ApiService from "../../../../common/InvApiService";
import { ExistingAttachment } from "../../AttachmentModel";

vi.mock("../../../stores/RootStore", () => ({
  default: () => ({}),
})); // break import cycle
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
      () => {},
    );
    const spy = vi.spyOn(ApiService, "query").mockImplementation(() =>
      Promise.resolve({
        data: new Blob(),
        status: 200,
        statusText: "OK",
        headers: {},
        config: {} as unknown,
      } as AxiosResponse),
    );
    await attachment.getFile();
    await attachment.getFile();
    expect(spy).toHaveBeenCalledTimes(1);
  });
});
