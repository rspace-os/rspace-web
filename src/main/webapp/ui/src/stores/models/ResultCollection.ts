import Result, { type ResultEditableFields } from "./Result";
import { action, observable, makeObservable, computed, override } from "mobx";
import { match } from "../../util/Util";
import * as ArrayUtils from "../../util/ArrayUtils";
import RsSet, { flattenWithIntersectionWithEq } from "../../util/set";
import { truncateIsoTimestamp } from "../definitions/Units";
import {
  type HasEditableFields,
} from "../definitions/Editable";
import { PersistedBarcode } from "./Barcode";
import { type SharedWithGroup } from "../definitions/Group";
import { areSameTag } from "../definitions/Tag";

export type BatchName = {|
  common: string,
  suffix: "NONE" | "INDEX_NUMBER" | "INDEX_LETTER" | "CREATED",
|};

/*
 * For adding a numerical index as a suffix to the name of records, the indexes
 * must be consistently formatted; adding leading zeros to ensure they sort
 * correctly.
 */
export const formatIndex = (index: number, numOfRecords: number): string => {
  // +1 as if numOfRecords = 10, widthWithPadding should be 2
  const widthWithPadding = Math.ceil(Math.log(numOfRecords + 1) / Math.log(10));

  // +1 to output 1-based indexing
  return (index + 1).toString().padStart(widthWithPadding, "0");
};

export type ResultCollectionEditableFields = {
  ...$Diff<ResultEditableFields, {| name: string |}>,
  name: BatchName,
  ...
};

/*
 * This is a wrapper around a set of Results, making it easier to perform batch
 * operations e.g. editing.
 */
export default class ResultCollection<ResultSubtype: Result> {
  records: RsSet<ResultSubtype>;
  name: BatchName;
  sharedWith: Array<SharedWithGroup>;

  constructor(records: RsSet<ResultSubtype>) {
    makeObservable(this, {
      records: observable,
      name: observable,
      sharedWith: observable,
      size: computed,
      setFieldsDirty: action,
      fieldValues: computed,
      canChooseWhichToEdit: computed,
    });
    this.records = records;
    const currentNames = new RsSet(this.records.map((r) => r.name));
    this.name =
      currentNames.size === 1
        ? { common: currentNames.first, suffix: "NONE" }
        : { common: "", suffix: "NONE" };
    this.sharedWith = [];
  }

  get size(): number {
    return this.records.size;
  }

  get fieldValues(): { ...ResultCollectionEditableFields } {
    const currentDescriptions = new RsSet(
      this.records.map((r) => r.description)
    );

    /*
     * Image preview can only be shown after the user has modified the image,
     * even if all of the records have the same image to begin with. This is
     * because the API returns different URLs for the images of different
     * records (regardless of content) and these then get converted into
     * distinct blob URLs by ImageStore. As such, it is not possible for us to
     * identify if two records have the same image. Until, that is, the user
     * chooses a new image for all of the records. The exception to this is
     * when multiple subsamples inherit their image from their (same) sample,
     * which is because the API returns the same URL for the images of each
     * subsample.
     */
    const currentImages = new RsSet(this.records.map((r) => r.image));

    /*
     * Only new barcodes are shown as editing/deleting existing barcodes across
     * a set of records is tricky because even if two barcode on two different
     * records have the same data, type, and description they wont have the
     * same id. Therefore, the code/database's notion of equality is different
     * to a user's and so to keep things simple we're only supporting the batch
     * adding of new barcodes.
     */
    const newBarcodes = new RsSet(
      ArrayUtils.filterClass(
        PersistedBarcode,
        this.records.first.barcodes
      ).filter((b) => b.id === null)
    );

    const currentSharingMode = new RsSet(
      this.records.map((r) => r.sharingMode)
    );

    return {
      image: currentImages.first ?? null,
      newBase64Image: currentImages.first ?? null,
      description: currentDescriptions.first ?? "",
      name: this.name,

      // all the tags that the records have in common
      tags: flattenWithIntersectionWithEq(
        new RsSet(this.records).map((r) => new RsSet(r.tags)),
        areSameTag
      ).toArray(),

      barcodes: [...newBarcodes],
      sharingMode:
        currentSharingMode.size === 1
          ? currentSharingMode.first
          : "OWNER_GROUPS", // owner's groups acts as default
      sharedWith: this.sharedWith,
    };
  }

  /*
   * User can choose which fields to enable, and only those that are
   * saved to the server.
   */
  get canChooseWhichToEdit(): boolean {
    return true;
  }

  isFieldEditable(fieldName: string): boolean {
    return this.records.every((r) => r.isFieldEditable(fieldName));
  }

