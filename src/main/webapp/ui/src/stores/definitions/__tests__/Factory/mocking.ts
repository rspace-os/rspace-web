/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import { type Factory } from "../../../definitions/Factory";
import PersonModel from "../../../models/PersonModel";
import { type PersonAttrs } from "../../Person";
import { type PersistedBarcodeAttrs, type BarcodeRecord } from "../../Barcode";
import { type DocumentAttrs, type Document } from "../../Document";
import { type InventoryRecord } from "../../InventoryRecord";
import { type IdentifierAttrs, type Identifier } from "../../Identifier";
import { type GlobalId } from "../../BaseRecord";
import InvApiService from "../../../../common/InvApiService";

type FactoryOverrides = {
  newRecord?: (
    params: Record<string, unknown> & { globalId: GlobalId | null }
  ) => InventoryRecord;
  newPerson?: (attrs: PersonAttrs) => PersonModel;
  newBarcode?: (attrs: PersistedBarcodeAttrs) => BarcodeRecord;
  newDocument?: (attrs: DocumentAttrs) => Document;
  newIdentifier?: (
    attrs: IdentifierAttrs,
    parentGlobalId: GlobalId,
    ApiService: typeof InvApiService
  ) => Identifier;
  newFactory?: () => Factory;
};

export const mockFactory = (overrides?: FactoryOverrides): Factory => {
  const f = (): Factory => ({
    newRecord: jest.fn(),
    newPerson: jest.fn().mockReturnValue({} as PersonModel),
    newBarcode: jest.fn().mockReturnValue({} as BarcodeRecord),
    newIdentifier: jest.fn().mockReturnValue({} as Identifier),
    newDocument: jest.fn().mockReturnValue({} as Document),
    newFactory: jest.fn().mockImplementation(f),
    ...overrides,
  });
  return f();
};
