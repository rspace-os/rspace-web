import { useTranslation } from "react-i18next";
import DisableAutoshareDialog from "./DisableAutoshareDialog";
import EnableAutoshareDialog from "./EnableAutoshareDialog";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
export default function AutoshareStatus(props: any) {
  const { t } = useTranslation("common");
  return (
    <>
      {!props.group.labGroup && <em>{t("profile.groups.autosharing.onlyLabGroups")}</em>}
      {props.isCurrentlySharing && props.group.labGroup && <em>{t("profile.groups.autosharing.inProgress")}</em>}
      {!props.isCurrentlySharing && props.group.autoshareEnabled && props.group.labGroup && (
        <DisableAutoshareDialog {...props} />
      )}
      {!props.isCurrentlySharing && !props.group.autoshareEnabled && props.group.labGroup && (
        <EnableAutoshareDialog {...props} />
      )}
    </>
  );
}
