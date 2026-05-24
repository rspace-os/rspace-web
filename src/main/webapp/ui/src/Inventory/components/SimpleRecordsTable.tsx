/*
 * This is a simple Table that displays the name and Global
 * ID of Records
 */

import { type Record } from "../../stores/definitions/Record";
import Collapse from "@mui/material/Collapse";
import React from "react";
import Table from "@mui/material/Table";
import TableHead from "@mui/material/TableHead";
import TableBody from "@mui/material/TableBody";
import TableRow from "@mui/material/TableRow";
import TableCell from "@mui/material/TableCell";
import GlobalId from "../../components/GlobalId";
import CardContent from "@mui/material/CardContent";

type SimpleRecordsTableArgs = {
  open: boolean;

  /*
   * Array has to be ReadOnly (i.e. immutable) because the caller will likely
   * be providing a collection of Records that are actually of some subtype
   * e.g. an Array<InventoryRecord> or Array<Container>. This component must
   * not mutate the array to add records that do not conform with that subtype.
   */
  records: ReadonlyArray<Record>;
};

export default function SimpleRecordsTable({
  open,
  records,
}: SimpleRecordsTableArgs): React.ReactNode {
  return (
    <Collapse in={open}>
      <CardContent sx={{ p: "0 !important" }}>
        <Table size="small" stickyHeader>
          <TableHead>
            <TableRow>
              <TableCell>Name</TableCell>
              <TableCell>Global ID</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {records.map((r) => (
              <TableRow key={r.globalId}>
                {/* width: 1px and preventing wrapping is intended to reduce
                    the column width as much as possible */}
                <TableCell sx={{ width: "1px", whiteSpace: "nowrap" }}>
                  {r.name}
                </TableCell>
                <TableCell>
                  <GlobalId record={r} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Collapse>
  );
}
