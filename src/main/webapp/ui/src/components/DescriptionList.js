// @flow strict

/*
 * General-purpose component that abstracts over the <dl>, <dd>, and <dt> tags.
 *
 * This component MUST ONLY have a dependency on this directory and ../util.
 */

import React, { type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import { makeStyles } from "tss-react/mui";
import clsx from "clsx";
import { styled } from "@mui/material/styles";

const useStyles = makeStyles()((theme, { dividers, rightAlignDds }) => ({
  dl: {
    display: "flex",
    flexDirection: "column",
    flexWrap: "unset",
    margin: 0,
    "& > span:nth-of-type(n+2)": {
      borderTop: dividers ? theme.borders.descriptionList : "none",
    },
  },
  dt: {
    color: theme.palette.text.secondary,
    fontWeight: "600",
    marginRight: theme.spacing(2),
  },
  span: {
    display: "flex",
    flexDirection: "row",
    justifyContent: rightAlignDds ? "space-between" : "unset",
    alignItems: "center",
    padding: dividers
      ? theme.spacing(0.75, 1, 0.675)
      : theme.spacing(0, 0, 1, 0),
    fontSize: "0.8rem",
    minHeight: "unset",
  },
  spanBelow: {
    flexDirection: "column",
  },
  dtBelow: {
    alignSelf: "flex-start",
  },
  ddBelow: {
    alignSelf: "flex-end",
  },
  dd: {
    margin: 0,
  },
  spanReducedPadding: {
    paddingTop: "3px !important",
    paddingBottom: "3px !important",
  },
}));

type DescriptionListArgs = {|
  content: Array<{|
    label: string,
    value: Node,
    below?: boolean,
    reducedPadding?: boolean,
  |}>,
  dividers?: boolean,
  rightAlignDds?: boolean,
  sx?: { ... },
|};

// This is used so that we can attach sx to the <dl>
const Dl = styled("dl")``;

/**
 * This component provides some means for the contents to be styled using the
 * MUI `sx` prop.
 *
 *  - When `below` is true, the <dt> and <dd> have the class .below. As such,
 *    they can styled by passing an `sx` object that selects them:
 *
 *      <DescriptionList
 *        sx={{
 *          "& dd.below": {
 *            width: "100%",
 *          }
 *        }}
 *        ...
 *      />
 *
 */
function DescriptionList({
  content,
  dividers = false,
  rightAlignDds = false,
  sx,
}: DescriptionListArgs): Node {
  const { classes } = useStyles({ dividers, rightAlignDds });

  return (
    <Dl className={classes.dl} sx={sx}>
      {content.map(
        ({ label, value, below = false, reducedPadding = false }) => (
          <span
            key={label}
            className={clsx(
              classes.span,
              below && classes.spanBelow,
              reducedPadding && classes.spanReducedPadding
            )}
          >
            <dt
              className={clsx(
                classes.dt,
                below && classes.dtBelow,
                below && "below"
              )}
            >
              {label}
            </dt>
            <dd
              className={clsx(
                classes.dd,
                below && classes.ddBelow,
                below && "below"
              )}
            >
              {value}
            </dd>
          </span>
        )
      )}
    </Dl>
  );
}

export default (observer(DescriptionList): ComponentType<DescriptionListArgs>);
