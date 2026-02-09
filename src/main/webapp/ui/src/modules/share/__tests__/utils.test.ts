import { describe, expect, test } from "vitest";
import { findCommonGroups } from "../utils";
import type { SharedFolderList, SharedList } from "../schema";

const shareList: SharedList = [
  {
    id: 1,
    sharedItemId: 10,
    shareItemName: "Doc 10",
    sharedTargetId: 100,
    sharedTargetType: "GROUP",
    permission: "READ",
  },
  {
    id: 2,
    sharedItemId: 10,
    shareItemName: "Doc 10",
    sharedTargetId: 200,
    sharedTargetType: "USER",
    permission: "READ",
  },
  {
    id: 3,
    sharedItemId: 20,
    shareItemName: "Doc 20",
    sharedTargetId: 100,
    sharedTargetType: "GROUP",
    permission: "READ",
  },
  {
    id: 4,
    sharedItemId: 20,
    shareItemName: "Doc 20",
    sharedTargetId: 200,
    sharedTargetType: "USER",
    permission: "EDIT",
  },
  {
    id: 5,
    sharedItemId: 30,
    shareItemName: "Doc 30",
    sharedTargetId: 300,
    sharedTargetType: "GROUP",
    permission: "READ",
  },
];

describe("findCommonGroups", () => {
  test("returns empty list when shareItemIds is empty", () => {
    const result = findCommonGroups({
      shares: shareList,
      shareItemIds: [],
    });

    expect(result).toEqual([]);
  });

  test("returns common targets across all share item ids", () => {
    const result = findCommonGroups({
      shares: shareList,
      shareItemIds: [10, 20],
    });

    expect(result).toHaveLength(2);
    expect(result).toEqual(
      expect.arrayContaining([
        { sharedTargetType: "GROUP", sharedTargetId: 100 },
        { sharedTargetType: "USER", sharedTargetId: 200 },
      ]),
    );
  });

  test("filters by permission when provided", () => {
    const result = findCommonGroups({
      shares: shareList,
      shareItemIds: [10, 20],
      permission: "READ",
    });

    expect(result).toHaveLength(1);
    expect(result).toEqual(
      expect.arrayContaining([
        { sharedTargetType: "GROUP", sharedTargetId: 100, permission: "READ" },
      ]),
    );
  });

  test("ignores shares for items not in shareItemIds", () => {
    const result = findCommonGroups({
      shares: shareList,
      shareItemIds: [30],
    });

    expect(result).toHaveLength(1);
    expect(result).toEqual(
      expect.arrayContaining([
        { sharedTargetType: "GROUP", sharedTargetId: 300 },
      ]),
    );
  });

  test("return empty array if shareItemIds have no common shares", () => {
    const result = findCommonGroups({
      shares: shareList,
      shareItemIds: [10, 30],
    });

    expect(result).toEqual([]);
  });

  test("return empty array if shareItemIds does not exist in shares", () => {
    const result = findCommonGroups({
      shares: shareList,
      shareItemIds: [40],
    });

    expect(result).toEqual([]);
  });

  test("returns all targets for a single share item id", () => {
    const result = findCommonGroups({
      shares: shareList,
      shareItemIds: [10],
    });

    expect(result).toHaveLength(2);
    expect(result).toEqual(
      expect.arrayContaining([
        { sharedTargetType: "GROUP", sharedTargetId: 100 },
        { sharedTargetType: "USER", sharedTargetId: 200 },
      ]),
    );
  });

  test("returns targets for a single folder share item id", () => {
    const folderShares: SharedFolderList = [
      {
        id: null,
        sharedItemId: 50,
        shareItemName: "Folder 50",
        sharedTargetId: 600,
        sharedTargetType: "GROUP",
        permission: "READ",
      },
      {
        id: null,
        sharedItemId: 50,
        shareItemName: "Folder 50",
        sharedTargetId: 700,
        sharedTargetType: "USER",
        permission: "READ",
      },
    ];

    const result = findCommonGroups({
      shares: folderShares,
      shareItemIds: [50],
    });

    expect(result).toHaveLength(2);
    expect(result).toEqual(
      expect.arrayContaining([
        { sharedTargetType: "GROUP", sharedTargetId: 600 },
        { sharedTargetType: "USER", sharedTargetId: 700 },
      ]),
    );
  });

  test("includes common targets from folder shares", () => {
    const folderShares: SharedFolderList = [
      {
        id: null,
        sharedItemId: 10,
        shareItemName: "Folder 10",
        sharedTargetId: 400,
        sharedTargetType: "GROUP",
        permission: "READ",
      },
      {
        id: null,
        sharedItemId: 20,
        shareItemName: "Folder 20",
        sharedTargetId: 400,
        sharedTargetType: "GROUP",
        permission: "READ",
      },
    ];

    const result = findCommonGroups({
      shares: folderShares,
      shareItemIds: [10, 20],
    });

    expect(result).toHaveLength(1);
    expect(result).toEqual(
      expect.arrayContaining([
        { sharedTargetType: "GROUP", sharedTargetId: 400 },
      ]),
    );
  });

  test("respects permission filters for folder shares", () => {
    const shares: SharedList = [
      {
        id: 6,
        sharedItemId: 10,
        shareItemName: "Doc 10",
        sharedTargetId: 200,
        sharedTargetType: "USER",
        permission: "READ",
      },
      {
        id: 7,
        sharedItemId: 20,
        shareItemName: "Doc 20",
        sharedTargetId: 200,
        sharedTargetType: "USER",
        permission: "READ",
      },
    ];
    const folderShares: SharedFolderList = [
      {
        id: null,
        sharedItemId: 10,
        shareItemName: "Folder 10",
        sharedTargetId: 500,
        sharedTargetType: "GROUP",
        permission: "EDIT",
      },
      {
        id: null,
        sharedItemId: 20,
        shareItemName: "Folder 20",
        sharedTargetId: 500,
        sharedTargetType: "GROUP",
        permission: "EDIT",
      },
    ];

    const result = findCommonGroups({
      shares: [...shares, ...folderShares],
      shareItemIds: [10, 20],
      permission: "EDIT",
    });

    expect(result).toHaveLength(1);
    expect(result).toEqual(
      expect.arrayContaining([
        { sharedTargetType: "GROUP", sharedTargetId: 500, permission: "EDIT" },
      ]),
    );
  });
});
