import { darken, type Theme } from "@mui/material/styles";
import type { SxProps } from "@mui/system";

/**
 * Shared `sx` for the Requests detail panel's approve/reject/fulfil action buttons
 * (OwnerActionsPanel, RejectRequestDialog, FulfilRequestDialog), all drawing on
 * theme.palette.sampleRequestActions.
 *
 * These can't be registered as MUI `color` variants (e.g. `color="reject"`): MUI only derives a
 * button's variant colouring from a real `PaletteColor` (`main`/`dark`/`contrastText`) registered
 * directly on `theme.palette`, generating its `--variant-*` CSS custom properties from that shape
 * alone. `sampleRequestActions`'s entries are a different, bespoke shape
 * (`color`/`borderColor`/`backgroundColor`, with a hover that swaps to an unrelated named colour
 * rather than a mechanical darken), so there's no `theme.palette.reject` etc. for MUI's automatic
 * per-colour variant generation to key off - a `color="reject"` prop silently matches nothing and
 * renders unstyled. "&&" boosts specificity above MuiButton's own built-in `.Mui-disabled` styling,
 * since some of these (e.g. markAsFulfilled, approvedDisabled) are deliberately applied to an
 * already-disabled button to show a particular status rather than the generic greyed-out look.
 */

export function rejectButtonSx(theme: Theme): SxProps<Theme> {
  return {
    "&&": {
      ...theme.palette.sampleRequestActions.reject,
      "&:hover": {
        borderColor: darken(theme.palette.sampleRequestActions.reject.borderColor, 0.2),
        backgroundColor: theme.palette.sampleRequestStatus.rejected,
      },
    },
  };
}

export function approveButtonSx(theme: Theme): SxProps<Theme> {
  return {
    "&&": {
      ...theme.palette.sampleRequestActions.approve,
      "&:hover": {
        backgroundColor: darken(theme.palette.sampleRequestActions.approve.backgroundColor, 0.15),
      },
    },
  };
}

export function approvedDisabledButtonSx(theme: Theme): SxProps<Theme> {
  return { "&&": theme.palette.sampleRequestActions.approvedDisabled };
}

export function markAsFulfilledButtonSx(theme: Theme): SxProps<Theme> {
  return {
    "&&": {
      ...theme.palette.sampleRequestActions.markAsFulfilled,
      "&:hover": {
        borderColor: darken(theme.palette.sampleRequestActions.markAsFulfilled.borderColor, 0.2),
        backgroundColor: theme.palette.sampleRequestStatus.fulfilled,
      },
    },
  };
}
