//@flow

import React, { type Node } from "react";
import IconButtonWithTooltip from "./IconButtonWithTooltip";
import Popover from "@mui/material/Popover";
import Alert from "@mui/lab/Alert";
import Stack from "@mui/material/Stack";
import { styled } from "@mui/material/styles";
import AlertTitle from "@mui/lab/AlertTitle";
import Link from "@mui/material/Link";
import SvgIcon from "@mui/material/SvgIcon";
import AccentMenuItem from "./AccentMenuItem";

const StyledPopover = styled(
  ({ highContrastMode: _highContrastMode, ...rest }) => <Popover {...rest} />
)(({ highContrastMode }) => ({
  "& > .MuiPaper-root": {
    padding: "2px",
    ...(highContrastMode
      ? {
          border: "2px solid black",
        }
      : {}),
  },
}));

/**
 * @module AccessibilityTips
 *
 * Via the "prefers-contrast" and "prefers-reduced-motion" media queries the
 * user's device and browser may inform us that the user wishes for the page to
 * adapt to their visual needs. There are various places in the UI in which we
 * do this (with the ultimate aim that the whole product will), in particular
 * through use of the [accented theme]{@link ../accentedTheme.js}. Moreover,
 * responsive web design means that it should be possible to zoom a large
 * viewport by up to 200%, doubling the size of all of the UI elements.
 *
 * All of this functionality may not be immediately apparent to the user. As
 * such, this module provides a couple of components that open a popup listing
 * the accessibility functionality provided by this part of the UI, with links
 * to the documentation for each major operation system/browser so that they
 * can discover how to enable it.
 */

function AccessibilityTipsPopup({
  anchorEl,
  setAnchorEl,
  supportsHighContrastMode,
  supportsReducedMotion,
  supports2xZoom,
  anchorOrigin,
  transformOrigin,
  elementType,
}: {|
  anchorEl: null | EventTarget,
  setAnchorEl: (null) => void,
  supportsHighContrastMode: boolean,
  supportsReducedMotion: boolean,
  supports2xZoom: boolean,
  anchorOrigin: {| vertical: "bottom" | "top", horizontal: "center" | "left" |},
  transformOrigin: {| vertical: "top", horizontal: "center" | "right" |},
  elementType: "dialog" | "page",
|}) {
  const highContrastModeIsEnabled = window.matchMedia(
    "(prefers-contrast: more)"
  ).matches;
  const reducedMotionModeIsEnabled = window.matchMedia(
    "(prefers-reduced-motion: reduce)"
  ).matches;

  return (
    <StyledPopover
      open={Boolean(anchorEl)}
      anchorEl={anchorEl}
      highContrastMode={highContrastModeIsEnabled}
      onClose={() => setAnchorEl(null)}
      PaperProps={{
        role: "dialog",
      }}
      anchorOrigin={anchorOrigin}
      transformOrigin={transformOrigin}
    >
      <Stack spacing={0.25}>
        {supportsHighContrastMode && (
          <Alert
            severity={highContrastModeIsEnabled ? "success" : "info"}
            elevation={0}
            aria-label="Tip"
          >
            <AlertTitle>
              {highContrastModeIsEnabled
                ? "High contrast mode is enabled."
                : `This ${elementType} supports a high contrast mode.`}
            </AlertTitle>
            To {highContrastModeIsEnabled ? "disable" : "enable"}, turn{" "}
            {highContrastModeIsEnabled ? "off" : "on"} your device&apos;s high
            contrast setting:
            <br />
            <Link href="https://support.microsoft.com/en-us/windows/change-color-contrast-in-windows-fedc744c-90ac-69df-aed5-c8a90125e696">
              Windows
            </Link>
            ,{" "}
            <Link href="https://support.apple.com/en-gb/guide/mac-help/unac089/mac">
              macOS
            </Link>
            , <Link href="https://support.apple.com/en-us/111773">iOS</Link>,{" "}
            <Link href="https://support.google.com/accessibility/android/answer/11183305">
              Android
            </Link>
          </Alert>
        )}
        {supportsReducedMotion && (
          <Alert
            severity={reducedMotionModeIsEnabled ? "success" : "info"}
            elevation={0}
            aria-label="Tip"
          >
            <AlertTitle>
              {reducedMotionModeIsEnabled
                ? "Reduced motion mode is enabled."
                : `This ${elementType} supports a reduced motion mode.`}
            </AlertTitle>
            To {reducedMotionModeIsEnabled ? "disable" : "enable"}, turn{" "}
            {reducedMotionModeIsEnabled ? "off" : "on"} your device&apos;s
            reduced motion setting:
            <br />
            <Link href="https://support.apple.com/en-gb/guide/mac-help/mchlc03f57a1/14.0/mac/14.0">
              macOS
            </Link>
            ,{" "}
            <Link href="https://support.apple.com/en-gb/guide/iphone/iph0b691d3ed/ios">
              iOS
            </Link>
            ,{" "}
            <Link href="https://support.google.com/accessibility/android/answer/11183305">
              Android
            </Link>
          </Alert>
        )}
        {supports2xZoom && (
          <Alert severity="info" elevation={0} aria-label="Tip">
            <AlertTitle>
              This {elementType} supports up to 200% zoom magnification.
            </AlertTitle>
            To enable, adjust your browser&apos;s settings:
            <br />
            <Link href="https://support.google.com/chrome/answer/96810?hl=en&co=GENIE.Platform%3DDesktop">
              Chrome
            </Link>
            ,{" "}
            <Link href="https://support.apple.com/en-gb/guide/safari/ibrw1068/mac">
              Safari
            </Link>
            ,{" "}
            <Link href="https://support.microsoft.com/en-us/microsoft-edge/accessibility-features-in-microsoft-edge-4c696192-338e-9465-b2cd-bd9b698ad19a">
              Edge
            </Link>
            ,{" "}
            <Link href="https://support.mozilla.org/en-US/kb/font-size-and-zoom-increase-size-of-web-pages">
              Firefox
            </Link>
          </Alert>
        )}
      </Stack>
    </StyledPopover>
  );
}