  //eslint-disable-next-line no-unused-vars
  get noValueLabel(): {[key in keyof ResultCollectionEditableFields]: ?string} {
    const currentNames = new RsSet(this.records.map((r) => r.name));
    const currentDescriptions = new RsSet(
      this.records.map((r) => r.description)
    );
    const currentTags = new RsSet(this.records.map((r) => r.tags));
    const currentImages = new RsSet(this.records.map((r) => r.image));
    return {
      image:
        currentImages.size === 1 ? null : "Enable to set image for all items",
      newBase64Image: null,
      name: currentNames.size === 1 ? null : "Varies",
      description: currentDescriptions.size === 1 ? null : "Varies",
      tags: currentTags.size === 1 ? null : "Varies",
      barcodes: null, // null because we show an empty table
      sharingMode: null, // not used by AccessPermission form field
      sharedWith: null, // not used by AccessPermission form field
    };
  }

  setFieldsDirty(newFieldValues: {}): void {
    const values: any = { ...newFieldValues };
    if ("name" in values) {
      this.name = values.name;
      delete values.name;
    }
    if ("sharedWith" in values) {
      this.sharedWith = values.sharedWith;
    }

    const commonTags = flattenWithIntersectionWithEq(
      new RsSet(this.records).map((r) => new RsSet(r.tags)),
      areSameTag
    );

    this.records
      .toArray((a, b) => (a.id ?? -1) - (b.id ?? -1))
      .map((record, i) => {
        if ("name" in newFieldValues) {
          // $FlowExpectedError[prop-missing]
          const name = newFieldValues.name;
          const suffix = match<string, () => string>([
            [(s) => s === "NONE", () => ""],
            [
              (s) => s === "INDEX_NUMBER",
              () => `.${formatIndex(i, this.records.size)}`,
            ],
            [
              (s) => s === "INDEX_LETTER" && this.records.size >= 27,
              () => {
                throw new Error(
                  "Too many records selected for alphabetical index."
                );
              },
            ],
            [
              (s) => s === "INDEX_LETTER",
              () => `.${String.fromCharCode("A".charCodeAt(0) + i)}`,
            ],
            [
              (s) => s === "CREATED",
              () =>
                truncateIsoTimestamp(record.created, "second").orElse("ERROR"),
            ],
          ])(this.name.suffix)();
          values.name = `${name.common}${suffix}`;
        }

        if ("tags" in values) {
          /*
           * Only modify the tags that the records all have in common. There
           * are three possibilities to consider:
           *  -  The user is removing a tag that is common to all records
           *  -  The user is adding a tag that is new to all records
           *  -  The user is adding a tag that some of the records already have
           *
           *  Definitions:
           *  `commonTags`  : The set of tags that all of the records being
           *                  edited share.
           *  `values.tags` : The new set of tags, as provided by the UI. The
           *                  difference between `commonTags` and `values.tags`
           *                  is the edit being applied.
           *  `record.tags` : The set of tags that the current record being
           *                  considered has.
           *
           * When removing a tag that is common to all, `values.tags` will be a
           * strict subset of `commonTags`. If `record.tags` is `{ A, B, C }`,
           * `commonTags` is `{ B, C }`, and we're removing C so `values.tags`
           * is `{ B }`, then `({ A, B, C } - { B, C }) ∪ { B } = { A, B }`.
           *
           * If the user is adding a tag then `values.tags` will be a strict
           * superset of `commonTags`. If the tag is new to all records, or if
           * it is not in `record.tags` because the current `record` does not
           * have it, then `({ A, B, C } - { B, C }) ∪ { B, C, D }`, where D is
           * the new tag, will be `{ A, B, C, D }`.
           *
           * If the new tag is in some of the records including this `record`
           * then `record.tags` will be a superset of both `commonTags` and
           * `values.tags`. As such, this will all be a no-op.
           * `({ A, B, C, D } - { B, C }) ∪ { B, C, D } = { A, B, C, D }`
           */
          values.tags = new RsSet(record.tags)
            .subtractWithEq(commonTags, areSameTag)
            .unionWithEq(new RsSet(values.tags), areSameTag)
            .toArray();
        }

        record.setFieldsDirty(values);
      });
  }
}

export class MixedResultCollection
  extends ResultCollection<Result>
  implements HasEditableFields<ResultCollectionEditableFields>
{
  constructor(records: RsSet<Result>) {
    super(records);
    makeObservable(this, {
      setFieldsDirty: override,
      fieldValues: override,
    });
  }

  get fieldValues(): { ...ResultCollectionEditableFields } {
    return super.fieldValues;
  }

  //eslint-disable-next-line no-unused-vars
  get noValueLabel(): {[key in keyof ResultCollectionEditableFields]: ?string} {
    return super.noValueLabel;
  }

  setFieldsDirty(newFieldValues: {}): void {
    super.setFieldsDirty(newFieldValues);
  }

  setFieldEditable(fieldName: string, value: boolean): void {
    for (const record of this.records) {
      record.setFieldEditable(fieldName, value);
    }
  }
}
