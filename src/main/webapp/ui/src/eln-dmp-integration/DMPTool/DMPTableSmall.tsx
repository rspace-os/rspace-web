import React from "react";
import Checkbox from "@mui/material/Checkbox";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import clsx from "clsx";
import { makeStyles } from "tss-react/mui";

type DMPId = number | string;
type DMPUserInternalId = number;

type Plan = {
  dmpId: DMPId;
  dmpTitle: string;
  dmpUserInternalId: DMPUserInternalId;
};

const useStyles = makeStyles()((theme) => ({
  bottomBorder: { borderBottom: `1px dotted ${theme.palette.primary.main}` },
  quantityButton: {
    padding: theme.spacing(0.5),
    textTransform: "none",
  },
  relativeAnchor: { position: "relative" },
  textField: {
    marginLeft: theme.spacing(1),
    marginRight: theme.spacing(1),
    fontWeight: "normal",
    display: "flex",
  },
  primary: { color: theme.palette.primary.main },
  modifiedHighlight: { color: theme.palette.modifiedHighlight },
  warningRed: { color: theme.palette.warningRed },

  /*  styling */
  tableRowCell: {
    display: "flex",
    flexDirection: "row",
    justifyContent: "space-between",
    borderBottomWidth: "0px",
    alignItems: "center",
    "@media print": {
      padding: theme.spacing(0.5),
    },
    width: "100%",
  },
  tableRow: {
    width: "100%",
    display: "flex",
  },
  tableRowDesktop: {
    flexDirection: "row",
  },
  tableRowMobile: {
    flexDirection: "column",
  },
  tableSubCell: {
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
  },
  spacedSubCell: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
  },
  centeredText: {
    textAlign: "center",
  },
}));

function PlanHeaderRow() {
  const { classes } = useStyles();
  return (
    <TableRow className={clsx(classes.tableRow, classes.tableRowDesktop)}>
      <TableCell className={clsx(classes.tableRowCell)}>
        <span style={{ flex: 1 }}>Select</span>
        <span style={{ flex: 5 }}>DMP Title</span>
        <span style={{ flex: 3 }} className={classes.centeredText}>
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
  const { classes } = useStyles();

  const isCurrentlySelected = (id: DMPUserInternalId) =>
    selectedPlans.includes(id);

  const toggleSelected = () => {
    const id = plan.dmpUserInternalId;
    if (isCurrentlySelected(id)) removeSelectedPlan(id);
    else addSelectedPlan(id);
  };

  return (
    <TableRow
      className={clsx(
        classes.bottomBorder,
        classes.tableRow,
        classes.tableRowDesktop
      )}
      data-testid={plan.dmpId}
    >
      <TableCell className={clsx(classes.tableRowCell)}>
        <span style={{ flex: 1 }} className={classes.spacedSubCell}>
          <Checkbox
            color="primary"
            onChange={() => toggleSelected()}
            value={"dmpUserInternalId"}
            checked={isCurrentlySelected(plan.dmpUserInternalId)}
            inputProps={{ "aria-label": "Plan selection" }}
          />
        </span>

        <span style={{ flex: 5 }} className={classes.primary}>
          {plan.dmpTitle}
        </span>
        <span className={classes.tableSubCell} style={{ flex: 3 }}>
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
