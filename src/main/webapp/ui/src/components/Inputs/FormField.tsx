import React from "react";
import FormControl from "@mui/material/FormControl";
import FormLabel from "@mui/material/FormLabel";
import FormHelperText from "@mui/material/FormHelperText";
import { Optional } from "../../util/optional";
import Typography from "@mui/material/Typography";
import { Heading } from "../DynamicHeadingLevel";

/**
 * This component provides all of the UI around a MUI input component (like
 * TextField or Checkbox), or our custom variants thereof, to turn them into
 * fully fledged form fields. This includes providing labels, helper text (such
 * as a count of the total characters permitted), and other such functionality.
 *
 * In many places, we provide the same props to both the input component itself
 * and the components that wrap it like FormContol, FormLabel, FormGroup, and
 * FormHelperText. To prevent duplication everywhere we use an input component
 * as a form control, this component coordinates where all of those props are
 * passed to each of those Form* components as well as the input component
 * itself via the `renderInput` prop. For example, rather than doing this:
 *
 *     <FormControl
 *       error={error}
 *     >
 *       <TextField error={error} />
 *     </FormControl>
 *
 * This component allows us to simply do:
 *
 *    <FormField
 *      error={error}
 *      renderInput={(props) => (
 *        <TextField {...props} />
 *      )}
 *    />
 *
 * Now, `error` need only be specified once at the call site, eliminating the
 * possibility of a bug arising from forgetting to provide `error` in both
 * places it is required. This principle equally applies to `disabled` and
 * `value` which are used to render helper text and max character labels inside
 * of FormHelperTexts. Forget to pass one and the label may not be shown.
 *
 * This component also aids with ensuring adherence to the WCAG Accessibility
 * standard. By default, it wraps all of the form field pieces in a div with
 * role "group", and connects up the input component, the label, and the
 * optional explanatory text with HTML ids using the `for`, `aria-labelledby`,
 * and `aria-describedby` attributes, respectively. When `asFieldset` is set,
 * HTMLFieldSetElements and HTMLLegendElements are rendered instead.
 */

