import Box from "@mui/material/Box";
import { ThemeProvider } from "@mui/material/styles";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableRow from "@mui/material/TableRow";
import type React from "react";
import materialTheme from "@/theme";
import RequestUserChip from "../RequestUserChip";

export const LONG_NAME = { first: "Bartholomew-Alexander", last: "Montgomery-Fitzwilliam" };

/** A two-column table, like the Requests tables, in a container of the given width. */
export function RequestUserChipInTable({ width }: { width: number }): React.ReactNode {
  return (
    <ThemeProvider theme={materialTheme}>
      <Box sx={{ width }}>
        <Table>
          <TableBody>
            <TableRow>
              <TableCell>
                <RequestUserChip
                  person={{ id: 1, username: "bart", firstName: LONG_NAME.first, lastName: LONG_NAME.last }}
                />
              </TableCell>
              <TableCell sx={{ whiteSpace: "nowrap" }}>{"2026-01-01 10:00"}</TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </Box>
    </ThemeProvider>
  );
}
