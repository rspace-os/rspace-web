import type InvApiService from "../../../common/InvApiService";
import type { BarcodeRecord, PersistedBarcodeAttrs } from "../../definitions/Barcode";
import { type GlobalId, globalIdPatterns } from "../../definitions/BaseRecord";
import type { Document, DocumentAttrs } from "../../definitions/Document";
import type { Factory } from "../../definitions/Factory";
import type { Identifier, IdentifierAttrs } from "../../definitions/Identifier";
import type { InventoryRecord } from "../../definitions/InventoryRecord";
import type { PersonAttrs } from "../../definitions/Person";
import { PersistedBarcode } from "../Barcode";
import ContainerModel, { type ContainerAttrs } from "../ContainerModel";
import { newDocument } from "../Document";
import IdentifierModel from "../IdentifierModel";
import InventoryBaseRecord from "../InventoryBaseRecord";
import PersonModel from "../PersonModel";
import SampleModel, { type SampleAttrs } from "../SampleModel";
import SubSampleModel, { type SubSampleAttrs } from "../SubSampleModel";
import TemplateModel, { type TemplateAttrs } from "../TemplateModel";

/**
 * A Factory that has no state and always instantiates a new object whenever it
 * is invoked. This is useful where the scope of the instantiated objects is
 * known to be small, such as in a test script, and where more complex
 * instantiation logic is not necessary.
 */
export default class AlwaysNewFactory implements Factory {
    newRecord(params: Record<string, unknown> & { globalId: GlobalId }): InventoryRecord {
        if (params instanceof InventoryBaseRecord)
            throw new Error("Cannot instantiate Record from InventoryBaseRecord");
        const g = params.globalId ?? "";
        const patterns = globalIdPatterns;
        // prettier-ignore
        const record = patterns.sample.test(g)
            ? new SampleModel(this, params as SampleAttrs)
            : patterns.subsample.test(g)
              ? new SubSampleModel(this, params as SubSampleAttrs)
              : patterns.container.test(g)
                ? new ContainerModel(this, params as ContainerAttrs)
                : patterns.sampleTemplate.test(g)
                  ? new TemplateModel(this, params as TemplateAttrs)
                  : patterns.bench.test(g)
                    ? new ContainerModel(this, params as ContainerAttrs)
                    : /* otherwise */ null;
        if (!record) throw new Error("Unknown Global ID");
        return record;
    }

    newPerson(attrs: PersonAttrs): PersonModel {
        return new PersonModel(attrs);
    }

    newBarcode(attrs: PersistedBarcodeAttrs): BarcodeRecord {
        return new PersistedBarcode(attrs);
    }

    newIdentifier(attrs: IdentifierAttrs, parentGlobalId: GlobalId, ApiService: typeof InvApiService): Identifier {
        return new IdentifierModel(attrs, parentGlobalId, ApiService);
    }

    newDocument(attrs: DocumentAttrs): Document {
        return newDocument(attrs);
    }

    newFactory(): Factory {
        return new AlwaysNewFactory();
    }
}
