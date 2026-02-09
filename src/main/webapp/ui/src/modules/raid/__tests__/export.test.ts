import { describe, expect, test } from "vitest";
import { getRaidExportEligibility } from "../services/export";
import type { GroupInfo } from "@/modules/groups/schema";
import type { RaidReferenceDTO } from "@/modules/raid/schema";

const baseRaid: RaidReferenceDTO = {
  raidServerAlias: "server-1",
  raidIdentifier: "raid-001",
  raidTitle: "Test RAiD",
};

const makeGroup = (
  overrides: Partial<GroupInfo> & Pick<GroupInfo, "id" | "type">,
): GroupInfo => ({
  id: overrides.id,
  globalId: `GR${overrides.id}`,
  name: `Group ${overrides.id}`,
  type: overrides.type,
  sharedFolderId: 1000 + overrides.id,
  sharedSnippetFolderId: 2000 + overrides.id,
  members: [],
  raid: overrides.raid ?? null,
});

const makeGroupsMap = (groups: Array<GroupInfo | null>): Map<number, GroupInfo | null> =>
  new Map(
    groups.map((group, index) => {
      if (group === null) {
        return [index + 1, null] as const;
      }
      return [group.id, group] as const;
    }),
  );

describe("getRaidExportEligibility", () => {
  test("returns NO_PROJECT_GROUPS when there are no project groups", () => {
    const groups: GroupInfo[] = [
      makeGroup({ id: 1, type: "LAB_GROUP" }),
      makeGroup({ id: 2, type: "COLLABORATION_GROUP" }),
    ];
    const groupsMap = makeGroupsMap(groups);

    const result = getRaidExportEligibility(groupsMap);

    expect(result).toEqual({
      isEligible: false,
      reason: "NO_PROJECT_GROUPS",
    });
  });

  test("returns NO_RAID_ASSOCIATION_FOUND when project groups lack raid", () => {
    const groups: GroupInfo[] = [
      makeGroup({ id: 3, type: "PROJECT_GROUP", raid: null }),
      makeGroup({ id: 4, type: "PROJECT_GROUP", raid: null }),
    ];
    const groupsMap = makeGroupsMap(groups);

    const result = getRaidExportEligibility(groupsMap);

    expect(result).toEqual({
      isEligible: false,
      reason: "NO_RAID_ASSOCIATION_FOUND",
      projectGroups: groups,
    });
  });

  test("returns MULTIPLE_RAIDS_FOUND when multiple project groups have raids", () => {
    const projectGroupA = makeGroup({
      id: 5,
      type: "PROJECT_GROUP",
      raid: baseRaid,
    });
    const projectGroupB = makeGroup({
      id: 6,
      type: "PROJECT_GROUP",
      raid: { ...baseRaid, raidIdentifier: "raid-002" },
    });
    const groups: GroupInfo[] = [projectGroupA, projectGroupB];
    const groupsMap = makeGroupsMap(groups);

    const result = getRaidExportEligibility(groupsMap);

    expect(result).toEqual({
      isEligible: false,
      reason: "MULTIPLE_RAIDS_FOUND",
      projectGroups: [projectGroupA, projectGroupB],
    });
  });

  test("returns eligible with single project group raid", () => {
    const projectGroup = makeGroup({
      id: 7,
      type: "PROJECT_GROUP",
      raid: baseRaid,
    });
    const groups: GroupInfo[] = [
      makeGroup({ id: 8, type: "LAB_GROUP" }),
      projectGroup,
    ];
    const groupsMap = makeGroupsMap(groups);

    const result = getRaidExportEligibility(groupsMap);

    expect(result).toEqual({
      isEligible: true,
      projectGroup,
      raid: baseRaid,
    });
  });

  test("returns eligible with single project group raid even when multiple project groups", () => {
    const projectGroupWithRaid = makeGroup({
      id: 9,
      type: "PROJECT_GROUP",
      raid: baseRaid,
    });
    const projectGroupWithoutRaid = makeGroup({
      id: 10,
      type: "PROJECT_GROUP",
      raid: null,
    });
    const groupsMap = makeGroupsMap([
      projectGroupWithRaid,
      projectGroupWithoutRaid,
    ]);

    const result = getRaidExportEligibility(groupsMap);

    expect(result).toEqual({
      isEligible: true,
      projectGroup: projectGroupWithRaid,
      raid: baseRaid,
    });
  });

  test("returns MISSING_GROUPS when any group lookup is missing", () => {
    const groupsMap = new Map<number, GroupInfo | null>([
      [1, makeGroup({ id: 1, type: "PROJECT_GROUP", raid: baseRaid })],
      [2, null],
      [3, makeGroup({ id: 3, type: "LAB_GROUP" })],
    ]);

    const result = getRaidExportEligibility(groupsMap);

    expect(result).toEqual({
      isEligible: false,
      reason: "MISSING_GROUPS",
      missingGroupIds: [2],
    });
  });

  test("returns MISSING_GROUPS with all missing ids when multiple are null", () => {
    const groupsMap = new Map<number, GroupInfo | null>([
      [10, null],
      [11, null],
    ]);

    const result = getRaidExportEligibility(groupsMap);

    expect(result).toEqual({
      isEligible: false,
      reason: "MISSING_GROUPS",
      missingGroupIds: [10, 11],
    });
  });
});
