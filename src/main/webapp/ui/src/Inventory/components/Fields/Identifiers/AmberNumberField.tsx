import { type ComponentType } from "react";
import { type SxProps, type Theme } from "@mui/material/styles";
import { formHelperTextClasses } from "@mui/material/FormHelperText";
import { inputClasses } from "@mui/material/Input";
import NumberField, {
  type NumberFieldArgs,
} from "../../../../components/Inputs/NumberField";

const AmberNumberField: ComponentType<NumberFieldArgs> = (props) => {
  const sx = props.sx;
  return (
    <NumberField
      {...props}
      /*
       * When this NumberField is in an error state, it is coloured amber rather than red as the user
       * does not need to take an immediate action; the form can be saved even whilst the field is in
       * an invalid state.
       *
       * The use of CSS classes is a little fragile as MUI does not guarantee their stability,
       * but without styling hooks into the various parts of the underlying TextField, there's
       * little better we can do.
       */
      sx={[
        (theme) => ({
          [`& .${inputClasses.error}::after, & .${inputClasses.error}::before`]:
            {
              borderBottomColor: theme.palette.warning.main,
            },
          [`& .${formHelperTextClasses.root}.${formHelperTextClasses.error}`]: {
            color: theme.palette.warning.main,
          },
        }),
        ...(Array.isArray(sx) ? sx : [sx]),
      ]}
    />
  );
};

export default AmberNumberField;
