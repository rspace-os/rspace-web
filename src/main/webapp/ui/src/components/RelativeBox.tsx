/*
 * Box component extended with position: relative, which allows the children to
 * use position: absolute and position themselves relative to this bounding box
 */

import Box from "@mui/material/Box";
import type { BoxProps } from "@mui/material/Box";

export default function RelativeBox(props: BoxProps): React.ReactNode {
  return <Box {...props} sx={{ position: "relative", ...props.sx }} />;
}
