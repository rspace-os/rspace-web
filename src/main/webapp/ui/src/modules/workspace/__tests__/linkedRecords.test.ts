import { beforeEach, describe, expect, it, vi } from "vitest";
import { getLinkedByRecords, getLinkedDocuments } from "@/modules/workspace/linkedRecords";

beforeEach(() => {
  fetchMock.resetMocks();
  vi.clearAllMocks();
});

describe("getLinkedByRecords", () => {
  it("requests the linked-by endpoint with the numeric target record id", async () => {
    fetchMock.mockResponseOnce(JSON.stringify({ data: [], error: null, success: true }));

    await getLinkedByRecords(123);

    expect(fetchMock).toHaveBeenCalledWith(
      "/workspace/getLinkedByRecords?targetRecordId=123",
      expect.objectContaining({
        method: "GET",
        headers: { "X-Requested-With": "XMLHttpRequest" },
      }),
    );
  });

  it("splits readable rows from private rows and aggregates private docs by owner", async () => {
    fetchMock.mockResponseOnce(
      JSON.stringify({
        data: [
          { id: 11, oid: { idString: "SD11" }, name: "Doc one", ownerFullName: "Ada Lovelace" },
          { id: 12, oid: { idString: "SD12" }, name: "Doc two", ownerFullName: "Grace Hopper" },
          { ownerFullName: "Grace Hopper", ownerUsername: "grace" },
          { ownerFullName: "Grace Hopper", ownerUsername: "grace" },
          { ownerFullName: "Ada Lovelace", ownerUsername: "ada" },
        ],
        error: null,
        success: true,
      }),
    );

    const result = await getLinkedByRecords(123);

    expect(result.readable).toEqual([
      { globalId: "SD11", name: "Doc one", ownerFullName: "Ada Lovelace" },
      { globalId: "SD12", name: "Doc two", ownerFullName: "Grace Hopper" },
    ]);
    expect(result.privateByOwner).toEqual([
      { ownerFullName: "Grace Hopper", count: 2 },
      { ownerFullName: "Ada Lovelace", count: 1 },
    ]);
  });

  it("throws the endpoint error message for non-OK responses", async () => {
    fetchMock.mockResponseOnce(
      JSON.stringify({
        data: null,
        error: { errorMessages: ["Record not found"] },
        success: false,
      }),
      { status: 404, statusText: "Not Found" },
    );

    await expect(getLinkedByRecords(99)).rejects.toThrow("Record not found");
  });
});

describe("getLinkedDocuments", () => {
  it("requests the gallery linked-documents endpoint with the media id", async () => {
    fetchMock.mockResponseOnce(JSON.stringify({ data: [], error: null, success: true }));

    await getLinkedDocuments(55);

    expect(fetchMock).toHaveBeenCalledWith(
      "/gallery/ajax/getLinkedDocuments/55",
      expect.objectContaining({
        method: "GET",
        headers: { "X-Requested-With": "XMLHttpRequest" },
      }),
    );
  });

  it("returns readable linked documents as global-id rows", async () => {
    fetchMock.mockResponseOnce(
      JSON.stringify({
        data: [{ id: 7, oid: { idString: "SD7" }, name: "Linked doc", ownerFullName: "Ada Lovelace" }],
        error: null,
        success: true,
      }),
    );

    const result = await getLinkedDocuments(55);

    expect(result.readable).toEqual([{ globalId: "SD7", name: "Linked doc", ownerFullName: "Ada Lovelace" }]);
    expect(result.privateByOwner).toEqual([]);
  });
});
