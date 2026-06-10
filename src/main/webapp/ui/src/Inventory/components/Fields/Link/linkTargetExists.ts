import ApiService from "../../../../common/InvApiService";
import { getWorkspaceRecordInformationAjax } from "@/modules/workspace/queries";
import { GLOBAL_ID_PATTERN } from "./linkTarget";

const INVENTORY_PREFIX_TO_PATH: Record<string, string> = {
  SA: "samples",
  SS: "subSamples",
  IC: "containers",
  IN: "instruments",
  IT: "sampleTemplates",
};

const ELN_PREFIXES = new Set(["SD", "NB", "GL"]);

/**
 * Reports whether a link target Global ID resolves to a real record the current
 * user can read, mirroring the backend's create-time existence check so typed
 * Global IDs can be rejected before the record is saved. A version suffix is
 * ignored: only the base record needs to exist. Any resolution failure (not
 * found, no permission, network error) is reported as "does not exist".
 */
export async function checkLinkTargetExists(
  globalId: string,
): Promise<boolean> {
  const parsed = GLOBAL_ID_PATTERN.exec(globalId);
  if (!parsed) return false;
  const [, prefix, dbId] = parsed;
  try {
    const inventoryPath = INVENTORY_PREFIX_TO_PATH[prefix];
    if (inventoryPath) {
      await ApiService.get<unknown>(`${inventoryPath}/${dbId}`);
      return true;
    }
    if (ELN_PREFIXES.has(prefix)) {
      await getWorkspaceRecordInformationAjax({ recordId: Number(dbId) });
      return true;
    }
    return false;
  } catch {
    return false;
  }
}
