import i18n from "@/modules/common/i18n";
import type { Document, DocumentAttrs } from "../definitions/Document";
import PersonModel from "./PersonModel";

export function newDocument({ globalId, name, id, owner }: DocumentAttrs): Document {
  return {
    id,
    name,
    globalId,
    owner: owner && new PersonModel(owner),
    deleted: false,
    cardTypeLabel: i18n.t("common:recordTypes.document.singular"),
    permalinkURL: `/globalId/${globalId}`,
    recordDetails: {},
    iconName: "document",
    recordTypeLabel: i18n.t("common:recordTypes.document.lower"),
  };
}
