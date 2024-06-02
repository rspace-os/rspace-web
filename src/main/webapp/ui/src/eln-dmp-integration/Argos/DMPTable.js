// @flow

import React, { type Node, type ComponentType, useState } from "react";
import Radio from "@mui/material/Radio";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import { observer } from "mobx-react-lite";
import {
  type PlanSummary,
  type PlanSummaryAdustableColumnLabel,
  planSummaryAdustableColumnLabels,
} from "./PlanSummary";
import { mapNullable } from "../../util/Util";
import AdjustableHeadCell from "../../components/Tables/AdjustableHeadCell";
import AdjustableCell from "../../components/Tables/AdjustableCell.js";
import { type UseState } from "../../util/types";

type PlanHeaderRowArgs = {|
  currentColumn: PlanSummaryAdustableColumnLabel,
  setCurrentColumn: (PlanSummaryAdustableColumnLabel) => void,
|};

const PlanHeaderRow = function PlanHeaderRow({
  currentColumn,
  setCurrentColumn,
}: PlanHeaderRowArgs) {
  return (
    <TableRow>
      <TableCell>Select</TableCell>
      <TableCell>Label</TableCell>
      <AdjustableHeadCell
        options={planSummaryAdustableColumnLabels}
        onChange={setCurrentColumn}
        current={currentColumn}
      />
    </TableRow>
  );
};

type BodyRowArgs = {|
  plan: PlanSummary,
  selectedPlan: ?PlanSummary,
  setSelectedPlan: (?PlanSummary) => void,
  currentColumn: PlanSummaryAdustableColumnLabel,
|};

function PlanRow({
  plan,
  selectedPlan,
  setSelectedPlan,
  currentColumn,
}: BodyRowArgs) {
  const isCurrentSelection = Boolean(
    mapNullable((selected) => plan.isEqual(selected), selectedPlan)
  );

  return (
    <TableRow>
      <TableCell>
        <Radio
          color="primary"
          onChange={() => setSelectedPlan(plan)}
          value={isCurrentSelection}
          checked={isCurrentSelection}
          inputProps={{ "aria-label": "Plan selection" }}
        />
      </TableCell>
      <TableCell>{plan.getLabel()}</TableCell>
      <AdjustableCell dataSource={plan} selectedOption={currentColumn} />
    </TableRow>
  );
}

type TableArgs = {|
  plans: Array<PlanSummary>,
  selectedPlan: ?PlanSummary,
  setSelectedPlan: (?PlanSummary) => void,
|};

function DMPTable({ plans, selectedPlan, setSelectedPlan }: TableArgs): Node {
  const [
    currentColumn,
    setCurrentColumn,
  ]: UseState<PlanSummaryAdustableColumnLabel> = useState("ID");
  return (
    <TableContainer style={{ overflowX: "hidden" }}>
      <Table size="small">
        <TableHead style={{ maxWidth: "100%" }}>
          <PlanHeaderRow
            currentColumn={currentColumn}
            setCurrentColumn={setCurrentColumn}
          />
        </TableHead>
        <TableBody style={{ maxWidth: "100%" }}>
          {plans.map((plan, i) => (
            <PlanRow
              key={i}
              plan={plan}
              selectedPlan={selectedPlan}
              setSelectedPlan={setSelectedPlan}
              currentColumn={currentColumn}
            />
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

export default (observer(DMPTable): ComponentType<TableArgs>);
