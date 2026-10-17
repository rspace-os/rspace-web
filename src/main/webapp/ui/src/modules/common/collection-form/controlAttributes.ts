import type { FocusEventHandler } from "react";
import type { ControlProps } from "./RenderFields.types";

export function controlAttributes<TDocument>({
  describedBy,
  disabled,
  fieldApi,
  fieldConfig,
  id,
  invalid,
}: ControlProps<TDocument>) {
  // Formisch ignores the event; custom controls may focus a wrapper instead of a native input.
  const onFocus: FocusEventHandler<HTMLElement> = fieldApi.props.onFocus;
  const onBlur: FocusEventHandler<HTMLElement> = fieldApi.props.onBlur;
  return {
    id,
    name: fieldConfig.name,
    disabled,
    required: fieldConfig.required,
    autoFocus: fieldApi.props.autoFocus,
    onFocus,
    onBlur,
    "aria-describedby": describedBy,
    "aria-invalid": invalid || undefined,
  };
}