export type FormFieldArgs<T> = {
  /**
   * All form fields MUST have a label. It should be as short as possible,
   * whilst remaining unambiguous. It MUST not end in a full stop. For example:
   * "Name", "Description", "Quantity".
   */
  label: string;

  /**
   * This function is what actually renders the MUI input component, or
   * anything that renders a HTMLInputElement, which it MUST do.
   *
   * Be sure not to do any mobx destructuring inside this function as any
   * changes to those properties will not trigger a re-render. Any such values
   * MUST be passed in the `value` prop and threaded through this component.
   *
   * DO NOT do this:
   *   function SomeComponent() {
   *     const state = useLocalObservable(() => ({ foo: "bar" }));
   *     return (
   *       <FormField
   *         value={null}
   *         renderInput={() => (
   *           <TextField value={state.foo} {..rest} />
   *        )}
   *        {...rest}
   *      />
   *     );
   *   }
   *
   * Instead, do this:
   *   function SomeComponent() {
   *     const state = useLocalObservable(() => ({ foo: "bar" }));
   *     return (
   *       <FormField
   *         value={state.foo}
   *         renderInput={({ value: foo }) => (
   *           <TextField value={foo} {..rest} />
   *        )}
   *        {...rest}
   *      />
   *     );
   *   }
   */
  renderInput: ({
    id,
    value,
    disabled,
    error,
  }: {
    /**
     * This components renders an HTMLLabelElement (or if is `asFieldset` is
     * true then a HTMLLegendElement) with an ID. If `renderInput` is
     * rendering an HTMLInputElement that doesn't otherwise have a visible
     * label then it MUST attach this ID as its ID. Not to do so is an
     * accessibility violation. It is fine to ignore this property if
     * `asFieldset` is true as a HTMLLegendElement will be rendered inside of
     * a HTMLFieldSetElement, which satisfies the accessibility requirement.
     * It is also acceptable to ignore this ID if there is no HTMLInputElement
     * to attach it to; some form fields may only consist of
     * HTMLButtonElements, or other elements that are not strictly interactive.
     */
    id: string;

    /**
     * These properties are the ones that are passed blindly from this
     * component down to the rendered input component. They CAN be ignored
     * provided that the rendered input component DOES NOT use them; if it does
     * then they MUST be used rather than passed directly. This prevents
     * duplication of setting props, and leaves open the potential for this
     * component to amend these values as they are threaded through.
     */
    value: T;
    disabled: boolean;
    error: boolean;
  }) => React.ReactNode;

  /**
   * Fields, by definition, have a value that is being manipulated and in many
   * cases that is useful to the whole form field rather than just the input
   * components that the field wraps. For example, for string fields the
   * `maxLength` prop can be used to provide a label that indicates the number
   * of used characters. This prop is used for that additional functionality
   * but is not necessary for the basic usage of displaying a label.
   */
  value: T;

  /**
   * On top of the behaviour provided by MUI when the Form* components are in
   * a disabled state, this component also adds some toggled behaviour of its
   * own. See `maxLength` props.
   */
  disabled?: boolean;

  /**
   * On top of the behaviour provided by MUI when the Form* components are in
   * an error state, this component also adds some toggled behaviour of its
   * own. See `helperText` and `maxLength` props.
   */
  error?: boolean;

  /**
   * Helper text is an optional string displayed when the field is in an error
   * state that explains what is wrong. It SHOULD be kept brief and MUST
   * explain the fault in a statement-of-fact tone that makes the required
   * resolution obvious. The sentence MUST end in a full stop. For example,
   * "Name must include at least one non-whitespace character." When the
   * `error` prop is not true, the helper text will NOT be shown.
   */
  helperText?: string | null;

  /**
   * If specified and `T` is a string, then a label is shown indicating how
   * many characters the user is allowed to enter and how many they already
   * have. This prop does not really make sense when the field type is a not
   * string as for most other fields typical values will not run up against
   * any database limits. The label is NOT shown if the `error` prop is true
   * and the helper text is a non-empty string. The label is NOT shown if
   * `disabled` is true.
   */
  maxLength?: number;

  /**
   * Form fields can optionally have a longer piece of explanatory text beneath
   * the label that provides additional context, links to documentation, and
   * general advice for completing the form. It ought to be kept fairly brief
   * where at all possible; anything more than a sentence or two should instead
   * be in the docs so that it doesn't obscure the page all the time. This is a
   * Node rather than a string so that it can include HTMLAnchorElements, and
   * other typographic elements.
   */
  explanation?: React.ReactNode;

  /**
   * An ID can be attached to the root FormControl component, to be used as
   * part of accessibility markup. For example, if there is another component
   * on the page that controls this form field (such as enabling/disabling it)
   * then that component should have an `aria-controls` attribute with this ID.
   */
  id?: string;

  /**
   * Provides no additional functionality beyond that of MUI's base
   * functionality of the Form* components, which is to say adorning the label
   * with a red asterisk.
   */
  required?: boolean;

  /**
   * Where a form field contains multiple interactive elements of equal
   * standing, this prop MUST be true. It will change the wrapping
   * HTMLDivElement to a HTMLFieldSetElement and the label from a
   * HTMLLabelElement to a HTMLLegendElement. For example, if the form field
   * contains multiple HTMLRadioElements each with their own label then this
   * prop MUST be true. If the form field contains a HTMLInputElement of type
   * text and a secondary HTMLButtonElement which performs an auxiliary action
   * then this prop need not be true.
   */
  asFieldset?: boolean;

  /**
   * The majority of input components render an HTMLInputElement to which an ID
   * should be attached so that the HTMLLabelElement rendered by the component
   * can reference it in its `for` attribute. However for input components that
   * do not have an HTMLInputElement, the HTMLLabelElement should not render a
   * `for` attribute with an ID that doesn't point to a valid DOM node. In
   * those cases, and only those cases, this prop MUST be true.
   *
   * Initially, this was implemented by checking if the ID passed in the
   * `renderInput` prop is actually used by a DOM node. However, this required
   * a second render to ensure that this check behaved correctly: first
   * `renderInput` would need to be invoked and then this component re-rendered
   * again to update the `for` attribute of the HTMLLabelElement. If on this
   * subsequent render `renderInput` changed where the ID pointed to then a
   * third render would be required, ad infinitum. Instead, this additional
   * prop prevents this cyclical rendering requirement.
   *
   * The downside to this prop is that `renderInput` will always return an ID,
   * regardless of whether this prop is true or not and thus regardless of
   * whether it is actually being used as the `for` attribute of the
   * HTMLLabelElement. If the input component rendered by `renderInput` were to
   * use the passed ID then the HTMLInputElement will have an ID that isn't
   * used by the HTMLLabelElement. To get flow to error on such cases proved
   * very difficult to do. To satisfy flow that the ID passed to `renderInput`
   * can't be passed to the input component being rendered, destructure it from
   * the passed props and assign it to a variable beginning with an underscore
   * to satisfy eslint. What's nice about this is that the code ends up being
   * very explicit about not following the default behaviour designed to meet
   * to the accessibility recommendations. For example,
   *
   *    <FormField
   *      doNotAttachIdToLabel
   *      renderInput={({ id: _id, ...rest }) => (
   *        <InputComponent {...rest} />
   *      )}
   *    />
   *
   */
  doNotAttachIdToLabel?: boolean;

  className?: string;
};

