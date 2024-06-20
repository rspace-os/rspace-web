//@flow

import React, { type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import SubSampleModel from "../../../stores/models/SubSampleModel";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import Grid from "@mui/material/Grid";
import Collapse from "@mui/material/Collapse";
import ExpandCollapseIcon from "../../../components/ExpandCollapseIcon";
import IconButton from "@mui/material/IconButton";

const Wrapper = ({ children }: {| children: Node |}) => {
  const [sectionOpen, setSectionOpen] = React.useState(true);
  return (
    <Grid container direction="row" flexWrap="nowrap" spacing={1}>
      <Grid item sx={{ pl: 0, ml: -2 }}>
        <IconButton
          onClick={() => setSectionOpen(!sectionOpen)}
          sx={{ p: 1.25 }}
        >
          <ExpandCollapseIcon open={sectionOpen} />
        </IconButton>
      </Grid>
      <Grid item>
        <Collapse in={sectionOpen} collapsedSize={44}>
          {children}
        </Collapse>
      </Grid>
    </Grid>
  );
};

type SubsampleDetailsArgs = {|
  subsample: InventoryRecord | null,
|};

function SubsampleDetails({ subsample }: SubsampleDetailsArgs) {
  if (subsample === null) return <Wrapper>No subsamples</Wrapper>;

  if (!subsample instanceof SubSampleModel)
    throw new Error("All Subsamples must be instances of SubSampleModel");
  return <Wrapper>{subsample.name}</Wrapper>;
}

export default (observer(
  SubsampleDetails
): ComponentType<SubsampleDetailsArgs>);
