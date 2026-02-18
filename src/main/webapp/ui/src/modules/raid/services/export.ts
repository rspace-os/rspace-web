import { GroupInfo } from "@/modules/groups/schema";
import { RaidReferenceDTO } from "@/modules/raid/schema";

export type RaidExportIneligibleReason = "MISSING_GROUPS" | "NO_PROJECT_GROUPS" | "NO_RAID_ASSOCIATION_FOUND" | "MULTIPLE_RAIDS_FOUND";

type GetRaidExportEligibilityResult =
  {
    isEligible: true;
    projectGroup: GroupInfo;
    raid: RaidReferenceDTO;
  }
  | {
    isEligible: false;
    reason: Extract<RaidExportIneligibleReason, "MISSING_GROUPS">;
    missingGroupIds: number[];
  }
  | {
    isEligible: false;
    reason: Extract<RaidExportIneligibleReason, "NO_PROJECT_GROUPS">;
  } | {
    isEligible: false;
    reason: Extract<RaidExportIneligibleReason, "NO_RAID_ASSOCIATION_FOUND">;
    projectGroups: GroupInfo[],
  } | {
    isEligible: false;
    reason: Extract<RaidExportIneligibleReason, "MULTIPLE_RAIDS_FOUND">;
    projectGroups: GroupInfo[],
  }

export const getRaidExportEligibility = (groupsMap: Map<number, GroupInfo | null>): GetRaidExportEligibilityResult => {
  // groupsMap can include targets derived from both direct shares and folderShares.
  const allGroupEntries = Array.from(groupsMap.entries());
  const missingGroups = allGroupEntries.filter(
    (entry): entry is [number, null] => entry[1] === null,
  );

  if (missingGroups.length > 0) {
    return {
      isEligible: false,
      reason: "MISSING_GROUPS",
      missingGroupIds: missingGroups.map(([groupId]) => groupId),
    };
  }

  const groups = allGroupEntries
    .filter((entry): entry is [number, GroupInfo] => entry[1] !== null)
    .map(([, group]) => group);

  const projectGroups = groups.filter((group) => group.type === "PROJECT_GROUP");
  if (projectGroups.length === 0) {
    return {
      isEligible: false,
      reason: "NO_PROJECT_GROUPS"
    }
  }

  const projectGroupsWithRaid = projectGroups.filter(
    (group): group is GroupInfo & { raid: NonNullable<GroupInfo["raid"]> } =>
      Boolean(group.raid),
  );

  if (projectGroupsWithRaid.length === 0) {
    return {
      isEligible: false,
      reason: "NO_RAID_ASSOCIATION_FOUND",
      projectGroups,
    };
  }

  if (projectGroupsWithRaid.length > 1) {
    return {
      isEligible: false,
      reason: "MULTIPLE_RAIDS_FOUND",
      projectGroups: projectGroupsWithRaid,
    };
  }

  return {
    isEligible: true,
    projectGroup: projectGroupsWithRaid[0],
    raid: projectGroupsWithRaid[0].raid,
  }
};
