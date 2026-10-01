import type { TFunction } from "i18next";
import type { ResourceAccessAdapter } from "@/modules/common/resource-access/ResourceAccessEditor";

export const bookingResourceAccessAdapter = (t: TFunction<"booking">): ResourceAccessAdapter => ({
  ownerRole: "OWNER",
  defaultRole: "BOOKER",
  allUsersRole: "BOOKER",
  allUsersLabel: t("access.allUsers"),
  leaveLabel: t("access.leave"),
  roles: [
    {
      key: "OWNER",
      label: t("access.roles.owner.label"),
      description: t("access.roles.owner.description"),
      allowedGranteeKinds: ["USER", "GROUP"],
    },
    {
      key: "MANAGER",
      label: t("access.roles.manager.label"),
      description: t("access.roles.manager.description"),
      allowedGranteeKinds: ["USER", "GROUP"],
    },
    {
      key: "BOOKER",
      label: t("access.roles.booker.label"),
      description: t("access.roles.booker.description"),
      allowedGranteeKinds: ["USER", "GROUP", "AUDIENCE"],
    },
    {
      key: "VIEWER",
      label: t("access.roles.viewer.label"),
      description: t("access.roles.viewer.description"),
      allowedGranteeKinds: ["USER", "GROUP"],
    },
    {
      key: "NO_ACCESS",
      label: t("access.roles.noAccess.label"),
      description: t("access.roles.noAccess.description"),
      allowedGranteeKinds: ["AUDIENCE"],
    },
  ],
});
