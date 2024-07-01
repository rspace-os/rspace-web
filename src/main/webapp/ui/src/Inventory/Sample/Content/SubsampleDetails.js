//@flow

import React, { type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import SubSampleModel from "../../../stores/models/SubSampleModel";
import Grid from "@mui/material/Grid";
import Collapse from "@mui/material/Collapse";
import ExpandCollapseIcon from "../../../components/ExpandCollapseIcon";
import IconButton from "@mui/material/IconButton";
import Toolbar from "@mui/material/Toolbar";
import AppBar from "@mui/material/AppBar";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardActions from "@mui/material/CardActions";
import Link from "@mui/material/Link";
import { styled, useTheme, darken, alpha } from "@mui/material/styles";
import LocationField from "../../components/Fields/Location";
import Box from "@mui/material/Box";
import GlobalId from "../../../components/GlobalId";
import QuantityField from "../../Subsample/Fields/Quantity";
import Stack from "@mui/material/Stack";
import Notes from "../../Subsample/Fields/Notes/Notes";
import MobileStepper from "@mui/material/MobileStepper";
import Button, { buttonClasses } from "@mui/material/Button";
import KeyboardArrowLeft from "@mui/icons-material/KeyboardArrowLeft";
import KeyboardArrowRight from "@mui/icons-material/KeyboardArrowRight";
import { type Search } from "../../../stores/definitions/Search";
import { doNotAwait, modulo } from "../../../util/Util";
import { svgIconClasses } from "@mui/material/SvgIcon";
import { Link as ReactRouterLink } from "react-router-dom";

const CustomStepper = styled(MobileStepper)(({ theme }) => ({
  backgroundColor: theme.palette.record.subSample.lighter,
  borderBottomLeftRadius: "4px",
  borderBottomRightRadius: "4px",
  border: `2px solid ${theme.palette.record.subSample.bg}`,
  borderTop: "none",
  color: alpha(darken(theme.palette.record.subSample.bg, 0.5), 0.7),
  fontWeight: "700",
  letterSpacing: "0.03em",
  [`& .${buttonClasses.root}`]: {
    [`& .${svgIconClasses.root}`]: {
      color: theme.palette.record.subSample.bg,
    },
    [`&.${buttonClasses.disabled}`]: {
      opacity: 0.3,
    },
  },
}));

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
  search: Search,
|};

function SubsampleDetails({ search }: SubsampleDetailsArgs) {
  const theme = useTheme();

  const subsample = search.activeResult;
  if (subsample === null || typeof subsample === "undefined")
    return <Wrapper>No subsamples</Wrapper>;
  const index = search.filteredResults.findIndex(
    (x) => x.globalId === subsample.globalId
  );

  if (!(subsample instanceof SubSampleModel))
    throw new Error("All Subsamples must be instances of SubSampleModel");

  return (
    <Wrapper>
      <>
        <Card
          variant="outlined"
          sx={{
            border: `2px solid ${theme.palette.record.subSample.bg}`,
            borderBottomLeftRadius: 0,
            borderBottomRightRadius: 0,
          }}
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
            <Stack spacing={2}>
              <LocationField fieldOwner={subsample} />
              <QuantityField
                fieldOwner={subsample}
                quantityCategory={subsample.quantityCategory}
                onErrorStateChange={() => {}}
              />
              <Notes record={subsample} onErrorStateChange={() => {}} />
            </Stack>
          </CardContent>
          <CardActions>
            <Link component={ReactRouterLink} to={subsample.permalinkURL}>
              See the full details of <strong>{subsample.name}</strong>
            </Link>
          </CardActions>
        </Card>
        <CustomStepper
          variant="text"
          steps={search.count}
          activeStep={
            index + search.fetcher.pageSize * search.fetcher.pageNumber
          }
          position="static"
          nextButton={
            <Button
              size="small"
              onClick={doNotAwait(async () => {
                if (index + 1 > search.filteredResults.length - 1)
                  await search.setPage(search.fetcher.pageNumber + 1);
                await search.setActiveResult(
                  search.filteredResults[(index + 1) % search.fetcher.pageSize]
                );
              })}
              disabled={
                index +
                  search.fetcher.pageSize * search.fetcher.pageNumber +
                  1 >=
                search.count
              }
            >
              <KeyboardArrowRight />
            </Button>
          }
          backButton={
            <Button
              size="small"
              onClick={doNotAwait(async () => {
                if (index === 0)
                  await search.setPage(search.fetcher.pageNumber - 1);
                await search.setActiveResult(
                  search.filteredResults[
                    modulo(index - 1, search.fetcher.pageSize)
                  ]
                );
              })}
              disabled={
                index + search.fetcher.pageSize * search.fetcher.pageNumber ===
                0
              }
            >
              <KeyboardArrowLeft />
            </Button>
          }
        />
      </>
    </Wrapper>
  );
}

export default (observer(
  SubsampleDetails
): ComponentType<SubsampleDetailsArgs>);
