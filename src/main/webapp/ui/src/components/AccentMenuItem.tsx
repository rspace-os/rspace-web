import CardHeader, { cardHeaderClasses } from "@mui/material/CardHeader";
import { cardMediaClasses } from "@mui/material/CardMedia";
import MenuItem, { menuItemClasses } from "@mui/material/MenuItem";
import { svgIconClasses } from "@mui/material/SvgIcon";
import { typographyClasses } from "@mui/material/Typography";
// biome-ignore lint/style/useImportType: initial biome migration
import { alpha, SxProps, Theme } from "@mui/system";
import React from "react";

type AccentMenuItemSlotProps = {
  title?: Record<string, unknown> & { sx?: SxProps<Theme> };
  subheader?: Record<string, unknown> & { sx?: SxProps<Theme> };
};

type AccentMenuItemArgs = {
  title: React.ReactNode;
  avatar?: React.ReactNode;
  subheader?: React.ReactNode;
  foregroundColor?:
    | string
    | {
        hue: number;
        saturation: number;
        lightness: number;
      };
  backgroundColor?:
    | string
    | {
        hue: number;
        saturation: number;
        lightness: number;
      };
  avatarBackgroundColor?: string;
  onClick?: (event: React.MouseEvent<HTMLButtonElement>) => void;
  onKeyDown?: (event: React.KeyboardEvent<HTMLButtonElement>) => void;
  compact?: boolean;
  disabled?: boolean;
  "aria-haspopup"?: "menu" | "dialog";
  titleTypographyProps?: {
    sx?: SxProps<Theme>;
  };

  /*
   * Use these properties to make the menu item a link.
   */

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  component?: React.ElementType<any>;
  href?: string;

  /*
   * These properties are dynamically added by the MUI Menu parent component
   */
  autoFocus?: boolean;
  tabIndex?: number;

  /*
   * Use this property to indicate that this menu item is the "current" item.
   * It will be styled with a slightly different background colour, and so may
   * not be obvious to all users, but will be exposed to screen readers as the
   * aria-current attribute. To find out more about what it means for an menu
   * item to be "current", see https://www.w3.org/TR/wai-aria-1.1/#aria-current.
   * Examples include the current page in a navigation menu, the current tab in
   * a tabbed interface, the applied sort order in a list, etc.
   */
  current?: boolean | "page" | "step" | "location" | "date" | "time";
  slotProps?: AccentMenuItemSlotProps;
};

/**
 * A menu item for the various menus in the application. It can be styled
 * according to the branding of a third-party integration, or to match the
 * accent colour of the current page.
 */
const AccentMenuItem = React.forwardRef<typeof MenuItem, AccentMenuItemArgs>(
  (
    {
      foregroundColor,
      backgroundColor,
      avatarBackgroundColor,
      compact,
      onClick,
      onKeyDown,
      disabled,
      autoFocus,
      tabIndex,
      "aria-haspopup": ariaHasPopup,
      title,
      subheader,
      avatar,
      titleTypographyProps,
      slotProps,
      component = "li",
      href,
      current,
    },
    ref,
  ) => (
    <MenuItem
      ref={ref}
      onKeyDown={onKeyDown}
      onClick={onClick}
      disabled={disabled}
      autoFocus={autoFocus}
      tabIndex={tabIndex}
      aria-haspopup={ariaHasPopup}
      component={component}
      href={href}
      selected={
        current === true ||
        current === "page" ||
        current === "step" ||
        current === "location" ||
        current === "date" ||
        current === "time"
      }
      aria-current={current}
      sx={(theme) => {
        const prefersMoreContrast = window.matchMedia("(prefers-contrast: more)").matches;
        const fg =
          typeof foregroundColor === "string"
            ? foregroundColor
            : foregroundColor
              ? `hsl(${foregroundColor.hue}deg, ${foregroundColor.saturation}%, ${foregroundColor.lightness}%, 100%)`
              : theme.palette.primary.contrastText;
        const bg =
          typeof backgroundColor === "string"
            ? backgroundColor
            : backgroundColor
              ? `hsl(${backgroundColor.hue}deg, ${backgroundColor.saturation}%, ${backgroundColor.lightness}%, 100%)`
              : theme.palette.primary.main;
        const avatarBg = avatarBackgroundColor ?? bg;
        return {
          margin: theme.spacing(1),
          padding: 0,
          borderRadius: "2px",
          border: prefersMoreContrast ? "2px solid #000" : "none",
          backgroundColor: prefersMoreContrast ? "#fff" : alpha(bg, 0.12),
          transition: "background-color ease-in-out .2s",
          "&:hover": {
            backgroundColor: prefersMoreContrast ? "#fff" : alpha(bg, 0.36),
          },
          [`&.${menuItemClasses.selected}`]: {
            backgroundColor: prefersMoreContrast ? "#fff" : alpha(bg, 0.5),
            "&:hover": {
              backgroundColor: prefersMoreContrast ? "#fff" : alpha(bg, 0.72),
            },
          },
          [`& .${cardHeaderClasses.root}`]: {
            padding: theme.spacing(compact ? 1 : 2),
          },
          [`& .${cardHeaderClasses.avatar}`]: {
            border: `${compact ? 3 : 4}px solid ${avatarBg}`,
            borderRadius: `${compact ? 4 : 6}px`,
            backgroundColor: avatarBg,
            color: fg,
            "& svg": {
              margin: "2px",
            },
          },
          [`& .${cardMediaClasses.root}`]: {
            width: 28,
            height: 28,
            borderRadius: "4px",
            margin: theme.spacing(0.25),
          },
          [`& .${svgIconClasses.root}`]: {
            width: 28,
            height: 28,
            padding: compact ? 0 : theme.spacing(0.25),
            background: bg,
            color: fg,
          },
          [`& .${typographyClasses.root}`]: {
            color: prefersMoreContrast ? "#000" : fg,
          },
          [`& .${cardHeaderClasses.content}`]: {
            marginRight: theme.spacing(2),
          },
          [`& .${cardHeaderClasses.title}`]: {
            fontSize: "1rem",
            fontWeight: 500,
          },
        };
      }}
    >
      <CardHeader
        title={title}
        avatar={avatar}
        subheader={subheader}
        slotProps={{
          ...slotProps,
          title: {
            ...(titleTypographyProps ?? {}),
            ...(slotProps?.title ?? {}),
          },
          subheader: {
            ...(slotProps?.subheader ?? {}),
            sx: {
              whiteSpace: "break-spaces",
              ...(slotProps?.subheader?.sx ?? {}),
            },
          },
        }}
      />
    </MenuItem>
  ),
);
AccentMenuItem.displayName = "AccentMenuItem";

export default AccentMenuItem;
