import { describe, expect, test } from "vitest";
import { RemoteFile } from "./useGalleryListing";

describe("RemoteFile owner fields", () => {
  function remoteFolder(): RemoteFile {
    return new RemoteFile({
      nfsId: 1,
      name: "folder",
      folder: true,
      fileSize: 0,
      modificationDate: undefined,
      path: [],
      logicPath: "1:folder",
      token: "token",
    });
  }

  test("ownerName stays 'Unknown owner' and ownerUsername null", () => {
    // Filestore items have no RSpace owner; write provenance (created-by/-at) is shown separately
    // in the info panel via an on-demand metadata fetch, not on the listing model.
    expect(remoteFolder().ownerName).toBe("Unknown owner");
    expect(remoteFolder().ownerUsername).toBeNull();
  });
});
