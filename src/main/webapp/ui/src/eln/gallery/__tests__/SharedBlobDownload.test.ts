import { afterEach, describe, expect, test, vi } from "vitest";
import { SharedBlobDownload } from "../useGalleryListing";

afterEach(() => {
  vi.restoreAllMocks();
});

function heldDownload() {
  let release: (blob: Blob) => void = () => {};
  const download = vi.fn(
    () =>
      new Promise<Blob>((resolve) => {
        release = resolve;
      }),
  );
  return { download, release: (blob: Blob) => release(blob) };
}

describe("SharedBlobDownload", () => {
  test("downloads again after a failed download", async () => {
    const download = vi
      .fn<() => Promise<Blob>>()
      .mockRejectedValueOnce(new Error("network"))
      .mockResolvedValueOnce(new Blob(["content"]));
    const blobDownload = new SharedBlobDownload(download);

    await expect(blobDownload.href()).rejects.toThrow("network");
    await expect(blobDownload.href()).resolves.toMatch(/^blob:/);
    expect(download).toHaveBeenCalledTimes(2);
  });

  test("revoking before the download arrives revokes its URL once it does", async () => {
    const revokeObjectURL = vi.spyOn(URL, "revokeObjectURL");
    const { download, release } = heldDownload();
    const blobDownload = new SharedBlobDownload(download);
    const href = blobDownload.href();

    blobDownload.revoke();
    release(new Blob(["content"]));

    const url = await href;
    await vi.waitFor(() => {
      expect(revokeObjectURL).toHaveBeenCalledWith(url);
    });
    expect(revokeObjectURL).toHaveBeenCalledTimes(1);
  });

  test("revoking after the download arrives revokes its URL once, and a later request downloads again", async () => {
    const revokeObjectURL = vi.spyOn(URL, "revokeObjectURL");
    const download = vi.fn(() => Promise.resolve(new Blob(["content"])));
    const blobDownload = new SharedBlobDownload(download);
    const url = await blobDownload.href();

    blobDownload.revoke();

    await vi.waitFor(() => {
      expect(revokeObjectURL).toHaveBeenCalledWith(url);
    });
    expect(revokeObjectURL).toHaveBeenCalledTimes(1);
    await expect(blobDownload.href()).resolves.not.toBe(url);
    expect(download).toHaveBeenCalledTimes(2);
  });

  test("a download handed over while in flight is neither downloaded again nor revoked", async () => {
    const revokeObjectURL = vi.spyOn(URL, "revokeObjectURL");
    const previous = heldDownload();
    const next = heldDownload();
    const previousDownload = new SharedBlobDownload(previous.download);
    const href = previousDownload.href();

    const shared = previousDownload.sharedWith(new SharedBlobDownload(next.download));
    previous.release(new Blob(["content"]));

    await expect(shared.href()).resolves.toBe(await href);
    expect(previous.download).toHaveBeenCalledTimes(1);
    expect(next.download).not.toHaveBeenCalled();
    expect(revokeObjectURL).not.toHaveBeenCalled();
  });
});
