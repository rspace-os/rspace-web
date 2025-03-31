import React from "react";
import { styled } from "@mui/material/styles";
import MenuItem from "@mui/material/MenuItem";
import CardHeader from "@mui/material/CardHeader";
import { alpha, SxProps, Theme } from "@mui/system";

type AccentMenuItemArgs = {
  title: string;
  avatar?: React.ReactNode;
  subheader?: React.ReactNode;
  foregroundColor?:
    | string
    | { hue: number; saturation: number; lightness: number };
  backgroundColor?:
    | string
    | { hue: number; saturation: number; lightness: number };
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
  //component?: "a";
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
};

/**
 * A menu item for the various menus in the application. It can be styled
 * according to the branding of a third-party integration, or to match the
 * accent colour of the current page.
 */
export default styled(
  // eslint-disable-next-line react/display-name -- Just a styled wrapper around MenuItem
  React.forwardRef<
    typeof MenuItem,
    AccentMenuItemArgs & { className?: string }
  >(
    (
      {
        foregroundColor: _foregroundColor,
        backgroundColor: _backgroundColor,
        compact: _compact,
        className,
        onClick,
        onKeyDown,
        disabled,
        autoFocus,
        tabIndex,
        // eslint-disable-next-line react/prop-types -- flow will handle this just fine
        "aria-haspopup": ariaHasPopup,
        title,
        subheader,
        avatar,
        titleTypographyProps,
        component = "li",
        href,
        current,
      },
      ref
    ) => (
      <MenuItem
        ref={ref}
        className={className}
        onKeyDown={onKeyDown}
        onClick={onClick}
        disabled={disabled}
        //eslint-disable-next-line jsx-a11y/no-autofocus
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
      >
        <CardHeader
          title={title}
          avatar={avatar}
          subheader={subheader}
          titleTypographyProps={titleTypographyProps}
          subheaderTypographyProps={{
            sx: {
              whiteSpace: "break-spaces",
            },
          }}
        />
      </MenuItem>
    )
  )
)(
  ({
    theme,
    backgroundColor = theme.palette.primary.main,
    foregroundColor = theme.palette.primary.contrastText,
    compact,
  }) => {
    const prefersMoreContrast = window.matchMedia(
      "(prefers-contrast: more)"
    ).matches;
    const fg =
      typeof foregroundColor === "string"
        ? foregroundColor
        : `hsl(${foregroundColor.hue}deg, ${foregroundColor.saturation}%, ${foregroundColor.lightness}%, 100%)`;
    const bg =
      typeof backgroundColor === "string"
        ? backgroundColor
        : `hsl(${backgroundColor.hue}deg, ${backgroundColor.saturation}%, ${backgroundColor.lightness}%, 100%)`;
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
      "&.Mui-selected": {
        backgroundColor: prefersMoreContrast ? "#fff" : alpha(bg, 0.5),
        "&:hover": {
          backgroundColor: prefersMoreContrast ? "#fff" : alpha(bg, 0.72),
        },
      },
      "& .MuiCardHeader-root": {
        padding: theme.spacing(compact ? 1 : 2),
      },
      "& .MuiCardHeader-avatar": {
        border: `${compact ? 3 : 4}px solid ${bg}`,
        borderRadius: `${compact ? 4 : 6}px`,
        backgroundColor: bg,
        "& svg": {
          margin: "2px",
        },
      },
      "& .MuiCardMedia-root": {
        width: compact ? 28 : 36,
        height: compact ? 28 : 36,
        borderRadius: "4px",
        margin: theme.spacing(0.25),
      },
      "& .MuiSvgIcon-root": {
        width: compact ? 28 : 36,
        height: compact ? 28 : 36,
        background: bg,
        padding: theme.spacing(0.5),
        color: fg,
      },
      "& .MuiTypography-root": {
        color: prefersMoreContrast ? "#000" : fg,
      },
      "& .MuiCardHeader-content": {
        marginRight: theme.spacing(2),
      },
      "& .MuiCardHeader-title": {
        fontSize: "1rem",
        fontWeight: 500,
      },
    };
  }
);
