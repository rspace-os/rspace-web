import FieldModel, { type FieldModelAttrs } from "../../FieldModel";
import { makeMockSample } from "../SampleModel/mocking";

export const fieldAttrs = (
  attrs: Readonly<Partial<FieldModelAttrs> & { type: FieldModelAttrs["type"] }>
): FieldModelAttrs => ({
  id: 1,
  globalId: "SF1",
  name: "A field",
  content: null,
  selectedOptions: null,
  definition: null,
  columnIndex: 1,
  attachment: null,
  mandatory: false,
  ...attrs,
});

export const makeMockField = (
  attrs: Readonly<Partial<FieldModelAttrs> & { type: FieldModelAttrs["type"] }>
): FieldModel => new FieldModel(fieldAttrs(attrs), makeMockSample());
