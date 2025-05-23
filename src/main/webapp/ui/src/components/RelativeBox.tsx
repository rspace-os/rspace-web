/*
 * Box component extended with position: relative, which allows the children to
 * use position: absolute and position themselves relative to this bounding box
 */

import Box from "@mui/material/Box";
import { styled } from "@mui/material/styles";

export default styled(Box)({
  position: "relative",
});
