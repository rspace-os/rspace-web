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
  color?: React.ComponentProps<typeof IconButton>["color"];
};

type AnchorLinkProps = {
  component: "a";
  href: string;
  target: string;
  rel: string;
};

const IconLink = withStyles<
  React.ComponentProps<typeof IconButton> & AnchorLinkProps,
  { root: string }
>((theme, { color }) => ({
  root: {
    color: `${
      color === "primary" ? theme.palette.primary.main : color
    } !important`,
    cursor: "pointer",
    transition: "all .15s ease",
    "&:hover": {
      filter: "brightness(0.9)",
      // have to re-state to prevent ELN's a:hover red style from taking effect
      color: `${
        color === "primary" ? theme.palette.primary.main : color
      } !important`,
    },
    transform: "translateY(-2px)",
  },
}))((props) => {
  const rest: React.ComponentProps<typeof IconButton> = { ...props };
  delete rest.color;
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
