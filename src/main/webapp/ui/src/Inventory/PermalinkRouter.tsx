import { observer } from "mobx-react-lite";
// biome-ignore lint/style/useImportType: initial biome migration
import React, { useContext } from "react";
import { Navigate, useParams } from "react-router-dom";
import AlertContext, { mkAlert } from "../stores/contexts/Alert";
import NavigateContext from "../stores/contexts/Navigate";
import { globalIdPatterns, inventoryRecordTypeLabels } from "../stores/definitions/BaseRecord";
// biome-ignore lint/style/useImportType: initial biome migration
import { type PermalinkType } from "../stores/definitions/Search";
import { match } from "../util/Util";
import SearchRouter from "./Search/SearchRouter";

type PermalinkRouterArgs = {
  type: PermalinkType;
};

function PermalinkRouter({ type }: PermalinkRouterArgs): React.ReactNode {
  const { id } = useParams();
  const { useLocation } = useContext(NavigateContext);
  const urlSearchParams = new URLSearchParams(useLocation().search);
  const { addAlert } = useContext(AlertContext);

  if (!id) return <Navigate to="/inventory" replace={true} />;

  let version = null;
  if (urlSearchParams.has("version")) {
    // biome-ignore lint/style/noNonNullAssertion: initial biome migration
    version = parseInt(urlSearchParams.get("version")!, 10);
    // biome-ignore lint/suspicious/noGlobalIsNan: initial biome migration
    if (isNaN(version)) return <h1>Invalid version parameter</h1>;
  }

  // biome-ignore lint/suspicious/noGlobalIsNan: initial biome migration
  if (isNaN(parseInt(id, 10))) {
    const recordType = match<PermalinkType, keyof typeof inventoryRecordTypeLabels>([
      [(t) => t === "sample", "sample"],
      [(t) => t === "container", "container"],
      [(t) => t === "subsample", "subsample"],
      [(t) => t === "sampletemplate", "sampleTemplate"],
    ])(type);
    /*
     * If the user has provided the Global Id where they are meant to provide
     * an Id, and it matches the passed `type` then we can correct their
     * mistake by dropping the type prefix from the Global Id and silently
     * redirecting. A versioned Global Id (e.g. SS4v1) carries the version in
     * its suffix; surface it as the `version` search param rather than
     * dropping it.
     */
    if (globalIdPatterns[recordType].test(id)) {
      const versionSuffix = /v(\d+)$/i.exec(id);
      if (versionSuffix) urlSearchParams.set("version", versionSuffix[1]);
      return (
        <Navigate to={`/inventory/${type}/${parseInt(id.slice(2), 10)}?${urlSearchParams.toString()}`} replace={true} />
      );
    }
    /*
     * If the provided `id` is otherwise invalid then there is no clever
     * recovery we can do but redirect to the root of the application and
     * display an error.
     */
    addAlert(
      mkAlert({
        message: `"${id}" is not a valid ${inventoryRecordTypeLabels[recordType]} id.`,
        variant: "error",
      }),
    );
    return <Navigate to="/inventory" replace={true} />;
  }

  return (
    <SearchRouter
      paramsOverride={{
        permalink: { type, id: parseInt(id, 10), version },
      }}
    />
  );
}

export default observer(PermalinkRouter);
