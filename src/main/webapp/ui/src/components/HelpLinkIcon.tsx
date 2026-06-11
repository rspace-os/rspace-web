import { type URL } from "../util/types";
import React from "react";
import IconButton from "@mui/material/IconButton";
import { svgIconClasses } from "@mui/material/SvgIcon";
import HelpIcon from "@mui/icons-material/Help";
import { useTheme } from "@mui/material/styles";
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

function IconLink({
  color,
  ...rest
}: Omit<React.ComponentProps<typeof IconButton>, "color"> &
  AnchorLinkProps & {
    color: React.ComponentProps<typeof IconButton>["color"] | "white";
  }): React.ReactNode {
  const theme = useTheme();
  const resolvedColor =
    color === "primary" ? theme.palette.primary.dark : color;
  return (
    <IconButton
      {...rest}
      sx={{
        color: `${resolvedColor} !important`,
        cursor: "pointer",
        transition: "all .15s ease",
        transform: "translateY(-2px)",
        "&:hover": {
          filter: "brightness(0.9)",
          // have to re-state to prevent ELN's a:hover red style from taking effect
          color: `${resolvedColor} !important`,
        },
        [`& .${svgIconClasses.root}`]: {
          color: `${resolvedColor} !important`,
        },
      }}
    />
  );
}

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
