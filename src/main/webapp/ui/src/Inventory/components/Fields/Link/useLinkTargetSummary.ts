import { useEffect, useState } from "react";
import ApiService from "../../../../common/InvApiService";

/**
 * Current state of a link target as reported by the lazy summary endpoint
 * (RSDEV-1182). Unreadable targets are redacted server-side to a
 * globalId-only summary, so name/type can be null even on success.
 */
export type LinkTargetSummary = {
  globalId: string | null;
  name: string | null;
  type: string | null;
  deleted: boolean;
};

/**
 * Resolves the current state of a link target for the link card. Returns null
 * while loading and on any failure (missing record, no permission, network
 * error): the card then renders no "Target deleted" pill and keeps its Open
 * action, the same as before the summary existed.
 */
export default function useLinkTargetSummary(
  globalId: string,
): LinkTargetSummary | null {
  const [summary, setSummary] = useState<LinkTargetSummary | null>(null);

  useEffect(() => {
    let cancelled = false;
    setSummary(null);
    ApiService.get<LinkTargetSummary>(
      `linkTargets/${encodeURIComponent(globalId)}/summary`,
    ).then(
      ({ data }) => {
        if (!cancelled) setSummary(data);
      },
      () => {
        if (!cancelled) setSummary(null);
      },
    );
    return () => {
      cancelled = true;
    };
  }, [globalId]);

  return summary;
}
