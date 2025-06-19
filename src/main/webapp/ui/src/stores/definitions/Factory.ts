//@flow

/*
 * The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT",
 * "SHOULD", "SHOULD NOT", "RECOMMENDED",  "MAY", and "OPTIONAL" in this
 * document are to be interpreted as described in RFC 2119.
 */

import { type PersonAttrs, type Person } from "./Person";
import { type PersistedBarcodeAttrs, type BarcodeRecord } from "./Barcode";
import { type InventoryRecord } from "./InventoryRecord";
import { type DocumentAttrs, type Document } from "./Document";
import { type IdentifierAttrs, type Identifier } from "./Identifier";
import { type GlobalId } from "./BaseRecord";
import InvApiService from "../../common/InvApiService";

/**
 * Objects which implement this interface provide an abstraction layer over the
 * instantiation of new objects of various types, thereby allowing for
 * different instantiation logic to be utilised at different times. In other
 * words, any object implementing this interface is adhering to the OOP Factory
 * pattern.
 */
export interface Factory {
  /*
   * Instantiates a new Result, given the respective attributes that the
   * instance of Result requires. This is why `any` is the type of the argument
   * as it cannot be determined before runtime what the attributes must be.
   */
  newRecord(
    params: Record<string, unknown> & { globalId: GlobalId | null }
  ): InventoryRecord;

  newPerson(attrs: PersonAttrs): Person;
  newBarcode(attrs: PersistedBarcodeAttrs): BarcodeRecord;
  newIdentifier(
    attrs: IdentifierAttrs,
    parentGlobalId: GlobalId,
    ApiService: typeof InvApiService
  ): Identifier;
  newDocument(attrs: DocumentAttrs): Document;

  /*
   * `newFactory` is used for creating a new factory, of the same type, without
   * any state that may be attached to the current instance. If the
   * implementation does not use any state is MAY return itself, however, if
   * there is any state at all then it MUST return a new instance.
   */
  newFactory(): Factory;
}
