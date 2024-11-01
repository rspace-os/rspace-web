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
import Divider from "@mui/material/Divider";

const useStyles = makeStyles()((theme, { rightAlignDds }) => ({
  dl: {
    display: "grid",
    gridTemplateColumns: "1fr 1fr",
    fontSize: "0.8rem",
    rowGap: "8px",
    margin: 0,
    marginBottom: "8px",
  },
  dt: {
    color: theme.palette.text.secondary,
    fontWeight: "600",
    marginRight: theme.spacing(2),
  },
  ddBelow: {
    gridColumn: "1 / span 2",
    marginTop: "-10px",
  },
  dd: {
    marginInlineStart: 0,
    justifySelf: "end",
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
  const { classes } = useStyles({ rightAlignDds });

  return (
    <Dl className={classes.dl} sx={sx}>
      {content.map(
        ({ label, value, below = false, reducedPadding = false }, i) => (
          <>
            {i > 0 && dividers && (
              <Divider
                orientation="horizontal"
                sx={{
                  gridColumn: "1 / span 2",
                }}
                aria-hidden="true"
                component="div"
              />
            )}
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
          </>
        )
      )}
    </Dl>
  );
}

export default (observer(DescriptionList): ComponentType<DescriptionListArgs>);
