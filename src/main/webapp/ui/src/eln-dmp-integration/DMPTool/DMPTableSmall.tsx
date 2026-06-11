import React from "react";
import Box from "@mui/material/Box";
import Checkbox from "@mui/material/Checkbox";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import type { SxProps, Theme } from "@mui/material/styles";

type DMPId = number | string;
type DMPUserInternalId = number;

type Plan = {
  dmpId: DMPId;
  dmpTitle: string;
  dmpUserInternalId: DMPUserInternalId;
};

const tableRowSx = { width: "100%", display: "flex", flexDirection: "row" };
const tableRowCellSx: SxProps<Theme> = (_theme) => ({
  display: "flex",
  flexDirection: "row",
  justifyContent: "space-between",
  borderBottomWidth: "0px",
  alignItems: "center",
  width: "100%",
  "@media print": {
    p: 0.5,
  },
});
type TableArgs = {
  plans: Array<Plan>;
  selectedPlans: Array<DMPUserInternalId>;
  addSelectedPlan: (id: DMPUserInternalId) => void;
  removeSelectedPlan: (id: DMPUserInternalId) => void;
};

export default function DMPTableSmall({
  plans,
  selectedPlans,
  addSelectedPlan,
  removeSelectedPlan,
}: TableArgs): React.ReactNode {
  return (
    <TableContainer sx={{ overflowX: "hidden" }}>
      <Table size="small">
        <TableHead sx={{ maxWidth: "100%" }}>
          <TableRow sx={tableRowSx}>
            <TableCell sx={tableRowCellSx}>
              <Box component="span" sx={{ flex: 1 }}>
                Select
              </Box>
              <Box component="span" sx={{ flex: 5 }}>
                DMP Title
              </Box>
              <Box component="span" sx={{ flex: 3, textAlign: "center" }}>
                ID
              </Box>
            </TableCell>
          </TableRow>
        </TableHead>
        <TableBody sx={{ maxWidth: "100%" }}>
          {plans.map((plan, i) => {
            const id = plan.dmpUserInternalId;
            const isSelected = selectedPlans.includes(id);
            return (
              <TableRow
                key={i}
                sx={(theme) => ({
                  ...tableRowSx,
                  borderBottom: `1px dotted ${theme.palette.primary.main}`,
                })}
                data-testid={plan.dmpId}
              >
                <TableCell sx={tableRowCellSx}>
                  <Box
                    component="span"
                    sx={{
                      flex: 1,
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "space-between",
                    }}
                  >
                    <Checkbox
                      color="primary"
                      onChange={() => {
                        if (isSelected) removeSelectedPlan(id);
                        else addSelectedPlan(id);
                      }}
                      value={"dmpUserInternalId"}
                      checked={isSelected}
                      slotProps={{
                        input: { "aria-label": "Plan selection" },
                      }}
                    />
                  </Box>
                  <Box
                    component="span"
                    sx={(theme) => ({
                      flex: 5,
                      color: theme.palette.primary.main,
                    })}
                  >
                    {plan.dmpTitle}
                  </Box>
                  <Box
                    component="span"
                    sx={{
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      flex: 3,
                    }}
                  >
                    {plan.dmpId}
                  </Box>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
