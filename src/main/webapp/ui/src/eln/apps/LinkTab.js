//@flow strict

import React, { type ComponentType } from "react";
import Tab from "@mui/material/Tab";
import { styled } from "@mui/material/styles";

type LinkTabArgs = {|
  label: string,
  href: string,
  tabIndex?: -1 | 0,
  disabled?: boolean,
|};

const LinkTab: ComponentType<LinkTabArgs> = styled((props) => (
  <Tab disableRipple {...props} />
))(({ theme }) => ({
  textTransform: "none",
  minWidth: 0,
  [theme.breakpoints.up("sm")]: {
    minWidth: 0,
  },
  fontWeight: theme.typography.fontWeightRegular,
  color: theme.palette.standardIcon.main,
  borderRadius: "4px",
  paddingTop: theme.spacing(0.5),
  paddingBottom: theme.spacing(0.5),
  paddingLeft: theme.spacing(1.5),
  paddingRight: theme.spacing(1.5),
  letterSpacing: "0.05em",
  minHeight: 40,
  cursor: "pointer",
  marginRight: theme.spacing(0.5),
  transition: "all .3s ease",
  "&:hover": {
    opacity: 1,
    background: theme.palette.hover.iconButton,
  },
  "&.Mui-selected": {
    fontWeight: theme.typography.fontWeightMedium,
  },
  "&.Mui-focusVisible": {
    backgroundColor: "#d1eaff",
  },
}));

export default LinkTab;
