import { observer } from "mobx-react-lite";
import React from "react";
import Searchbar from "./components/Searchbar";
import SearchFeedback from "./components/SearchFeedback";
import { type SearchView } from "../../stores/definitions/Search";
import SearchDisplayControls from "./components/SearchDisplayControls";
import SearchParameterControls from "./components/SearchParameterControls";
import Grid from "@mui/material/Grid";
import { makeStyles } from "tss-react/mui";
import Divider from "@mui/material/Divider";
import SearchParameterChips from "./components/SearchParameterChips";
import HelpLinkIcon from "../../components/HelpLinkIcon";
import docLinks from "../../assets/DocLinks";

type MainSearchArgs = {
  size?: "small" | "default";
  TABS?: Array<SearchView>;
  handleSearch: (query: string) => void;
  searchbarAdornment?: React.ReactNode;
};

const useStyles = makeStyles()((theme) => ({
  middleSection: {
    paddingTop: 0,
  },
  grow: {
    flexGrow: 1,
  },
  divider: {
    margin: theme.spacing(0.75, 0),
  },
  searchParameterChips: {
    maxWidth: "100% !important",
  },
}));

function MainSearch({
  size = "default",
  handleSearch,
  TABS = ["LIST", "TREE", "CARD"],
  searchbarAdornment,
}: MainSearchArgs): React.ReactNode {
  const { classes } = useStyles();

  return (
    <Grid container direction="column" spacing={1}>
      <Grid item>
        <Grid
          container
          direction="row"
          wrap="nowrap"
          spacing={1}
          alignItems="center"
        >
          <Grid item className={classes.grow}>
            <Searchbar handleSearch={handleSearch} />
          </Grid>
          <Grid item>
            <HelpLinkIcon
              link={docLinks.search}
              title="Info on searching Inventory."
            />
          </Grid>
          {size === "small" && (
            <Grid item>
              <SearchDisplayControls TABS={TABS} />
            </Grid>
          )}
          {Boolean(searchbarAdornment) && (
            <Grid item>{searchbarAdornment}</Grid>
          )}
        </Grid>
      </Grid>
      <Grid item className={classes.middleSection}>
        <SearchParameterControls />
      </Grid>
      <Grid item className={classes.searchParameterChips}>
        <SearchParameterChips />
      </Grid>
      {size === "default" && (
        <>
          <Grid item>
            <Divider orientation="horizontal" className={classes.divider} />
          </Grid>
          <Grid item>
            <Grid container direction="row" wrap="nowrap" spacing={1}>
              <Grid item className={classes.grow}>
                <SearchFeedback />
              </Grid>
              <Grid item>
                <SearchDisplayControls TABS={TABS} />
              </Grid>
            </Grid>
          </Grid>
        </>
      )}
    </Grid>
  );
}

export default observer(MainSearch);