export default function FormField<T>({
  label,
  value,
  renderInput,
  disabled,
  error,
  helperText,
  maxLength,
  explanation,
  id,
  required,
  asFieldset,
  doNotAttachIdToLabel,
  className,
}: FormFieldArgs<T>): React.ReactNode {
  const inputId = React.useId();
  const labelId = React.useId();
  const explanationId = React.useId();

  const lengthLabel = (): Optional<string> => {
    if (
      Boolean(error) &&
      typeof helperText === "string" &&
      helperText.length > 0
    )
      return Optional.empty();
    if (disabled === true) return Optional.empty();
    if (typeof value !== "string") return Optional.empty();
    if (typeof maxLength === "undefined") return Optional.empty();
    return Optional.present(`${value.length} / ${maxLength}`);
  };

  const labelComponent = () => {
    if (disabled) return Heading;
    if (asFieldset) return "legend";
    return "label";
  };

  return (
    <FormControl
      role="group"
      className={className}
      fullWidth
      error={
        error ||
        (typeof value === "string" &&
          typeof maxLength === "number" &&
          value.length > maxLength)
      }
      aria-labelledby={labelId}
      required={required}
      id={id}
      {...(typeof explanation !== "undefined"
        ? { "aria-describedby": explanationId }
        : {})}
      {...(asFieldset && !disabled ? { component: "fieldset" } : {})}
    >
      <FormLabel
        id={labelId}
        component={labelComponent()}
        // reset styles added by browser when setting component prop
        sx={{ mt: 0, textAlign: "left" }}
        {...(asFieldset || doNotAttachIdToLabel ? {} : { htmlFor: inputId })}
      >
        {label}
      </FormLabel>
      {Boolean(explanation) && (
        <Typography id={explanationId} variant="body2" sx={{ mb: 0.5 }}>
          {explanation}
        </Typography>
      )}
      {renderInput({
        id: inputId,
        value,
        error: error ?? false,
        disabled: disabled ?? false,
      })}
      {lengthLabel()
        .map((l) => <FormHelperText key={null}>{l}</FormHelperText>)
        .orElse(null)}
      {Boolean(error) &&
        typeof helperText === "string" &&
        helperText.length > 0 && <FormHelperText>{helperText}</FormHelperText>}
    </FormControl>
  );
}
