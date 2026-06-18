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
  /**
   * False when the viewer cannot read the target. Deliberately conflates
   * unshared, never-shared, nonexistent, and hard-deleted-by-another-owner:
   * all are redacted identically (ADR-0002), so false never discloses
   * whether the record exists.
   */
  readable: boolean;
};

/**
 * Resolves the current state of a link target for the link card. Returns null
 * while loading and on any failure (network error, malformed id): the card
 * then renders no state pill and keeps its Open action, the same as before
 * the summary existed. Missing and unreadable targets are not failures: the
 * server resolves them to a redacted summary with readable false.
 */
export default function useLinkTargetSummary(globalId: string): LinkTargetSummary | null {
  const [summary, setSummary] = useState<LinkTargetSummary | null>(null);

  useEffect(() => {
    let cancelled = false;
    setSummary(null);
    // No target (empty field, or callers that only want a summary while editing)
    // has nothing to resolve; skip the request rather than hitting the endpoint
    // with a blank id.
    if (!globalId) {
      return;
    }
    ApiService.get<LinkTargetSummary>(`linkTargets/${encodeURIComponent(globalId)}/summary`).then(
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
