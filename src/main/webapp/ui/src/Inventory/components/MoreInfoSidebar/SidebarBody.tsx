import { buttonClasses } from "@mui/material/Button";
import { formGroupClasses } from "@mui/material/FormGroup";
import Stack from "@mui/material/Stack";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import type { Factory } from "../../../stores/definitions/Factory";
import type { InventoryRecord } from "../../../stores/definitions/InventoryRecord";
// biome-ignore lint/suspicious/noShadowRestrictedNames: initial biome migration
import Date from "./Date";
import GlobalId from "./GlobalId";
import LatestTemplateActions from "./LatestTemplateActions";
import LinkedDocuments from "./LinkedDocuments";
import VersionHistory from "./VersionHistory";

type SidebarBodyArgs = {
  record: InventoryRecord;
  factory: Factory | null;
};

/**
 * Presentational body of the MoreInfo side panel. Renders the labelled fields
 * for an inventory record. Used by the main Sidebar drawer and by the
 * InventoryInfoDialog modal so both surfaces share one implementation.
 */
function SidebarBody({ record, factory }: SidebarBodyArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  return (
    <Stack
      spacing={2}
      // Give every action button (version history, update samples, linked
      // documents) a shared minimum width so they line up, while still letting
      // a longer label grow past it. These are the only FormGroup-wrapped
      // buttons in the panel; the dialog Close buttons they render are portaled
      // out of this subtree.
      sx={{
        [`& .${formGroupClasses.root} .${buttonClasses.root}`]: {
          minWidth: "220px",
        },
      }}
    >
      <GlobalId record={record} />
      <Date label={t("moreInfo.created")} date={record.created} />
      <Date label={t("moreInfo.lastModified")} date={record.lastModified} />
      <VersionHistory record={record} />
      <LatestTemplateActions record={record} />
      {/* templates cannot appear in a List of Materials, but they are valid
          link targets, so the linked-documents dialog (which also lists the
          inventory items linking here) applies to them like every other type */}
      {(record.usableInLoM || record.recordType === "sampleTemplate" || record.recordType === "instrumentTemplate") &&
        record.globalId && <LinkedDocuments globalId={record.globalId} factory={factory} />}
    </Stack>
  );
}

export default observer(SidebarBody);
