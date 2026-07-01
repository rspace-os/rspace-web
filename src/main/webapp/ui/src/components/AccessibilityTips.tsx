import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import { paperClasses } from "@mui/material/Paper";
import Popover from "@mui/material/Popover";
import Stack from "@mui/material/Stack";
import SvgIcon from "@mui/material/SvgIcon";
import React from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import AccentMenuItem from "./AccentMenuItem";
import IconButtonWithTooltip from "./IconButtonWithTooltip";

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
 * Additionally, certain pages (Gallery and Inventory) provide skip-to-content
 * functionality through landmark navigation. This allows users to press the
 * "Tab" key to reveal skip links that jump directly to main page sections
 * like navigation, content, and other landmarks for easier navigation.
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
  supportsSkipToContent,
  anchorOrigin,
  transformOrigin,
  elementType,
}: {
  anchorEl: null | Element;
  setAnchorEl: (newAnchorEl: null) => void;
  supportsHighContrastMode: boolean;
  supportsReducedMotion: boolean;
  supports2xZoom: boolean;
  supportsSkipToContent: boolean;
  anchorOrigin: {
    vertical: "bottom" | "top";
    horizontal: "center" | "left";
  };
  transformOrigin: {
    vertical: "top";
    horizontal: "center" | "right";
  };
  elementType: "dialog" | "page";
}) {
  const { t } = useTranslation("common");
  const highContrastModeIsEnabled = window.matchMedia("(prefers-contrast: more)").matches;
  const reducedMotionModeIsEnabled = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const elementTypeLabel =
    elementType === "dialog" ? t("accessibilityTips.elementTypes.dialog") : t("accessibilityTips.elementTypes.page");
  return (
    <Popover
      open={Boolean(anchorEl)}
      anchorEl={anchorEl}
      onClose={() => setAnchorEl(null)}
      anchorOrigin={anchorOrigin}
      transformOrigin={transformOrigin}
      sx={{
        [`& > .${paperClasses.root}`]: {
          padding: "2px",
          maxWidth: "500px",
          ...(highContrastModeIsEnabled
            ? {
                border: "2px solid black",
              }
            : {}),
        },
      }}
      slotProps={{
        paper: {
          role: "dialog",
        },
      }}
    >
      <Stack spacing={0.25}>
        {supportsHighContrastMode && (
          <Alert
            severity={highContrastModeIsEnabled ? "success" : "info"}
            elevation={0}
            aria-label={t("accessibilityTips.tip")}
          >
            <AlertTitle>
              {highContrastModeIsEnabled
                ? t("accessibilityTips.highContrast.enabled")
                : t("accessibilityTips.highContrast.supported", { elementType: elementTypeLabel })}
            </AlertTitle>
            <TransRichText
              ns="common"
              i18nKey={
                highContrastModeIsEnabled
                  ? "accessibilityTips.highContrast.body.disable"
                  : "accessibilityTips.highContrast.body.enable"
              }
            />
          </Alert>
        )}
        {supportsReducedMotion && (
          <Alert
            severity={reducedMotionModeIsEnabled ? "success" : "info"}
            elevation={0}
            aria-label={t("accessibilityTips.tip")}
          >
            <AlertTitle>
              {reducedMotionModeIsEnabled
                ? t("accessibilityTips.reducedMotion.enabled")
                : t("accessibilityTips.reducedMotion.supported", { elementType: elementTypeLabel })}
            </AlertTitle>
            <TransRichText
              ns="common"
              i18nKey={
                reducedMotionModeIsEnabled
                  ? "accessibilityTips.reducedMotion.body.disable"
                  : "accessibilityTips.reducedMotion.body.enable"
              }
            />
          </Alert>
        )}
        {supports2xZoom && (
          <Alert severity="info" elevation={0} aria-label={t("accessibilityTips.tip")}>
            <AlertTitle>{t("accessibilityTips.zoom.supported", { elementType: elementTypeLabel })}</AlertTitle>
            <TransRichText ns="common" i18nKey="accessibilityTips.zoom.body" />
          </Alert>
        )}
        {supportsSkipToContent && (
          <Alert severity="info" elevation={0} aria-label={t("accessibilityTips.tip")}>
            <AlertTitle>{t("accessibilityTips.skipToContent.supported", { elementType: elementTypeLabel })}</AlertTitle>
            {t("accessibilityTips.skipToContent.instructions")}
          </Alert>
        )}
      </Stack>
    </Popover>
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

      <path mask="url(#man)" d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2"></path>
    </SvgIcon>
  );
}
type AccessibilityTipsComponentArgs = {
  supportsHighContrastMode?: boolean;
  supportsReducedMotion?: boolean;
  supports2xZoom?: boolean;
  supportsSkipToContent?: boolean;
};

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
  supportsSkipToContent,
}: AccessibilityTipsComponentArgs): React.ReactNode {
  const { t } = useTranslation("common");
  const [anchorEl, setAnchorEl] = React.useState<Element | null>(null);
  if (!supportsHighContrastMode && !supportsReducedMotion && !supports2xZoom && !supportsSkipToContent) return null;
  return (
    <>
      <IconButtonWithTooltip
        title={t("accessibilityTips.buttonLabel")}
        icon={<AccessibilityIcon />}
        size="small"
        onClick={(e: React.MouseEvent<HTMLButtonElement>) => {
          setAnchorEl(e.currentTarget);
        }}
      />
      <AccessibilityTipsPopup
        anchorEl={anchorEl}
        setAnchorEl={setAnchorEl}
        supportsHighContrastMode={supportsHighContrastMode ?? false}
        supportsReducedMotion={supportsReducedMotion ?? false}
        supports2xZoom={supports2xZoom ?? false}
        supportsSkipToContent={supportsSkipToContent ?? false}
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
  supportsSkipToContent,
  onClose,
}: {
  onClose: () => void;
} & AccessibilityTipsComponentArgs): React.ReactNode {
  const { t } = useTranslation("common");
  const [anchorEl, setAnchorEl] = React.useState<Element | null>(null);
  if (!supportsHighContrastMode && !supportsReducedMotion && !supports2xZoom && !supportsSkipToContent) return null;
  return (
    <>
      <AccentMenuItem
        title={t("accessibilityTips.menuLabel")}
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
        supportsSkipToContent={supportsSkipToContent ?? false}
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
