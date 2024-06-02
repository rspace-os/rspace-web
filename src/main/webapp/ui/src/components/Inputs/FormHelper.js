//@flow

import Box from "@mui/material/Box";
import FormControl from "./FormControl";
import FormHelperText from "@mui/material/FormHelperText";
import React, { type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";

type FormHelperArgs = {|
  children: Node,
  disabled?: boolean,
  error?: boolean,
  helperText: string,
  label: string,
|};

function FormHelper({
  children,
  error = false,
  helperText,
  label,
}: FormHelperArgs): Node {
  return (
    <FormControl label={label} error={error}>
      <Box mb={0.5}>
        <FormHelperText component="span">{helperText}</FormHelperText>
      </Box>
      {children}
    </FormControl>
  );
}

export default (observer(FormHelper): ComponentType<FormHelperArgs>);
