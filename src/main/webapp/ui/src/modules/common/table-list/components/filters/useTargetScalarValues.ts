import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useRelationshipOptions } from "@/modules/common/relationship-picker/relationshipOptionQueries";
import type { RelationshipSource } from "@/modules/common/relationship-picker/relationshipSources";
import type { ValueSuggestions } from "./SuggestedValueInput";

export function useTargetScalarValues({
  source,
  term,
  enabled,
  idLinkLabel,
}: {
  source: RelationshipSource;
  term: string;
  enabled: boolean;
  idLinkLabel: (globalId: string) => string;
}): ValueSuggestions {
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const { options, failed, loading } = useRelationshipOptions({
    source,
    term,
    token,
    labels: { idLinkLabel },
    enabled,
  });
  return {
    values: [...new Set(options.map((option) => option.label))],
    loading,
    failed,
    hasMore: false,
  };
}
