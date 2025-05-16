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

export const FieldTypes: { [fieldName: string]: symbol } = {
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

export type FieldType = (typeof FieldTypes)[keyof typeof FieldTypes];

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
    [FieldTypes.choice]: "choice",
    [FieldTypes.date]: "date",
    [FieldTypes.number]: "number",
    [FieldTypes.radio]: "radio",
    [FieldTypes.plain_text]: "string",
    [FieldTypes.formatted_text]: "text",
    [FieldTypes.uri]: "uri",
    [FieldTypes.time]: "time",
    [FieldTypes.reference]: "reference",
    [FieldTypes.attachment]: "attachment",
  };
  if (!map[fieldType])
    throw new Error(`Invalid field type: Symbol(${Symbol.keyFor(fieldType)})`);
  return map[fieldType];
};

export const apiStringToFieldType = (apiString: string): FieldType => {
  const map: { [fieldName: string]: symbol } = {
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

export const FIELD_LABEL: { [fieldName: FieldType]: string } = {
  [FieldTypes.choice]: "Choice",
  [FieldTypes.date]: "Date",
  [FieldTypes.number]: "Number",
  [FieldTypes.radio]: "Radio",
  [FieldTypes.plain_text]: "Plain text",
  [FieldTypes.formatted_text]: "Formatted text",
  [FieldTypes.uri]: "URI",
  [FieldTypes.time]: "Time",
  [FieldTypes.attachment]: "Attachment",
  [FieldTypes.reference]: "Reference",
};

export const FIELD_ICON: { [fieldName: FieldType]: React.ReactNode } = {
  [FieldTypes.choice]: <CheckBoxOutlinedIcon />,
  [FieldTypes.date]: <EventOutlinedIcon />,
  [FieldTypes.number]: <NumberIcon />,
  [FieldTypes.radio]: <RadioButtonCheckedOutlinedIcon />,
  [FieldTypes.plain_text]: <ShortTextOutlinedIcon />,
  [FieldTypes.formatted_text]: <SubjectOutlinedIcon />,
  [FieldTypes.uri]: <LinkOutlinedIcon />,
  [FieldTypes.time]: <QueryBuilderOutlinedIcon />,
  [FieldTypes.attachment]: <AttachFileOutlinedIcon />,
};

export const FIELD_HELP_TEXT: { [fieldName: FieldType]: string } = {
  [FieldTypes.choice]: "Choose many options.",
  [FieldTypes.date]: "A single date.",
  [FieldTypes.number]: "Any numerical value.",
  [FieldTypes.radio]: "Choose one option.",
  [FieldTypes.plain_text]: "A short plain-text label.",
  [FieldTypes.formatted_text]: "A longer formatted piece of text.",
  [FieldTypes.uri]: "A Web link.",
  [FieldTypes.time]: "A time of day.",
  [FieldTypes.attachment]: "A single file, e.g. document, or chemistry file.",
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
  [fieldName: FieldType]: {
    icon: React.ReactNode;
    help: string;
    label: string;
  };
} = listToObject(Object.values(FieldTypes), (f) => ({
  icon: FIELD_ICON[f],
  help: FIELD_HELP_TEXT[f],
  label: FIELD_LABEL[f],
}));
