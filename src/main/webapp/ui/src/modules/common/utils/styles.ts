import { type SxProps, type Theme } from "@mui/material/styles";

// The element type of MUI's array-form sx prop
type SxItem = boolean | Exclude<SxProps<Theme>, ReadonlyArray<unknown>>;

export const asSxArray = (s: SxProps<Theme> | undefined): SxItem[] =>
  Array.isArray(s) ? [...(s as ReadonlyArray<SxItem>)] : s ? [s as SxItem] : [];

export const mergeSx = (
  ...sxValues: Array<SxProps<Theme> | undefined>
): SxProps<Theme> => sxValues.flatMap(asSxArray);
