/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */

import { type Factory } from "../../../definitions/Factory";
import PersonModel from "../../../models/PersonModel";
import { type PersonAttrs } from "../../Person";
import { type BarcodeAttrs, type BarcodeRecord } from "../../Barcode";
import { type DocumentAttrs, type Document } from "../../Document";
import { type InventoryRecord } from "../../InventoryRecord";
import { type IdentifierAttrs, type Identifier } from "../../Identifier";
import { type GlobalId } from "../../BaseRecord";

type FactoryOverrides = {|
  newRecord?: (any) => InventoryRecord,
  newPerson?: (PersonAttrs) => PersonModel,
  newBarcode?: (BarcodeAttrs) => BarcodeRecord,
  newDocument?: (DocumentAttrs) => Document,
  newIdentifier?: (IdentifierAttrs, GlobalId) => Identifier,
  newFactory?: () => Factory,
|};

export const mockFactory: (?FactoryOverrides) => Factory = (
  overrides: ?FactoryOverrides
) => {
  const f: () => Factory = () => ({
    newRecord: jest.fn<[any], InventoryRecord>(),
    newPerson: jest.fn<[PersonAttrs], PersonModel>(),
    newBarcode: jest.fn<[BarcodeAttrs], BarcodeRecord>(),
    newIdentifier: jest.fn<[IdentifierAttrs, GlobalId], Identifier>(),
    newDocument: jest.fn<[DocumentAttrs], Document>(),
    newFactory: jest.fn<[], Factory>().mockImplementation(f),
    ...overrides,
  });
  return f();
};
