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
|};

function DescriptionList({
  content,
  dividers = false,
  rightAlignDds = false,
}: DescriptionListArgs): Node {
  const { classes } = useStyles({ dividers, rightAlignDds });

  return (
    <dl className={classes.dl}>
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
            <dt className={clsx(classes.dt, below && classes.dtBelow)}>
              {label}
            </dt>
            <dd className={clsx(classes.dd, below && classes.ddBelow)}>
              {value}
            </dd>
          </span>
        )
      )}
    </dl>
  );
}

export default (observer(DescriptionList): ComponentType<DescriptionListArgs>);
