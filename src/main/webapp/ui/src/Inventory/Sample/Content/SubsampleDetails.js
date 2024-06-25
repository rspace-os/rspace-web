//@flow

import React, { type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import SubSampleModel from "../../../stores/models/SubSampleModel";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import Grid from "@mui/material/Grid";
import Collapse from "@mui/material/Collapse";
import ExpandCollapseIcon from "../../../components/ExpandCollapseIcon";
import IconButton from "@mui/material/IconButton";
import Toolbar from "@mui/material/Toolbar";
import AppBar from "@mui/material/AppBar";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import { useTheme } from "@mui/material/styles";
import LocationField from "../../components/Fields/Location";
import Box from "@mui/material/Box";
import GlobalId from "../../../components/GlobalId";

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
      <Grid item flexGrow={1}>
        <Collapse in={sectionOpen} collapsedSize={50}>
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
  const theme = useTheme();

  if (subsample === null) return <Wrapper>No subsamples</Wrapper>;

  if (!(subsample instanceof SubSampleModel))
    throw new Error("All Subsamples must be instances of SubSampleModel");
  return (
    <Wrapper>
      <Card
        variant="outlined"
        sx={{ border: `2px solid ${theme.palette.record.subSample.bg}` }}
      >
        <AppBar
          position="relative"
          open={true}
          sx={{
            backgroundColor: theme.palette.record.subSample.bg,
            boxShadow: "none",
          }}
        >
          <Toolbar
            variant="dense"
            disableGutters
            sx={{
              px: 1.5,
            }}
          >
            {subsample.name}
            <Box flexGrow={1}></Box>
            <GlobalId record={subsample} />
          </Toolbar>
        </AppBar>
        <CardContent>
          <LocationField fieldOwner={subsample} />
        </CardContent>
      </Card>
    </Wrapper>
  );
}

export default (observer(
  SubsampleDetails
): ComponentType<SubsampleDetailsArgs>);
