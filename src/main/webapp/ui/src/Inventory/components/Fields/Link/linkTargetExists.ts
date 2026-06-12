import ApiService from "../../../../common/InvApiService";
import { getWorkspaceRecordInformationAjax } from "@/modules/workspace/queries";
import { GLOBAL_ID_PATTERN, INVENTORY_PREFIX_TO_API_PATH } from "./linkTarget";

const ELN_PREFIXES = new Set(["SD", "NB", "GL"]);

/**
 * Reports whether a link target Global ID resolves to a real record the current
 * user can read, mirroring the backend's create-time existence check so typed
 * Global IDs can be rejected before the record is saved. A version suffix is
 * ignored: only the base record needs to exist. Any resolution failure (not
 * found, no permission, network error) is reported as "does not exist".
 *
 * For inventory targets a 200 response is not enough: the inventory GET also
 * succeeds for items the user may only see a redacted "limited read" view of,
 * while linking requires full READ permission (the backend's
 * LinkTargetResolver rejects the link at save otherwise). So the response's
 * permittedActions must include READ; anything less is reported as
 * not-visible, giving the same generic message as a missing id so existence
 * is not leaked.
 */
export async function checkLinkTargetExists(
  globalId: string,
): Promise<boolean> {
  const parsed = GLOBAL_ID_PATTERN.exec(globalId);
  if (!parsed) return false;
  const [, prefix, dbId] = parsed;
  try {
    const inventoryPath = INVENTORY_PREFIX_TO_API_PATH[prefix];
    if (inventoryPath) {
      const { data } = await ApiService.get<{ permittedActions?: unknown }>(
        `${inventoryPath}/${dbId}`,
      );
      const permittedActions = data?.permittedActions;
      return (
        Array.isArray(permittedActions) && permittedActions.includes("READ")
      );
    }
    if (ELN_PREFIXES.has(prefix)) {
      const info = await getWorkspaceRecordInformationAjax({
        recordId: Number(dbId),
      });
      // the workspace endpoint resolves by numeric id alone, so a typed id
      // can resolve a different record kind sharing the number (e.g. "GL150"
      // resolves folder FL150): only an exact Global ID match counts
      return info.oid.idString === `${prefix}${dbId}`;
    }
    return false;
  } catch {
    return false;
  }
}
