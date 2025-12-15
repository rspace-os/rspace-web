import React, { type ComponentType } from "react";
import { withStyles } from "Styles";
import NumberField, {
  type NumberFieldArgs,
} from "../../../../components/Inputs/NumberField";

/**
 * When this NumberField is in an error state, it coloured amber rather than red as the user does
 * not need to take an immediate action; the form can be saved even whilst the field is in an
 * invalid state.
 */
const AmberNumberField: ComponentType<NumberFieldArgs> = withStyles<
  NumberFieldArgs,
  { root: string }
>((theme) => ({
  root: {
    /*
     * The use of CSS classes is a little fragile as MUI does not guarantee their stability,
     * but without styling hooks into the various parts of the underlying TextField, there's
     * little better we can do.
     */
    "& .Mui-error": {
      "&:after, &:before": {
        borderBottomColor: theme.palette.warning.main,
      },
    },
    "& .MuiFormHelperText-root.Mui-error": {
      color: theme.palette.warning.main,
    },
  },
}))(({ classes, ...props }) => (
  <NumberField {...props} className={classes.root} />
));

export default AmberNumberField;
