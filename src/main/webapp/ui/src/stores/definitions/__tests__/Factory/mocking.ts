/*
 */

import { vi } from "vitest";
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
    newRecord: vi.fn(),
    newPerson: vi.fn().mockReturnValue({}),
    newBarcode: vi.fn().mockReturnValue({}),
    newIdentifier: vi.fn().mockReturnValue({}),
    newDocument: vi.fn().mockReturnValue({}),
    newFactory: vi.fn().mockImplementation(f),
    ...overrides,
  });
  return f();
};

