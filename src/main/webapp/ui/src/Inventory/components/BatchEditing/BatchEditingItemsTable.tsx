import Box from "@mui/material/Box";
import Card from "@mui/material/Card";
import CardHeader from "@mui/material/CardHeader";
import IconButton from "@mui/material/IconButton";
import { observer } from "mobx-react-lite";
import React from "react";
import ExpandCollapseIcon from "../../../components/ExpandCollapseIcon";
// biome-ignore lint/style/useImportType: initial biome migration
import { type Record } from "../../../stores/definitions/Record";
// biome-ignore lint/style/useImportType: initial biome migration
import RsSet from "../../../util/set";
import SimpleRecordsTable from "../SimpleRecordsTable";

type BatchEditingItemsTableArgs<RecordLike extends Record> = {
  records: RsSet<RecordLike>;
  label: string;
};
function BatchEditingItemsTable<RecordLike extends Record>({
  records,
  label,
}: BatchEditingItemsTableArgs<RecordLike>): React.ReactNode {
  const [open, setOpen] = React.useState(false);
  return (
    <Box sx={{ my: 1 }}>
      <Card variant="outlined">
        <CardHeader
          sx={{ height: 48, p: "0 0 0 12px" }}
          title={`${label} (Click to ${open ? "close" : "expand"} list)`}
          onClick={() => setOpen(!open)}
          action={
            <IconButton>
              <ExpandCollapseIcon open={open} />
            </IconButton>
          }
          slotProps={{
            action: { sx: { m: 0, height: "100%", alignItems: "center", display: "flex" } },
            title: { variant: "body1" },
          }}
        />
        <SimpleRecordsTable open={open} records={records.toArray((a, b) => (a.id ?? -1) - (b.id ?? -1))} />
      </Card>
    </Box>
  );
}
export default observer(BatchEditingItemsTable);
