import React from "react";
import useOauthToken from "@/hooks/auth/useOauthToken";
import { useGetStoichiometryQuery } from "@/modules/stoichiometry/queries";
import { toEditableMolecules } from "@/tinyMCE/stoichiometry/editableMolecules";
import type { StoichiometryTableProps } from "./types";
import StoichiometryTableGrid from "./StoichiometryTableGrid";

export default function StaticStoichiometryTable({
  stoichiometryId,
  stoichiometryRevision,
}: StoichiometryTableProps): React.ReactNode {
  const { getToken } = useOauthToken();
  const { data } = useGetStoichiometryQuery({
    stoichiometryId,
    revision: stoichiometryRevision,
    getToken,
  });
  const molecules = React.useMemo(() => toEditableMolecules(data), [data.id, data.revision]);

  return <StoichiometryTableGrid editable={false} allMolecules={molecules} />;
}
