//@flow

import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import Typography from "@mui/material/Typography";
import React, { type Node } from "react";

/**
 * This component is for displaying a button that has a large icon and a short
 * piece of explanatory text in addition to a label.
 */

type BigIconButtonArgs = {|
  label: string,
  icon: Node,
  explanatoryText: string,

  /**
   * When using the button inside of a HTMLLabelElement to trigger an invisible
   * HTMLInputElement with type "file", leave `onClick` undefined and set
   * `component` to "span". This will ensure that click events bubble up to the
   * HTMLLabelElement that should wrap the whole form field.
   */
  onClick?: () => void,
  component?: string,
|};

export default function BigIconButton({
  label,
  icon,
  explanatoryText,
  onClick,
  component,
}: BigIconButtonArgs): Node {
  return (
    <Button
      color="primary"
      variant="outlined"
      onClick={onClick}
      component={component}
      sx={{
        /*
         * Whilst browsers with default buttons do center text, if the caller
         * sets the component to something like "span" then we need to ensure
         * that the text is centered. The prop `component` may be set to "span"
         * when this button is used to trigger a parent HTMLLabelElement and
         * thus an invisible HTMLInputElement with type "file"
         */
        textAlign: "center",
      }}
    >
      <Grid container direction="column">
        <Grid item>{icon}</Grid>
        <Grid item>{label}</Grid>
        <Grid item>
          <Typography variant="caption">{explanatoryText}</Typography>
        </Grid>
      </Grid>
    </Button>
  );
}
