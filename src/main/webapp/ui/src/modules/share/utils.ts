import type {
  ShareLike,
  SharePermission,
  ShareTargetInfo,
} from "@/modules/share/schema";

export const findCommonGroups = ({
  shares,
  shareItemIds,
  permission,
}: {
  shares: ReadonlyArray<ShareLike>;
  shareItemIds: number[];
  permission?: SharePermission;
}): ShareTargetInfo[] => {
  const uniqueShareItemIds = Array.from(new Set(shareItemIds));
  if (uniqueShareItemIds.length === 0) {
    return [];
  }

  const shareTargets = new Map<string, number[]>();
  shares.forEach((share) => {
    if (!shareItemIds.includes(share.sharedItemId)) {
      return;
    }

    if (permission && share.permission !== permission) {
      return;
    }

    const key = `${share.sharedTargetType}:${share.sharedTargetId}`;

    const existingShareItems = shareTargets.get(key);
    if (!existingShareItems) {
      shareTargets.set(key, [share.sharedItemId]);
    } else {
      existingShareItems.push(share.sharedItemId);
    }
  })

  shareTargets.forEach((itemIds, shareTarget) => {
    const hasAllItems = uniqueShareItemIds.every((id) => itemIds.includes(id));
    if (!hasAllItems) {
      shareTargets.delete(shareTarget);
    }
  })

  return Array.from(shareTargets.keys()).map((key) => {
    const [sharedTargetTypeValue, sharedTargetIdValue] = key.split(":");

    return {
      sharedTargetType: sharedTargetTypeValue as ShareTargetInfo["sharedTargetType"],
      sharedTargetId: Number(sharedTargetIdValue),
      ...(permission ? { permission } : {})
    }
  });
};
