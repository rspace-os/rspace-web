//@flow

import ExpandCollapseIcon from "../../../components/ExpandCollapseIcon";
import { type Record } from "../../../stores/definitions/Record";
import { observer } from "mobx-react-lite";
import RsSet from "../../../util/set";
import IconButton from "@mui/material/IconButton";
import React, { type Node, useState } from "react";
import Card from "@mui/material/Card";
import Box from "@mui/material/Box";
import CardHeader from "@mui/material/CardHeader";
import { withStyles } from "Styles";
import SimpleRecordsTable from "../SimpleRecordsTable";

const CustomHeader = withStyles<
  {| open: boolean, setOpen: (boolean) => void, title: string |},
  { root: string, action: string }
>(() => ({
  root: {
    height: 48,
    padding: "0 0 0 12px",
  },
  action: {
    margin: 0,
    height: "100%",
    alignItems: "center",
    display: "flex",
  },
}))(({ open, setOpen, classes, title }) => (
  <CardHeader
    classes={classes}
    title={`${title} (Click to ${open ? "close" : "expand"} list)`}
    onClick={() => setOpen(!open)}
    titleTypographyProps={{ variant: "body1" }}
    action={
      <IconButton onClick={() => setOpen(!open)}>
        <ExpandCollapseIcon open={open} />
      </IconButton>
    }
  />
));

/*
 * The RecordLike type variable is used to reference the subtype of Record that
 * this component will likely be called with. In all liklihood, the caller of
 * this component will pass an RsSet<InventoryRecord>, RsSet<Container>, etc.
 * The code in this component MUST not mutate the `records` set, and in fact
 * should be prevented from doing so because of this type variable. It is
 * unfortunate that Flow does not provide a ReadOnlySet utility type like
 * ReadOnlyArray.
 */
type BatchEditingItemsTableArgs<RecordLike: Record> = {|
  records: RsSet<RecordLike>,
  label: string,
|};

function BatchEditingItemsTable<RecordLike: Record>({
  records,
  label,
}: BatchEditingItemsTableArgs<RecordLike>): Node {
  const [open, setOpen] = useState(false);

  return (
    <Box my={1}>
      <Card variant="outlined">
        <CustomHeader title={label} open={open} setOpen={setOpen} />
        <SimpleRecordsTable
          open={open}
          records={records.toArray((a, b) => (a.id ?? -1) - (b.id ?? -1))}
        />
      </Card>
    </Box>
  );
}

export default (observer(
  BatchEditingItemsTable
): typeof BatchEditingItemsTable);
