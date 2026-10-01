import type { ControlProps } from "./RenderFields.types";

export function controlAttributes<TDocument>({
  describedBy,
  disabled,
  fieldApi,
  fieldConfig,
  id,
  invalid,
}: ControlProps<TDocument>) {
  return {
    id,
    name: fieldConfig.name,
    disabled,
    required: fieldConfig.required,
    autoFocus: fieldApi.props.autoFocus,
    "aria-describedby": describedBy,
    "aria-invalid": invalid || undefined,
  };
}