function AccessibilityIcon() {
  return (
    <SvgIcon viewBox="0 0 24 24">
      <mask id="man">
        <rect x="0" y="0" width="24" height="24" fill="white" />
        <path
          style={{
            transform: "scale(0.75)",
            transformOrigin: "center",
            fill: "black",
          }}
          d="M20.5 6c-2.61.7-5.67 1-8.5 1s-5.89-.3-8.5-1L3 8c1.86.5 4 .83 6 1v13h2v-6h2v6h2V9c2-.17 4.14-.5 6-1zM12 6c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2"
        ></path>
      </mask>

      <path
        mask="url(#man)"
        d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2"
      ></path>
    </SvgIcon>
  );
}

type AccessibilityTipsComponentArgs = {|
  supportsHighContrastMode?: boolean,
  supportsReducedMotion?: boolean,
  supports2xZoom?: boolean,
|};

/**
 * @summary This component provides an icon button for use in the headers of
 *          dialogs that is designed to inform the user that the current dialog
 *          has support for configurable accessibility options.
 * @see     {@link module:AccessibilityTips} for more info.
 */
export function AccessibilityTipsIconButton({
  supportsHighContrastMode,
  supportsReducedMotion,
  supports2xZoom,
}: AccessibilityTipsComponentArgs): Node {
  const [anchorEl, setAnchorEl] = React.useState<EventTarget | null>(null);

  return (
    <>
      <IconButtonWithTooltip
        title="Accessibility tips"
        icon={<AccessibilityIcon />}
        size="small"
        onClick={(e: Event & { +currentTarget: EventTarget, ... }) => {
          setAnchorEl(e.currentTarget);
        }}
      />
      <AccessibilityTipsPopup
        anchorEl={anchorEl}
        setAnchorEl={setAnchorEl}
        supportsHighContrastMode={supportsHighContrastMode ?? false}
        supportsReducedMotion={supportsReducedMotion ?? false}
        supports2xZoom={supports2xZoom ?? false}
        anchorOrigin={{
          vertical: "bottom",
          horizontal: "center",
        }}
        transformOrigin={{
          vertical: "top",
          horizontal: "center",
        }}
        elementType="dialog"
      />
    </>
  );
}

/**
 * @summary This component provides an accented menu item for use in the
 *          profile/account menu of the App Bar that is designed to inform the
 *          user that the current page has support for configurable
 *          accessibility options.
 * @see     {@link module:AccessibilityTips} for more info.
 */
export function AccessibilityTipsMenuItem({
  supportsHighContrastMode,
  supportsReducedMotion,
  supports2xZoom,
  onClose,
}: {|
  ...AccessibilityTipsComponentArgs,
  onClose: () => void,
|}): Node {
  const [anchorEl, setAnchorEl] = React.useState<EventTarget | null>(null);

  return (
    <>
      <AccentMenuItem
        title="Accessibility Tips"
        avatar={<AccessibilityIcon />}
        onClick={(e) => {
          setAnchorEl(e.currentTarget);
        }}
        compact
      />
      <AccessibilityTipsPopup
        anchorEl={anchorEl}
        setAnchorEl={() => {
          setAnchorEl(null);
          onClose();
        }}
        supportsHighContrastMode={supportsHighContrastMode ?? false}
        supportsReducedMotion={supportsReducedMotion ?? false}
        supports2xZoom={supports2xZoom ?? false}
        anchorOrigin={{
          vertical: "top",
          horizontal: "left",
        }}
        transformOrigin={{
          vertical: "top",
          horizontal: "right",
        }}
        elementType="page"
      />
    </>
  );
}
