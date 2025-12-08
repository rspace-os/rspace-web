import type InvApiService from "../../common/InvApiService";
import type { BarcodeRecord, PersistedBarcodeAttrs } from "./Barcode";
import type { GlobalId } from "./BaseRecord";
import type { Document, DocumentAttrs } from "./Document";
import type { Identifier, IdentifierAttrs } from "./Identifier";
import type { InventoryRecord } from "./InventoryRecord";
import type { Person, PersonAttrs } from "./Person";

/**
 * Objects which implement this interface provide an abstraction layer over the
 * instantiation of new objects of various types, thereby allowing for
 * different instantiation logic to be utilised at different times. In other
 * words, any object implementing this interface is adhering to the OOP Factory
 * pattern.
 */
export interface Factory {
    /*
     * Instantiates a new InventoryRecord, given the respective attributes that
     * the instance of InventoryRecord requires. This is why
     * `Record<string, unknown>` is the type of the argument as it cannot be
     * determined before runtime what the attributes must be.
     */
    newRecord(params: Record<string, unknown> & { globalId: GlobalId | null }): InventoryRecord;

    newPerson(attrs: PersonAttrs): Person;
    newBarcode(attrs: PersistedBarcodeAttrs): BarcodeRecord;
    newIdentifier(attrs: IdentifierAttrs, parentGlobalId: GlobalId, ApiService: typeof InvApiService): Identifier;
    newDocument(attrs: DocumentAttrs): Document;

    /*
     * `newFactory` is used for creating a new factory, of the same type, without
     * any state that may be attached to the current instance. If the
     * implementation does not use any state is MAY return itself, however, if
     * there is any state at all then it MUST return a new instance.
     */
    newFactory(): Factory;
}
