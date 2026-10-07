import type { DataCiteClient } from "@/__tests__/e2e/api/clients/DataCiteClient";
import type { InventoryClient } from "@/__tests__/e2e/api/clients/InventoryClient";
import { env } from "@/__tests__/e2e/env";
import { IMPORTABLE_DOI_PREFIX, IMPORTABLE_DOI_SUFFIX_PREFIX } from "@/__tests__/e2e/mocks/datacite";
import { uniqueName } from "@/__tests__/e2e/testData";

export type ImportableDataCitePid = {
  pid: string;
  name: string;
  owner: string;
  manufacturer: string;
};

/**
 * A Findable DataCite Instrument DOI that no RSpace instrument links to. Real mode mints and publishes one
 * on a throwaway instrument, then trashes it to release the link.
 */
export async function importableDataCitePid(
  { clientInventory, clientDataCite }: { clientInventory: InventoryClient; clientDataCite: DataCiteClient },
  realNamePrefix: string,
): Promise<ImportableDataCitePid> {
  if (env.integrationMode !== "real") {
    const suffix = uniqueName(IMPORTABLE_DOI_SUFFIX_PREFIX);
    return {
      pid: `${IMPORTABLE_DOI_PREFIX}/${suffix}`,
      name: `E2E Import Target ${suffix}`,
      owner: "E2E Test Institution",
      manufacturer: "E2E Instrument Co",
    };
  }
  const name = uniqueName(realNamePrefix);
  const throwaway = await clientInventory.createInstrument({ name });
  const info = await clientInventory.registerIdentifier({ parentGlobalId: throwaway.globalId });
  await clientInventory.publishIdentifier(info.id);
  await clientInventory.deleteInstrument(throwaway.id);

  // RSpace mints with no HostingInstitution, so the import falls back to the publisher for Owner.
  // Multiple creators are joined with PidinstRecordMapper.JOIN ("; ").
  const { publisher, creators } = await clientDataCite.getMetadata(info.doi);
  if (!publisher || creators.length === 0) {
    throw new Error(
      `DOI ${info.doi} has no publisher or creator to import: ${JSON.stringify({ publisher, creators })}`,
    );
  }
  return { pid: info.doi, name, owner: publisher, manufacturer: creators.join("; ") };
}
