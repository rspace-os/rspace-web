import { getGroupById } from "@/modules/groups/queries";
import type { GroupInfo } from "@/modules/groups/schema";
import { getShareListing, type ShareListingParams } from "@/modules/share/queries";
import { findCommonGroups } from "@/modules/share/utils";

export async function getCommonGroupsInShares(
  params: ShareListingParams = {},
  { token }: { token: string },
): Promise<Map<number, GroupInfo | null>> {
  const shares = await getShareListing(params, { token });

  const sharesWithFolders = shares.folderShares ? [...shares.shares, ...shares.folderShares] : shares.shares;

  const commonGroupShares = findCommonGroups({
    shares: sharesWithFolders,
    shareItemIds: (params.sharedItemIds ?? []).map((id) => Number(id)),
  }).filter((share) => share.sharedTargetType === "GROUP");

  return new Map(
    await Promise.all(
      commonGroupShares.map(
        async ({ sharedTargetId }) => [sharedTargetId, await getGroupById(String(sharedTargetId), { token })] as const,
      ),
    ),
  );
}
