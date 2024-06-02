//@flow strict

import React, { type Node } from "react";
import Typography from "@mui/material/Typography";

type VisuallyHiddenHeadingArgs = {|
  variant: "h1" | "h2" | "h3" | "h4" | "h5" | "h6",
  children: Node,
|};

/*
 * This component is for adding a heading to the DOM that is not visible to
 * sighted users but is provided to ensure compliance with the accessibility
 * standard on not skipping any heading levels. This is so that accessibility
 * tools, like screen readers, can give an accurate description of the layout
 * of the page.
 *
 * Unlike headings that are visible, there is no need to separately consider a
 * `component` prop as the `variant` prop can simply be the semantically
 * correct heading level. Given that this heading will never be shown, how MUI
 * chooses to style that tag is irrelevant.
 */
export default function VisuallyHiddenHeading({
  variant,
  children,
}: VisuallyHiddenHeadingArgs): Node {
  return (
    <Typography
      variant={variant}
      component={variant}
      sx={{
        /*
         * These styles are taken from
         * https://www.a11yproject.com/posts/how-to-hide-content/
         * Note the use of 1 px rather than 0 px because Apple's VoiceOver
         * will not announce elements with dimension of 0.
         */
        position: "absolute",
        height: "1px",
        width: "1px",
        overflow: "hidden",
        whiteSpace: "nowrap",
        clip: "rect(1px, 1px, 1px, 1px)",
        clipPath: "inset(50%)",
      }}
    >
      {children}
    </Typography>
  );
}
