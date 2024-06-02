//@flow

import React, { type ComponentType } from "react";
import Box from "@mui/material/Box";
import { withStyles } from "Styles";
import Typography from "@mui/material/Typography";
// $FlowExpectedError[cannot-resolve-module] An .svg, not a JS module
import NoResultsSvg from "/src/assets/graphics/NoResults.svg";
import Divider from "@mui/material/Divider";
import docLinks from "../../../assets/DocLinks";

type NoResultsArgs = {|
  query: ?string,
|};

const NoResults: ComponentType<NoResultsArgs> = withStyles<
  NoResultsArgs,
  {
    root: string,
    noresults: string,
    help: string,
    divider: string,
    image: string,
  }
>((theme) => ({
  root: {
    fontWeight: 700,
    fontSize: "1.6rem",
    color: theme.palette.lightestGrey,
    display: "flex",
    flexDirection: "column",
    height: "100%",
    alignItems: "center",
    marginBottom: theme.spacing(1),
  },
  noresults: {
    color: theme.palette.primary.placeholderText,
  },
  help: {
    textAlign: "center",
    color: theme.palette.primary.placeholderText,
    marginTop: theme.spacing(2),
    fontSize: "1rem",
    maxWidth: "20em",
  },
  divider: {
    width: "75%",
    marginTop: theme.spacing(2),
  },
  image: {
    flexGrow: 1,
    background: `center / contain no-repeat url("${NoResultsSvg}")`,
    width: "100%",
    minHeight: "50px",
    maxHeight: "200px",
  },
}))(({ classes, query }) => (
  <Box className={classes.root}>
    <div className={classes.image}></div>
    <span className={classes.noresults}>No results.</span>
    <Typography className={classes.help}>
      Try searching for a different term, or use the advanced search to change
      search filters.
    </Typography>
    {query !== "" && (
      <>
        <Divider orientation="horizontal" className={classes.divider} />
        <Typography className={classes.help}>
          For more information on using Lucene queries, see{" "}
          <a href={docLinks.luceneSyntax} rel="noreferrer" target="_blank">
            advanced search
          </a>{" "}
          and the related{" "}
          <a
            href="https://lucene.apache.org/core/2_9_4/queryparsersyntax.html"
            rel="noreferrer"
            target="_blank"
          >
            Apache page
          </a>
          .
        </Typography>
      </>
    )}
  </Box>
));

NoResults.displayName = "NoResults";
export default NoResults;
