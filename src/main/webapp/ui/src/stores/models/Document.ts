import { type Document, type DocumentAttrs } from "../definitions/Document";
import PersonModel from "./PersonModel";

export function newDocument({
  globalId,
  name,
  id,
  owner,
}: DocumentAttrs): Document {
  return {
    id,
    name,
    globalId,
    owner: owner && new PersonModel(owner),
    deleted: false,
    cardTypeLabel: "Document",
    permalinkURL: `/globalId/${globalId}`,
    recordDetails: {},
    iconName: "document",
    recordTypeLabel: "document",
  };
}
