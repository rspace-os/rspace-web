/*
 * This is a simple Table that displays the name and Global
 * ID of Records
 */

import { withStyles } from "Styles";
import CardContent from "@mui/material/CardContent";
import Collapse from "@mui/material/Collapse";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import type React from "react";
import GlobalId from "../../components/GlobalId";
import type { Record } from "../../stores/definitions/Record";

const CustomContent = withStyles<React.ComponentProps<typeof CardContent>, { root: string }>(() => ({
    root: {
        padding: `0 !important`,
    },
}))(CardContent);

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

export default function SimpleRecordsTable({ open, records }: SimpleRecordsTableArgs): React.ReactNode {
    return (
        <Collapse in={open}>
            <CustomContent>
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
                                <TableCell sx={{ width: "1px", whiteSpace: "nowrap" }}>{r.name}</TableCell>
                                <TableCell>
                                    <GlobalId record={r} />
                                </TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </CustomContent>
        </Collapse>
    );
}
