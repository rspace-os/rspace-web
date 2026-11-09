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
      allowedGranteeKinds: ["USER", "GROUP"],
    },
    {
      key: "MANAGER",
      label: t("access.roles.manager.label"),
      allowedGranteeKinds: ["USER", "GROUP"],
    },
    {
      key: "BOOKER",
      label: t("access.roles.booker.label"),
      allowedGranteeKinds: ["USER", "GROUP", "AUDIENCE"],
    },
    {
      key: "VIEWER",
      label: t("access.roles.viewer.label"),
      allowedGranteeKinds: ["USER", "GROUP"],
    },
    {
      key: "NO_ACCESS",
      label: t("access.roles.noAccess.label"),
      allowedGranteeKinds: ["AUDIENCE"],
    },
  ],
});
