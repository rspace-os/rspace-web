import { BaseApiClient } from "./BaseApiClient";

export type SharePermission = "READ" | "EDIT";

type SharingResult = {
  shareInfos: Array<{ id: number }>;
  failedShares: number[];
};

export class ShareClient extends BaseApiClient {
  async shareWithGroup(itemIds: number[], groupId: number, permission: SharePermission): Promise<SharingResult> {
    const result = await this.requestJson<SharingResult>("post", "/api/v1/share", {
      data: {
        itemsToShare: itemIds,
        groups: [{ id: groupId, permission }],
        users: [],
      },
      action: "shareItemsWithGroup",
    });
    if (result.failedShares.length > 0) {
      throw new Error(`shareItemsWithGroup failed for item ids: ${result.failedShares.join(", ")}`);
    }
    return result;
  }
}
