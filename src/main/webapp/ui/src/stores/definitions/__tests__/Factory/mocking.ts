/*
 */

import { vi } from "vitest";
// biome-ignore lint/style/useImportType: initial biome migration
import InvApiService from "../../../../common/InvApiService";
// biome-ignore lint/style/useImportType: initial biome migration
import { type Factory } from "../../../definitions/Factory";
// biome-ignore lint/style/useImportType: initial biome migration
import PersonModel from "../../../models/PersonModel";
// biome-ignore lint/style/useImportType: initial biome migration
import { type BarcodeRecord, type PersistedBarcodeAttrs } from "../../Barcode";
// biome-ignore lint/style/useImportType: initial biome migration
import { type GlobalId } from "../../BaseRecord";
// biome-ignore lint/style/useImportType: initial biome migration
import { type Document, type DocumentAttrs } from "../../Document";
// biome-ignore lint/style/useImportType: initial biome migration
import { type Identifier, type IdentifierAttrs } from "../../Identifier";
// biome-ignore lint/style/useImportType: initial biome migration
import { type InventoryRecord } from "../../InventoryRecord";
// biome-ignore lint/style/useImportType: initial biome migration
import { type PersonAttrs } from "../../Person";

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
