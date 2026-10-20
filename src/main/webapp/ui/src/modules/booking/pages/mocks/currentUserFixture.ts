import type { CurrentUser } from "@/modules/common/queries/currentUser";

export const currentUser: CurrentUser = {
  id: 1,
  username: "ada",
  email: "ada@example.com",
  firstName: "Ada",
  lastName: "Lovelace",
  homeFolderId: 2,
  workbenchId: 3,
  hasPiRole: false,
  hasSysAdminRole: false,
  profileImageUrl: null,
  profileImageApiUrl: null,
  orcid: { available: false, id: null },
  capabilities: { canUseInventory: true, canPublish: false, canViewSystem: false },
  livechat: { enabled: false, serverKey: null },
  session: {
    operatedAs: false,
    lastSession: null,
    canUseDevtools: false,
    canOverrideFeatureFlags: false,
    canChangeFeatureFlagBaselines: false,
  },
};
