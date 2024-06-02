// @flow

import React, { type Node, type ComponentType } from "react";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import { observer } from "mobx-react-lite";
import { type Plan } from "./DMPDialog";
import DOMPurify from "dompurify";
import Radio from "@mui/material/Radio";
import useViewportDimensions from "../../util/useViewportDimensions";

const ResponsivePlanHeaderRow = observer(() => {
  const { isViewportSmall } = useViewportDimensions();
  return (
    <TableRow>
      <TableCell>Select</TableCell>
      <TableCell>Title</TableCell>
      {!isViewportSmall && (
        <>
          <TableCell>ID</TableCell>
          <TableCell>Description</TableCell>
        </>
      )}
    </TableRow>
  );
});

type BodyRowArgs = {|
  plan: Plan,
  selectedPlan: ?Plan,
  setSelectedPlan: (?Plan) => void,
|};

const ResponsivePlanRow = observer(
  ({ plan, selectedPlan, setSelectedPlan }: BodyRowArgs) => {
    const { isViewportSmall } = useViewportDimensions();
    const isCurrentSelection = plan.id === selectedPlan?.id;

    const toggleSelected = () => {
      setSelectedPlan(isCurrentSelection ? null : plan);
    };

    const formattedDescription = () => {
      const sanitized = DOMPurify.sanitize(plan.description);
      return {
        __html: `${sanitized.substring(0, 200)} ${
          sanitized.length > 200 ? "..." : ""
        }`,
      };
    };

    return (
      <TableRow data-testid={plan.id}>
        <TableCell>
          <Radio
            color="primary"
            onChange={() => toggleSelected()}
            value={isCurrentSelection}
            checked={isCurrentSelection}
            inputProps={{ "aria-label": "Plan selection" }}
          />
        </TableCell>
        <TableCell>{plan.title}</TableCell>
        {!isViewportSmall && (
          <>
            <TableCell>{plan.id}</TableCell>
            <TableCell>
              <span dangerouslySetInnerHTML={formattedDescription()}></span>
            </TableCell>
          </>
        )}
      </TableRow>
    );
  }
);

type TableArgs = {|
  plans: Array<Plan>,
  selectedPlan: ?Plan,
  setSelectedPlan: (?Plan) => void,
|};

function DMPTable({ plans, selectedPlan, setSelectedPlan }: TableArgs): Node {
  return (
    <TableContainer style={{ overflowX: "hidden" }}>
      <Table size="small">
        <TableHead style={{ maxWidth: "100%" }}>
          <ResponsivePlanHeaderRow />
        </TableHead>
        <TableBody style={{ maxWidth: "100%" }} aria-live="polite">
          {plans.map((plan, i) => (
            <ResponsivePlanRow
              key={i}
              plan={plan}
              selectedPlan={selectedPlan}
              setSelectedPlan={setSelectedPlan}
            />
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

export default (observer(DMPTable): ComponentType<TableArgs>);
