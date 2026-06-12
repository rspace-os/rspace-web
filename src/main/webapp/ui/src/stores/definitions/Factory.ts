// biome-ignore lint/style/useImportType: initial biome migration
import InvApiService from "../../common/InvApiService";
// biome-ignore lint/style/useImportType: initial biome migration
import { type BarcodeRecord, type PersistedBarcodeAttrs } from "./Barcode";
// biome-ignore lint/style/useImportType: initial biome migration
import { type GlobalId } from "./BaseRecord";
// biome-ignore lint/style/useImportType: initial biome migration
import { type Document, type DocumentAttrs } from "./Document";
// biome-ignore lint/style/useImportType: initial biome migration
import { type Identifier, type IdentifierAttrs } from "./Identifier";
// biome-ignore lint/style/useImportType: initial biome migration
import { type InventoryRecord } from "./InventoryRecord";
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
