import Box from "@mui/material/Box";
import type React from "react";

type Props = { label: string };

/**
 * Label that is shown when a field is both disabled and empty. Generally
 * avoid using, instead preferring to show the empty field in a disabled state.
 */
export default function NoValue({ label }: Props): React.ReactNode {
  return (
    <Box component="span" sx={{ color: "lightestGrey", fontStyle: "italic" }}>
      {label}
    </Box>
  );
}
