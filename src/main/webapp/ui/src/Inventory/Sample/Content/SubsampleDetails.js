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
import Typography from "@mui/material/Typography";
import ImageField from "../../components/Fields/Image";

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
  const cardId = React.useId();

  /*
   * Below the details card are two buttons that allow the user to enumerate
   * through the subsamples. This state variable is true whilst the onClick
   * handlers for those buttons are doing their processing. This includes not
   * just updating search.activeResult but also fetching the previous/next
   * page of results if the activeResult is currently the first/last result
   * in the page and the previous/next button has been tapped, respectively.
   */
  const [processingCardNav, setProcessingCardNav] = React.useState(false);

  /*
   * If the search results have changed, then we want to update
   * search.activeResult, and consequently the subsamples whose details we
   * are showing, so that we don't end up showing the details of a subsample
   * that is no longer visible in the table of results.
   */
  React.useEffect(() => {
    /*
     * We don't change the activeResult if processingCardNav is true
     * because otherwise when the user is currently viewing the details
     * of the first subsample in the page and presses the previous button,
     * they would end up viewing the first result of the previous page and
     * not the last result of that previous page which is what they intend.
     */
    if (!processingCardNav) void search.setActiveResult();
  }, [search.filteredResults]);

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
          role="region"
          aria-label="Subsample details"
          id={cardId}
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
              <ImageField fieldOwner={subsample} />
              <QuantityField
                fieldOwner={subsample}
                quantityCategory={subsample.quantityCategory}
                onErrorStateChange={() => {}}
              />
              <Notes record={subsample} onErrorStateChange={() => {}} />
            </Stack>
          </CardContent>
          <CardActions>
            <Typography align="center" sx={{ width: "100%" }}>
              <Link component={ReactRouterLink} to={subsample.permalinkURL}>
                See full details of <strong>{subsample.name}</strong>
              </Link>
            </Typography>
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
              aria-controls={cardId}
              size="small"
              onClick={doNotAwait(async () => {
                setProcessingCardNav(true);
                try {
                  if (index + 1 > search.filteredResults.length - 1)
                    await search.setPage(search.fetcher.pageNumber + 1);
                  await search.setActiveResult(
                    search.filteredResults[
                      (index + 1) % search.fetcher.pageSize
                    ]
                  );
                } finally {
                  setProcessingCardNav(false);
                }
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
              aria-controls={cardId}
              size="small"
              onClick={doNotAwait(async () => {
                setProcessingCardNav(true);
                try {
                  if (index === 0)
                    await search.setPage(search.fetcher.pageNumber - 1);
                  await search.setActiveResult(
                    search.filteredResults[
                      modulo(index - 1, search.fetcher.pageSize)
                    ]
                  );
                } finally {
                  setProcessingCardNav(false);
                }
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
