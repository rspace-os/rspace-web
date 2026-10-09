import HelpIcon from "@mui/icons-material/Help";
import IconButton from "@mui/material/IconButton";
import { svgIconClasses } from "@mui/material/SvgIcon";
import { useTheme } from "@mui/material/styles";
import type React from "react";
import type { URL } from "../util/types";
import CustomTooltip from "./CustomTooltip";

type HelpIconProps = {
  link: URL;
  title: string;
  size?: "small" | "medium" | "large";
  color?: React.ComponentProps<typeof IconButton>["color"] | "white";
  /**
   * Overrides the icon's rendered size to track the font-size of wherever it's placed (e.g.
   * "inherit" to exactly match surrounding text) instead of one of MUI's fixed `size` presets.
   */
  fontSize?: string;
};

type AnchorLinkProps = {
  component: "a";
  href: string;
  target: string;
  rel: string;
};

function IconLink({
  color,
  fontSize,
  ...rest
}: Omit<React.ComponentProps<typeof IconButton>, "color"> &
  AnchorLinkProps & {
    color: React.ComponentProps<typeof IconButton>["color"] | "white";
    fontSize?: string;
  }): React.ReactNode {
  const theme = useTheme();
  const resolvedColor = color === "primary" ? theme.palette.primary.dark : color;
  return (
    <IconButton
      {...rest}
      sx={{
        color: `${resolvedColor} !important`,
        cursor: "pointer",
        transition: "all .15s ease",
        transform: "translateY(-2px)",
        ...(fontSize ? { padding: 0 } : {}),
        "&:hover": {
          filter: "brightness(0.9)",
          // have to re-state to prevent ELN's a:hover red style from taking effect
          color: `${resolvedColor} !important`,
        },
        [`& .${svgIconClasses.root}`]: {
          color: `${resolvedColor} !important`,
          ...(fontSize ? { fontSize } : {}),
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
  fontSize,
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
        fontSize={fontSize}
        aria-label={title}
      >
        <HelpIcon />
      </IconLink>
    </CustomTooltip>
  );
}
