import { type URL } from "../util/types";
import React from "react";
import IconButton from "@mui/material/IconButton";
import HelpIcon from "@mui/icons-material/Help";
import { withStyles } from "Styles";
import CustomTooltip from "./CustomTooltip";

type HelpIconProps = {
  link: URL;
  title: string;
  size?: "small" | "medium" | "large";
  color?: React.ComponentProps<typeof IconButton>["color"] | "white";
};

type AnchorLinkProps = {
  component: "a";
  href: string;
  target: string;
  rel: string;
};

const IconLink = withStyles<
  Omit<React.ComponentProps<typeof IconButton>, "color"> &
    AnchorLinkProps & {
      color: React.ComponentProps<typeof IconButton>["color"] | "white";
    },
  { root: string }
>((theme, { color }) => ({
  root: {
    color: `${
      color === "primary" ? theme.palette.primary.dark : color
    } !important`,
    cursor: "pointer",
    transition: "all .15s ease",
    "&:hover": {
      filter: "brightness(0.9)",
      // have to re-state to prevent ELN's a:hover red style from taking effect
      color: `${
        color === "primary" ? theme.palette.primary.dark : color
      } !important`,
    },
    transform: "translateY(-2px)",
    "& .MuiSvgIcon-root": {
      color: `${
        color === "primary" ? theme.palette.primary.dark : color
      } !important`,
    },
  },
}))(({ color: _color, ...rest }) => {
  return <IconButton {...rest} />;
});

/**
 * A simple question mark icon button for linking to documentation.
 */
export default function HelpLinkIcon({
  link,
  title,
  size = "small",
  color = "primary",
}: HelpIconProps): React.ReactNode {
  return (
    <CustomTooltip title={title}>
      <IconLink
        component="a"
        href={link}
        target="_blank"
        rel="noreferrer"
        size={size}
        color={color}
        aria-label={title}
      >
        <HelpIcon />
      </IconLink>
    </CustomTooltip>
  );
}
