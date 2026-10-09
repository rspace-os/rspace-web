import type { FormStore } from "@formisch/react";
import type { FieldLayout, UiFieldConfig } from "../RenderFields.types";
import { RESPONSIVE_INLINE_FIELD_SPAN_CLASS_NAME } from "../responsiveFieldLayout";

export function UiField<TDocument>({
  disabled,
  fieldConfig,
  form,
  layout,
}: {
  disabled: boolean;
  fieldConfig: UiFieldConfig<TDocument>;
  form: FormStore;
  layout: FieldLayout;
}) {
  // Rendered as a component, not called as a function, so its own hooks are its own.
  const Ui = fieldConfig.component;
  const ui = <Ui form={form} disabled={disabled} />;
  // The component is the caller's, so the column span goes on a wrapper.
  return layout === "inline" ? <div className={RESPONSIVE_INLINE_FIELD_SPAN_CLASS_NAME}>{ui}</div> : ui;
}
