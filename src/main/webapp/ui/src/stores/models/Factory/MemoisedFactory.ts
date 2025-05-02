import { type PersonId, type PersonAttrs } from "../../definitions/Person";
import { type GlobalId } from "../../definitions/BaseRecord";
import PersonModel from "../PersonModel";
import Result from "../Result";
import AlwaysNewFactory from "./AlwaysNewFactory";
import { type Factory } from "../../definitions/Factory";
import { type DocumentAttrs, type Document } from "../../definitions/Document";

/*
 * A Factory which memoises the instantiation of new objects, thereby
 * preventing the allocation of additional memory. This is preffered where the
 * instantiated objects will exist for a long period of time and where a great
 * number of objects is likely to result in excessive memory usage. Care should
 * be taken to use the `newFactory` method to ensure that new objects are
 * instantiated when the provided attributes have changed as an equality check
 * is performed on only a subset of the attributes.
 */
export default class MemoisedFactory extends AlwaysNewFactory {
  recordCache: Map<GlobalId, Result>;
  personCache: Map<PersonId, PersonModel>;
  documentCache: Map<GlobalId, Document>;

  constructor() {
    super();
    this.recordCache = new Map();
    this.personCache = new Map();
    this.documentCache = new Map();
  }

  /*
   * New result objects are only created when the Global ID has not been seen
   * previously. Any new data passed as `params` will not be assigned to the
   * existing object, so do call `populateFromJson` on the returned object if
   * the data has changed.
   */
  newRecord(params: any): Result {
    if (params instanceof Result)
      throw new Error("Cannot instantiate Record from Result");
    const globalId = params.globalId;
    const existingRecord = this.recordCache.get(globalId);
    if (existingRecord) {
      return existingRecord;
    } else {
      const newResult = super.newRecord(params);
      this.recordCache.set(globalId, newResult);
      return newResult;
    }
  }

  /*
   * New people objects are only created when the person's id is different. If
   * the `attrs` are different to the existing object but the id remains the
   * same then the `attrs` are ignored and the old data is returned.
   */
  newPerson(attrs: PersonAttrs): PersonModel {
    const id = attrs.id;
    const makeNew = () => {
      const newPerson = super.newPerson(attrs);
      this.personCache.set(id, newPerson);
      return newPerson;
    };
    return this.personCache.get(id) ?? makeNew();
  }

  newDocument(attrs: DocumentAttrs): Document {
    const globalId = attrs.globalId;
    const existingDocument = this.documentCache.get(globalId);
    if (existingDocument) return existingDocument;
    const newDocument = super.newDocument(attrs);
    this.documentCache.set(globalId, newDocument);
    return newDocument;
  }

  newFactory(): Factory {
    return new MemoisedFactory();
  }
}
