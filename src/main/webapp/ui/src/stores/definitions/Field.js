//@flow

import { type Attachment } from "./Attachment";
import { type BaseRecord } from "./BaseRecord";
import { type ValidationResult } from "../../components/ValidatingSubmitButton";
import { type GalleryFile } from "../../eln/gallery/useGalleryListing";

export type FieldType =
  | "choice"
  | "date"
  | "number"
  | "radio"
  | "string"
  | "text"
  | "uri"
  | "time"
  | "reference"
  | "attachment";

export type OptionValue = string;

export type Option = {|
  value: OptionValue,
  label: string,
  editing: boolean,
|};

export interface Field extends BaseRecord {
  attachment: ?Attachment;
  originalAttachment: ?Attachment;
  mandatory: boolean;
  columnIndex: number;
  type: FieldType;
  error: boolean;
  content: string | number;
  selectedOptions: ?Array<string>;
  options: Array<Option>;

  +paramsForBackend: { ... };
  +hasContent: boolean;

  validate(): ValidationResult;
  setAttributesDirty({ ... }): void;
  setAttachment(File | GalleryFile): void;
  setError(boolean): void;
}
