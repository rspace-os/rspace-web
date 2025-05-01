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

export type Option = {
  value: OptionValue;
  label: string;
  editing: boolean;
};

export interface Field extends BaseRecord {
  attachment: Attachment | null;
  originalAttachment: Attachment | null;
  mandatory: boolean;
  columnIndex: number;
  type: FieldType;
  error: boolean;
  content: string | number;
  selectedOptions: Array<string> | null;
  options: Array<Option>;

  readonly paramsForBackend: object;
  readonly hasContent: boolean;
  readonly renderContentAsString: string;

  validate(): ValidationResult;
  setAttributesDirty(attributes: Record<string, unknown>): void;
  setAttachment(file: File | GalleryFile): void;
  setError(error: boolean): void;
}
