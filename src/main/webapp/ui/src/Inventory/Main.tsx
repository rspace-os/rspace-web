import { makeStyles } from "tss-react/mui";
import * as React from "react";
import clsx from "clsx";
import styled from "@mui/system/styled";
import { lighten } from "@mui/material";

const useStyles = makeStyles()((theme) => ({
  main: {
    flexGrow: 1,
    minWidth: 0, // this is for the right hand panel 'title' ellipsis to work as expected
    height: "100%",
    // backgroundColor:  `hsl(${theme.palette.primary.background.hue}deg, ${theme.palette.primary.background.saturation}%, 98%)`,
    backgroundColor: lighten(theme.palette.primary.background, 0.98),
  },
  hiddenSidebar: {
    paddingTop: theme.spacing(7), // this for the header which includes the open sidebar button
  },
}));

type MainArgs = {
  children: React.ReactNode;
  sx?: Record<string, unknown>;
} & React.HTMLAttributes<HTMLElement>;

const StyledMain = styled("main")``;

export default React.forwardRef<HTMLElement, MainArgs>(function Main(
  { children, sx, ...htmlAttributes },
  ref,
) {
  const { classes } = useStyles();

  return (
    <StyledMain
      ref={ref}
      className={clsx(classes.main)}
      sx={sx}
      {...htmlAttributes}
    >
      {children}
    </StyledMain>
  );
});
