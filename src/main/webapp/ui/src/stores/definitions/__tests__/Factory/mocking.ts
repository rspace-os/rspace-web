/*
 */

import { vi } from "vitest";
import type InvApiService from "../../../../common/InvApiService";
import type { Factory } from "../../../definitions/Factory";
import type PersonModel from "../../../models/PersonModel";
import type { BarcodeRecord, PersistedBarcodeAttrs } from "../../Barcode";
import type { GlobalId } from "../../BaseRecord";
import type { Document, DocumentAttrs } from "../../Document";
import type { Identifier, IdentifierAttrs } from "../../Identifier";
import type { InventoryRecord } from "../../InventoryRecord";
import type { PersonAttrs } from "../../Person";

type FactoryOverrides = {
  newRecord?: (params: Record<string, unknown> & { globalId: GlobalId | null }) => InventoryRecord;
  newPerson?: (attrs: PersonAttrs) => PersonModel;
  newBarcode?: (attrs: PersistedBarcodeAttrs) => BarcodeRecord;
  newDocument?: (attrs: DocumentAttrs) => Document;
  newIdentifier?: (attrs: IdentifierAttrs, parentGlobalId: GlobalId, ApiService: typeof InvApiService) => Identifier;
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
