import type { SxProps, Theme } from "@mui/material/styles";

// The element type of MUI's array-form sx prop
type SxItem = boolean | Exclude<SxProps<Theme>, ReadonlyArray<unknown>>;

export const asSxArray = (s: SxProps<Theme> | undefined): SxItem[] =>
  Array.isArray(s) ? [...(s as ReadonlyArray<SxItem>)] : s ? [s as SxItem] : [];

export const mergeSx = (...sxValues: Array<SxProps<Theme> | undefined>): SxProps<Theme> => sxValues.flatMap(asSxArray);

/**
 * Limits text to `lines` lines, ending in an ellipsis, and breaks words with no spaces so that
 * long names cannot overflow sideways either. In a flex row, the element also needs `minWidth: 0`.
 */
export const clampToLines = (lines: number): SxProps<Theme> => ({
  display: "-webkit-box",
  WebkitBoxOrient: "vertical",
  WebkitLineClamp: lines,
  overflow: "hidden",
  overflowWrap: "anywhere",
});
