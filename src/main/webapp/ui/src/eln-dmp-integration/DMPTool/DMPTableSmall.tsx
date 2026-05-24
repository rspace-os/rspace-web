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
const tableSubCellSx = {
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
};

function PlanHeaderRow() {
  return (
    <TableRow sx={tableRowSx}>
      <TableCell sx={tableRowCellSx}>
        <span style={{ flex: 1 }}>Select</span>
        <span style={{ flex: 5 }}>DMP Title</span>
        <span style={{ flex: 3, textAlign: "center" }}>
          ID
        </span>
      </TableCell>
    </TableRow>
  );
}

type BodyRowArgs = {
  plan: Plan;
  selectedPlans: Array<DMPUserInternalId>;
  addSelectedPlan: (id: DMPUserInternalId) => void;
  removeSelectedPlan: (id: DMPUserInternalId) => void;
};

function PlanRow({
  plan,
  selectedPlans,
  addSelectedPlan,
  removeSelectedPlan,
}: BodyRowArgs) {
  const isCurrentlySelected = (id: DMPUserInternalId) =>
    selectedPlans.includes(id);

  const toggleSelected = () => {
    const id = plan.dmpUserInternalId;
    if (isCurrentlySelected(id)) removeSelectedPlan(id);
    else addSelectedPlan(id);
  };

  return (
    <TableRow
      sx={(theme) => ({
        ...tableRowSx,
        borderBottom: `1px dotted ${theme.palette.primary.main}`,
      })}
      data-testid={plan.dmpId}
    >
      <TableCell sx={tableRowCellSx}>
        <span style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "space-between" }}>
          <Checkbox
            color="primary"
            onChange={() => toggleSelected()}
            value={"dmpUserInternalId"}
            checked={isCurrentlySelected(plan.dmpUserInternalId)}
            slotProps={{ input: { "aria-label": "Plan selection" } }}
          />
        </span>

        <Box component="span" sx={(theme) => ({ flex: 5, color: theme.palette.primary.main })}>
          {plan.dmpTitle}
        </Box>
        <span style={{ ...tableSubCellSx, flex: 3 }}>
          {plan.dmpId}
        </span>
      </TableCell>
    </TableRow>
  );
}

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
    <TableContainer style={{ overflowX: "hidden" }}>
      <Table size="small">
        <TableHead style={{ maxWidth: "100%" }}>
          <PlanHeaderRow />
        </TableHead>
        <TableBody style={{ maxWidth: "100%" }}>
          {plans.map((plan, i) => (
            <PlanRow
              key={i}
              plan={plan}
              selectedPlans={selectedPlans}
              addSelectedPlan={addSelectedPlan}
              removeSelectedPlan={removeSelectedPlan}
            />
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
