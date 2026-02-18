import { beforeEach, describe, expect, test, vi } from "vitest";
import { getCommonGroupsInShares } from "../services/shareGroups";
import type { GroupInfo } from "@/modules/groups/schema";
import type { ShareListingParams } from "../queries";
import type { ShareSearchResponse } from "../schema";
import { getGroupById } from "@/modules/groups/queries";
import { getShareListing } from "@/modules/share/queries";

vi.mock("@/modules/groups/queries", () => ({
  getGroupById: vi.fn(),
}));

vi.mock("@/modules/share/queries", () => ({
  getShareListing: vi.fn(),
}));

const mockedGetGroupById = vi.mocked(getGroupById);
const mockedGetShareListing = vi.mocked(getShareListing);

const makeShareResponse = (
  shares: ShareSearchResponse["shares"],
  folderShares: ShareSearchResponse["folderShares"] = [],
): ShareSearchResponse => ({
  totalHits: shares.length,
  pageNumber: 0,
  shares,
  folderShares,
  _links: [],
});

const makeGroup = (id: number): GroupInfo => ({
  id,
  globalId: `GR${id}`,
  name: `Group ${id}`,
  type: "LAB_GROUP",
  sharedFolderId: 1000 + id,
  sharedSnippetFolderId: 2000 + id,
  members: [],
  raid: null,
});

describe("getCommonGroupsInShares", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test("normalizes shareItemIds and filters to group targets", async () => {
    const shares: ShareSearchResponse["shares"] = [
      {
        id: 1,
        sharedItemId: 10,
        shareItemName: "Doc 10",
        sharedTargetId: 5,
        sharedTargetType: "GROUP",
        permission: "READ",
      },
      {
        id: 2,
        sharedItemId: 20,
        shareItemName: "Doc 20",
        sharedTargetId: 5,
        sharedTargetType: "GROUP",
        permission: "READ",
      },
      {
        id: 3,
        sharedItemId: 20,
        shareItemName: "Doc 20",
        sharedTargetId: 6,
        sharedTargetType: "USER",
        permission: "READ",
      },
      {
        id: 4,
        sharedItemId: 10,
        shareItemName: "Doc 10",
        sharedTargetId: 6,
        sharedTargetType: "USER",
        permission: "READ",
      },
    ];
    const params: ShareListingParams = {
      sharedItemIds: ["10", "20"],
    };
    const token = "token-123";

    mockedGetShareListing.mockResolvedValue(makeShareResponse(shares));
    const group = makeGroup(5);
    mockedGetGroupById.mockResolvedValue(group);

    const result = await getCommonGroupsInShares(params, { token });

    expect(mockedGetShareListing).toHaveBeenCalledWith(params, { token });
    expect(mockedGetGroupById).toHaveBeenCalledTimes(1);
    expect(mockedGetGroupById).toHaveBeenCalledWith("5", { token });
    expect(Array.from(result.entries())).toEqual([[5, group]]);
  });

  test("returns empty map when there are no common group shares", async () => {
    const token = "token-456";
    const params: ShareListingParams = { sharedItemIds: ["10", "20"] };
    mockedGetShareListing.mockResolvedValue(makeShareResponse([]));

    const result = await getCommonGroupsInShares(params, { token });

    expect(mockedGetGroupById).not.toHaveBeenCalled();
    expect(result.size).toBe(0);
  });

  test("includes null group results for group targets", async () => {
    const token = "token-789";
    const params: ShareListingParams = { sharedItemIds: ["10", "20"] };
    const shares: ShareSearchResponse["shares"] = [
      {
        id: 1,
        sharedItemId: 10,
        shareItemName: "Doc 10",
        sharedTargetId: 11,
        sharedTargetType: "GROUP",
        permission: "READ",
      },
      {
        id: 2,
        sharedItemId: 10,
        shareItemName: "Doc 10",
        sharedTargetId: 12,
        sharedTargetType: "GROUP",
        permission: "READ",
      },
      {
        id: 3,
        sharedItemId: 20,
        shareItemName: "Doc 20",
        sharedTargetId: 11,
        sharedTargetType: "GROUP",
        permission: "READ",
      },
      {
        id: 4,
        sharedItemId: 20,
        shareItemName: "Doc 20",
        sharedTargetId: 12,
        sharedTargetType: "GROUP",
        permission: "READ",
      },
    ];
    mockedGetShareListing.mockResolvedValue(makeShareResponse(shares));
    const groupA = makeGroup(11);
    mockedGetGroupById
      .mockResolvedValueOnce(groupA)
      .mockResolvedValueOnce(null);

    const result = await getCommonGroupsInShares(params, { token });

    expect(mockedGetGroupById).toHaveBeenCalledTimes(2);
    expect(Array.from(result.entries())).toEqual([
      [11, groupA],
      [12, null],
    ]);
  });

  test("includes folder shares when determining common groups", async () => {
    const token = "token-012";
    const shares: ShareSearchResponse["shares"] = [
      {
        id: 1,
        sharedItemId: 10,
        shareItemName: "Doc 10",
        sharedTargetId: 5,
        sharedTargetType: "GROUP",
        permission: "READ",
      },
    ];
    const folderShares: NonNullable<ShareSearchResponse["folderShares"]> = [
      {
        id: null,
        sharedItemId: 20,
        shareItemName: "Folder 20",
        sharedTargetId: 5,
        sharedTargetType: "GROUP",
        permission: "READ",
      },
    ];
    const params: ShareListingParams = { sharedItemIds: ["10", "20"] };

    mockedGetShareListing.mockResolvedValue(
      makeShareResponse(shares, folderShares),
    );
    const group = makeGroup(5);
    mockedGetGroupById.mockResolvedValue(group);

    const result = await getCommonGroupsInShares(params, { token });

    expect(Array.from(result.entries())).toEqual([[5, group]]);
  });

  test("returns common groups when only folder shares are present", async () => {
    const token = "token-345";
    const shares: ShareSearchResponse["shares"] = [];
    const folderShares: NonNullable<ShareSearchResponse["folderShares"]> = [
      {
        id: null,
        sharedItemId: 10,
        shareItemName: "Folder 10",
        sharedTargetId: 15,
        sharedTargetType: "GROUP",
        permission: "READ",
      },
      {
        id: null,
        sharedItemId: 20,
        shareItemName: "Folder 20",
        sharedTargetId: 15,
        sharedTargetType: "GROUP",
        permission: "READ",
      },
    ];
    const params: ShareListingParams = { sharedItemIds: ["10", "20"] };

    mockedGetShareListing.mockResolvedValue(
      makeShareResponse(shares, folderShares),
    );
    const group = makeGroup(15);
    mockedGetGroupById.mockResolvedValue(group);

    const result = await getCommonGroupsInShares(params, { token });

    expect(mockedGetGroupById).toHaveBeenCalledTimes(1);
    expect(Array.from(result.entries())).toEqual([[15, group]]);
  });
});
