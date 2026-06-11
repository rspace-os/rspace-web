import React from "react";
import { observer } from "mobx-react-lite";
import Stack from "@mui/material/Stack";
import GlobalId from "./GlobalId";
import Date from "./Date";
import LatestTemplateActions from "./LatestTemplateActions";
import VersionHistory from "./VersionHistory";
import LinkedDocuments from "./LinkedDocuments";
import { type Factory } from "../../../stores/definitions/Factory";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";

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
  return (
    <Stack spacing={5}>
      <GlobalId record={record} />
      <Date label="Created" date={record.created} />
      <Date label="Last Modified" date={record.lastModified} />
      <VersionHistory record={record} />
      <LatestTemplateActions record={record} />
      {record.usableInLoM && record.globalId && (
        <LinkedDocuments globalId={record.globalId} factory={factory} />
      )}
    </Stack>
  );
}

export default observer(SidebarBody);
