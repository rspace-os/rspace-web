import type { InventoryClient } from "@/__tests__/e2e/api/clients/InventoryClient";
import { env } from "@/__tests__/e2e/env";
import { IMPORTABLE_DOI_PREFIX, IMPORTABLE_DOI_SUFFIX_PREFIX } from "@/__tests__/e2e/mocks/datacite";
import { uniqueName } from "@/__tests__/e2e/testData";

/**
 * A Findable DataCite Instrument DOI that no RSpace instrument links to. Real mode mints and publishes one
 * on a throwaway instrument, then trashes it to release the link.
 */
export async function importableDataCitePid(
  clientInventory: InventoryClient,
  realNamePrefix: string,
): Promise<{ pid: string; name: string }> {
  if (env.integrationMode !== "real") {
    const suffix = uniqueName(IMPORTABLE_DOI_SUFFIX_PREFIX);
    return { pid: `${IMPORTABLE_DOI_PREFIX}/${suffix}`, name: `E2E Import Target ${suffix}` };
  }
  const name = uniqueName(realNamePrefix);
  const throwaway = await clientInventory.createInstrument({ name });
  const info = await clientInventory.registerIdentifier({ parentGlobalId: throwaway.globalId });
  await clientInventory.publishIdentifier(info.id);

  await clientInventory.deleteInstrument(throwaway.id);
  return { pid: info.doi, name };
}
