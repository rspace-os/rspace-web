import React from "react";
import { observer } from "mobx-react-lite";
import { makeStyles } from "tss-react/mui";
import clsx from "clsx";
import { styled } from "@mui/material/styles";
import Divider from "@mui/material/Divider";

export const TwoColumnDl = styled("dl")(() => ({
  display: "grid",
  gridTemplateColumns: "1fr 1fr",
}));

const useStyles = makeStyles()((theme) => ({
  dl: {
    rowGap: theme.spacing(1),
    fontSize: "0.8rem",
    margin: 0,
    marginBottom: theme.spacing(1),
  },
  dt: {
    color: theme.palette.text.secondary,
    fontWeight: "600",
    marginRight: theme.spacing(2),
    alignSelf: "center",
  },
  dtReducedPadding: {
    marginTop: `-${theme.spacing(1)}`,
    marginBottom: `-${theme.spacing(1)}`,
  },
  dd: {
    marginInlineStart: 0,
    justifySelf: "end",
  },
  ddReducedPadding: {
    marginTop: `-${theme.spacing(1)}`,
    marginBottom: `-${theme.spacing(1)}`,
  },
  ddBelow: {
    gridColumn: "1 / span 2",
    marginTop: "-10px",
  },
}));

type DescriptionListArgs = {
  content: Array<{
    label: string;
    value: React.ReactNode;
    below?: boolean;
    reducedPadding?: boolean;
  }>;
  dividers?: boolean;
  sx?: object;
};

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
  sx,
}: DescriptionListArgs): React.ReactNode {
  const { classes } = useStyles();

  return (
    <TwoColumnDl sx={sx} className={classes.dl}>
      {content.map(
        ({ label, value, below = false, reducedPadding = false }, i) => (
          <React.Fragment key={i}>
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
                reducedPadding && classes.dtReducedPadding,
                below && "below"
              )}
            >
              {label}
            </dt>
            <dd
              className={clsx(
                classes.dd,
                reducedPadding && classes.ddReducedPadding,
                below && classes.ddBelow,
                below && "below"
              )}
            >
              {value}
            </dd>
          </React.Fragment>
        )
      )}
    </TwoColumnDl>
  );
}

/**
 * General-purpose component that abstracts over the <dl>, <dd>, and <dt> tags.
 *
 * This component MUST ONLY have a dependency on this directory and ../util.
 */
export default observer(DescriptionList);
