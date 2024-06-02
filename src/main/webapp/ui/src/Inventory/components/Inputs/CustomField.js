//@flow

import React, { type Node } from "react";
import { observer } from "mobx-react-lite";
import { dropProperty } from "../../../util/Util";
import InputWrapper, {
  type InputWrapperArgs,
} from "../../../components/Inputs/InputWrapper";
import AttachmentField, {
  type AttachmentFieldArgs,
} from "../../../components/Inputs/AttachmentField";
import ChoiceField, {
  type ChoiceFieldArgs,
} from "../../../components/Inputs/ChoiceField";
import DateField, {
  type DateFieldArgs,
} from "../../../components/Inputs/DateField";
import FileField, {
  type FileFieldArgs,
} from "../../../components/Inputs/FileField";
import NumberField, {
  type NumberFieldArgs,
} from "../../../components/Inputs/NumberField";
import RadioField, {
  type RadioFieldArgs,
} from "../../../components/Inputs/RadioField";
import ReferenceField, {
  type ReferenceFieldArgs,
} from "../../../components/Inputs/ReferenceField";
import StringField, {
  type StringFieldArgs,
} from "../../../components/Inputs/StringField";
import TextField, {
  type TextFieldArgs,
} from "../../../components/Inputs/TextField";
import TimeField, {
  type TimeFieldArgs,
} from "../../../components/Inputs/TimeField";
import UriField, {
  type UriFieldArgs,
} from "../../../components/Inputs/UriField";

/*
 * These type aliases must be inexact objects so that all of the props
 * necessary to render different field types can all be passed in at the same
 * time. Otherwise, at compile time, the code could only render one field OR
 * another. By allowing extra properties to be passed with those required for a
 * given field, we can delay that condition to runtime.
 *
 * As such, be aware that Flow will not warn about small errors in the props
 * passed to this component such as typos and unused props.
 */
type FieldArgs<T: string> =
  | {| type: "Attachment", ...AttachmentFieldArgs<any> |}
  | {| type: "Choice", ...ChoiceFieldArgs<T> |}
  | {| type: "Date", ...DateFieldArgs |}
  | {| type: "File", ...FileFieldArgs |}
  | {| type: "Number", ...NumberFieldArgs |}
  | {| type: "Radio", ...RadioFieldArgs<T> |}
  | {| type: "Reference", ...ReferenceFieldArgs |}
  | {| type: "String", ...StringFieldArgs |}
  | {| type: "Text", ...TextFieldArgs |}
  | {| type: "Time", ...TimeFieldArgs |}
  | {| type: "Uri", ...UriFieldArgs |};

type CustomFieldArgs<T: string> = {|
  type:
    | "Attachment"
    | "Choice"
    | "Date"
    | "File"
    | "Number"
    | "Radio"
    | "Reference"
    | "String"
    | "Text"
    | "Time"
    | "Uri",
  ...FieldArgs<T>,
  label?: string,
  value?: any,
  error?: boolean,
  helperText?: ?string,
  maxLength?: number,
  disabled?: boolean,
  actions?: Node,
  inline?: boolean,
  explanation?: ?Node,
  required?: boolean,
|};

/*
 * Component that can render any of the field types, based on the value of the
 * `type` prop, wrapped in an InputWrapper.
 */
function CustomField<T: string>(props: CustomFieldArgs<T>): Node {
  const wrapperProps: $Diff<InputWrapperArgs, {| children: mixed |}> = {
    label: props.label,
    value: props.value,
    error: props.error,
    helperText: props.helperText,
    maxLength: props.maxLength,
    disabled: props.disabled,
    actions: props.actions,
    inline: props.inline,
    explanation: props.explanation,
    required: props.required,
  };

  const fieldProps: any = dropProperty((props: any), "type");
  delete fieldProps.label;
  delete fieldProps.helperText;
  delete fieldProps.required;

  /*
   * Excessive properties -- the ones allowed by the inexact type alises --
   * must now be removed so that react doesn't fill up the JS console with
   * warnings about superfluous props.
   */
  if (props.type !== "Attachment") {
    delete fieldProps.attachment;
    delete fieldProps.onAttachmentChange;
    delete fieldProps.disableFileUpload;
  }
  if (props.type !== "Choice" && props.type !== "Radio") {
    delete fieldProps.onOptionChange;
    delete fieldProps.onOptionRemove;
    delete fieldProps.hideWhenDisabled;
    delete fieldProps.allowOptionDeletion;
  }
  if (props.type !== "Radio") {
    delete fieldProps.allowRadioUnselection;
  }

  let field;
  if (props.type === "Attachment") {
    field = <AttachmentField {...fieldProps} />;
  } else if (props.type === "Choice") {
    field = <ChoiceField {...fieldProps} />;
  } else if (props.type === "Date") {
    field = <DateField {...fieldProps} />;
  } else if (props.type === "File") {
    field = <FileField {...fieldProps} />;
  } else if (props.type === "Number") {
    field = <NumberField {...fieldProps} />;
  } else if (props.type === "Radio") {
    field = <RadioField {...fieldProps} />;
  } else if (props.type === "Reference") {
    field = <ReferenceField {...fieldProps} />;
  } else if (props.type === "String") {
    field = <StringField {...fieldProps} />;
  } else if (props.type === "Text") {
    field = <TextField {...fieldProps} />;
  } else if (props.type === "Time") {
    field = <TimeField {...fieldProps} />;
  } else if (props.type === "Uri") {
    field = <UriField {...fieldProps} />;
  } else {
    throw new Error(`Unknown field type "${props.type}"`);
  }

  return <InputWrapper {...wrapperProps}>{field}</InputWrapper>;
}

export default (observer(CustomField): typeof CustomField);
