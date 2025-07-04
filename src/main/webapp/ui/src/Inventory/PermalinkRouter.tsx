import SearchRouter from "./Search/SearchRouter";
import { observer } from "mobx-react-lite";
import { useParams, Navigate } from "react-router-dom";
import React, { useContext } from "react";
import { type PermalinkType } from "../stores/definitions/Search";
import NavigateContext from "../stores/contexts/Navigate";
import AlertContext, { mkAlert } from "../stores/contexts/Alert";
import {
  inventoryRecordTypeLabels,
  globalIdPatterns,
} from "../stores/definitions/BaseRecord";
import { match } from "../util/Util";

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
    version = parseInt(urlSearchParams.get("version")!, 10);
    if (isNaN(version)) return <h1>Invalid version parameter</h1>;
  }

  if (isNaN(parseInt(id, 10))) {
    const recordType = match<
      PermalinkType,
      keyof typeof inventoryRecordTypeLabels
    >([
      [(t) => t === "sample", "sample"],
      [(t) => t === "container", "container"],
      [(t) => t === "subsample", "subsample"],
      [(t) => t === "sampletemplate", "sampleTemplate"],
    ])(type);
    /*
     * If the user has provided the Global Id where they are meant to provide
     * an Id, and it matches the passed `type` then we can correct their
     * mistake by dropping the type prefix from the Global Id and silently
     * redirecting.
     */
    if (globalIdPatterns[recordType].test(id)) {
      return (
        <Navigate
          to={`/inventory/${type}/${id.slice(2)}?${urlSearchParams.toString()}`}
          replace={true}
        />
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
      })
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
