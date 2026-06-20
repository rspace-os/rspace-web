import { describe, expect, test } from "vitest";
import { RemoteFile } from "./useGalleryListing";

/**
 * S3 filestore items carry RSpace write-provenance (createdBy / createdAt). This is RSpace-stamped,
 * not the object's real ownership/creation, so it is exposed on its own fields and shown under the
 * info panel's "Added to S3" rows. Owner stays "Unknown owner" and the generic creationDate stays
 * unset, so those rows keep their backend-agnostic meaning.
 */
describe("RemoteFile write-provenance metadata", () => {
  function remoteFolder(createdBy?: string | null, createdAt?: Date): RemoteFile {
    return new RemoteFile({
      nfsId: 1,
      name: "folder",
      folder: true,
      fileSize: 0,
      modificationDate: undefined,
      createdBy,
      createdAt,
      path: [],
      logicPath: "1:folder",
      token: "token",
    });
  }

  test("createdBy exposes the RSpace uploader when present", () => {
    expect(remoteFolder("alice").createdBy).toBe("alice");
  });

  test("createdBy is null when absent", () => {
    expect(remoteFolder().createdBy).toBeNull();
  });

  test("createdAt exposes the upload time when present", () => {
    const uploadedAt = new Date("2026-06-18T10:00:00Z");
    expect(remoteFolder("alice", uploadedAt).createdAt).toEqual(uploadedAt);
  });

  test("createdAt is undefined when absent", () => {
    expect(remoteFolder("alice").createdAt).toBeUndefined();
  });

  test("ownerName stays 'Unknown owner' regardless of write-provenance", () => {
    expect(remoteFolder("alice").ownerName).toBe("Unknown owner");
    expect(remoteFolder("alice").ownerUsername).toBeNull();
  });

  // RemoteFile deliberately does not declare the generic `creationDate`, so the info panel's
  // "Created" row stays empty for S3 items — provenance shows under "Added to S3 on" instead.
  // That absence is enforced by the type system (accessing `.creationDate` here would not compile).
});
