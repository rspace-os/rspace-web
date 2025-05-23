import { type GlobalId, globalIdPatterns } from "../../definitions/BaseRecord";
import ContainerModel, { ContainerAttrs } from "../ContainerModel";
import PersonModel from "../PersonModel";
import { type PersonAttrs } from "../../definitions/Person";
import Result from "../InventoryBaseRecord";
import SampleModel, { SampleAttrs } from "../SampleModel";
import SubSampleModel, { SubSampleAttrs } from "../SubSampleModel";
import TemplateModel, { TemplateAttrs } from "../TemplateModel";
import { type Factory } from "../../definitions/Factory";
import {
  type PersistedBarcodeAttrs,
  type BarcodeRecord,
} from "../../definitions/Barcode";
import { PersistedBarcode } from "../Barcode";
import { Identifier, type IdentifierAttrs } from "../../definitions/Identifier";
import IdentifierModel from "../IdentifierModel";
import { type DocumentAttrs, type Document } from "../../definitions/Document";
import { newDocument } from "../Document";
import InvApiService from "../../../common/InvApiService";
import { InventoryRecord } from "../../definitions/InventoryRecord";

/**
 * A Factory that has no state and always instantiates a new object whenever it
 * is invoked. This is useful where the scope of the instantiated objects is
 * known to be small, such as in a test script, and where more complex
 * instantiation logic is not necessary.
 */
export default class AlwaysNewFactory implements Factory {
  newRecord(
    params: Record<string, unknown> & { globalId: GlobalId }
  ): InventoryRecord {
    if (params instanceof Result)
      throw new Error("Cannot instantiate Record from Result");
    const g = params.globalId ?? "";
    const patterns = globalIdPatterns;
    // prettier-ignore
    const record =
      patterns.sample.test(g)          ? new SampleModel   (this, params as SampleAttrs) :
      patterns.subsample.test(g)       ? new SubSampleModel(this, params as SubSampleAttrs) :
      patterns.container.test(g)       ? new ContainerModel(this, params as ContainerAttrs) :
      patterns.sampleTemplate.test(g)  ? new TemplateModel (this, params as TemplateAttrs) :
      patterns.bench.test(g)           ? new ContainerModel(this, params as ContainerAttrs) :
      /* otherwise */                    null;
    if (!record) throw new Error("Unknown Global ID");
    return record;
  }

  newPerson(attrs: PersonAttrs): PersonModel {
    return new PersonModel(attrs);
  }

  newBarcode(attrs: PersistedBarcodeAttrs): BarcodeRecord {
    return new PersistedBarcode(attrs);
  }

  newIdentifier(
    attrs: IdentifierAttrs,
    parentGlobalId: GlobalId,
    ApiService: typeof InvApiService
  ): Identifier {
    return new IdentifierModel(attrs, parentGlobalId, ApiService);
  }

  newDocument(attrs: DocumentAttrs): Document {
    return newDocument(attrs);
  }

  newFactory(): Factory {
    return new AlwaysNewFactory();
  }
}
