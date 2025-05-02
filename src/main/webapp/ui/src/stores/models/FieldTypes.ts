import React from "react";
import CheckBoxOutlinedIcon from "@mui/icons-material/CheckBoxOutlined";
import RadioButtonCheckedOutlinedIcon from "@mui/icons-material/RadioButtonCheckedOutlined";
import ShortTextOutlinedIcon from "@mui/icons-material/ShortTextOutlined";
import SubjectOutlinedIcon from "@mui/icons-material/SubjectOutlined";
import EventOutlinedIcon from "@mui/icons-material/EventOutlined";
import QueryBuilderOutlinedIcon from "@mui/icons-material/QueryBuilderOutlined";
import LinkOutlinedIcon from "@mui/icons-material/LinkOutlined";
import AttachFileOutlinedIcon from "@mui/icons-material/AttachFileOutlined";
import { listToObject } from "../../util/Util";
import NumberIcon from "../../components/NumberIcon";

/*
 * Flowjs just straight up doesn't support Symbols which makes
 * this file a bit of a mess, unfortunately. See this GitHub
 * issue https://github.com/facebook/flow/issues/3258
 */

export const FieldTypes: { [string]: symbol } = {
  choice: Symbol.for("choice"),
  date: Symbol.for("date"),
  number: Symbol.for("number"),
  radio: Symbol.for("radio"),
  plain_text: Symbol.for("plain text"),
  formatted_text: Symbol.for("formatted text"),
  time: Symbol.for("time"),
  uri: Symbol.for("uri"),
  reference: Symbol.for("reference"),
  attachment: Symbol.for("attachment"),
};

export type FieldType = $Values<typeof FieldTypes>;

export const compatibleFieldTypes = (
  fieldType: FieldType
): Array<FieldType> => {
  const types = new Set([fieldType]);
  if (
    fieldType !== FieldTypes.reference &&
    fieldType !== FieldTypes.attachment
  ) {
    types.add(FieldTypes.formatted_text);
    types.add(FieldTypes.plain_text);
    types.add(FieldTypes.radio);
    types.add(FieldTypes.choice);
  }
  return [...types];
};

export const fieldTypeToApiString = (fieldType: FieldType): string => {
  if (!(typeof fieldType === "symbol"))
    throw new Error("FieldTypes are all Symbols.");
  const map = {
    // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
    [FieldTypes.choice]: "choice",
    // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
    [FieldTypes.date]: "date",
    // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
    [FieldTypes.number]: "number",
    // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
    [FieldTypes.radio]: "radio",
    // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
    [FieldTypes.plain_text]: "string",
    // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
    [FieldTypes.formatted_text]: "text",
    // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
    [FieldTypes.uri]: "uri",
    // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
    [FieldTypes.time]: "time",
    // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
    [FieldTypes.reference]: "reference",
    // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
    [FieldTypes.attachment]: "attachment",
  };
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  if (!map[fieldType])
    // $FlowExpectedError[incompatible-type] fieldType is a valid FieldType
    throw new Error(`Invalid field type: Symbol(${Symbol.keyFor(fieldType)})`);
  return map[fieldType];
};

export const apiStringToFieldType = (apiString: string): FieldType => {
  const map: {[string]: symbol} = {
    choice: FieldTypes.choice,
    date: FieldTypes.date,
    number: FieldTypes.number,
    radio: FieldTypes.radio,
    string: FieldTypes.plain_text,
    text: FieldTypes.formatted_text,
    uri: FieldTypes.uri,
    time: FieldTypes.time,
    reference: FieldTypes.reference,
    attachment: FieldTypes.attachment,
  };
  if (!map[apiString])
    throw new Error(
      `Invalid api string '${apiString}'; not a valid field type.`
    );
  return map[apiString];
};

export const hasOptions = (fieldType: FieldType): boolean =>
  fieldType === FieldTypes.choice || fieldType === FieldTypes.radio;

export const FIELD_LABEL: { [FieldType]: string } = {
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  [FieldTypes.choice]: "Choice",
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  [FieldTypes.date]: "Date",
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  [FieldTypes.number]: "Number",
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  [FieldTypes.radio]: "Radio",
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  [FieldTypes.plain_text]: "Plain text",
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  [FieldTypes.formatted_text]: "Formatted text",
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  [FieldTypes.uri]: "URI",
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  [FieldTypes.time]: "Time",
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  [FieldTypes.attachment]: "Attachment",
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  [FieldTypes.reference]: "Reference",
};

export const FIELD_ICON: { [FieldType]: Element } = {
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  [FieldTypes.choice]: <CheckBoxOutlinedIcon />,
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  [FieldTypes.date]: <EventOutlinedIcon />,
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  [FieldTypes.number]: <NumberIcon />,
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  [FieldTypes.radio]: <RadioButtonCheckedOutlinedIcon />,
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  [FieldTypes.plain_text]: <ShortTextOutlinedIcon />,
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  [FieldTypes.formatted_text]: <SubjectOutlinedIcon />,
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  [FieldTypes.uri]: <LinkOutlinedIcon />,
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  [FieldTypes.time]: <QueryBuilderOutlinedIcon />,
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  [FieldTypes.reference]: <LinkOutlinedIcon />,
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  [FieldTypes.attachment]: <AttachFileOutlinedIcon />,
};

export const FIELD_HELP_TEXT: { [FieldType]: string } = {
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  [FieldTypes.choice]: "Choose many options.",
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  [FieldTypes.date]: "A single date.",
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  [FieldTypes.number]: "Any numerical value.",
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  [FieldTypes.radio]: "Choose one option.",
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  [FieldTypes.plain_text]: "A short plain-text label.",
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  [FieldTypes.formatted_text]: "A longer formatted piece of text.",
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  [FieldTypes.uri]: "A Web link.",
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  [FieldTypes.time]: "A time of day.",
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  [FieldTypes.attachment]: "A single file, e.g. document, or chemistry file.",
  // $FlowExpectedError[invalid-computed-prop] Symbols are not supported by flow
  [FieldTypes.reference]: "",
};

export const SUPPORTED_TYPES: Set<FieldType> = new Set<FieldType>([
  FieldTypes.choice,
  FieldTypes.date,
  FieldTypes.number,
  FieldTypes.radio,
  FieldTypes.plain_text,
  FieldTypes.formatted_text,
  FieldTypes.uri,
  FieldTypes.time,
  FieldTypes.attachment,
]);

export const FIELD_DATA: {
  [FieldType]: { icon: Node, help: string, label: string },
} = listToObject(Object.values(FieldTypes), (f) => ({
  icon: FIELD_ICON[f],
  help: FIELD_HELP_TEXT[f],
  label: FIELD_LABEL[f],
}));
