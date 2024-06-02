//@flow

import SearchRouter from "./Search/SearchRouter";
import { observer } from "mobx-react-lite";
import { useParams } from "react-router-dom";
import React, { type Node, type ComponentType, useContext } from "react";
import { type PermalinkType } from "../stores/definitions/Search";
import NavigateContext, { type UseLocation } from "../stores/contexts/Navigate";

type PermalinkRouterArgs = {|
  type: PermalinkType,
|};

function PermalinkRouter({ type }: PermalinkRouterArgs): Node {
  const { id } = useParams();
  const { useLocation } = useContext(NavigateContext);
  const urlSearchParams = new URLSearchParams(
    (useLocation(): UseLocation).search
  );
  let version = null;
  if (urlSearchParams.has("version")) {
    version = parseInt(urlSearchParams.get("version"));
    if (isNaN(version)) return <h1>Invalid version parameter</h1>;
  }

  return (
    <SearchRouter
      paramsOverride={{
        permalink: { type, id: parseInt(id, 10), version },
      }}
    />
  );
}

export default (observer(PermalinkRouter): ComponentType<PermalinkRouterArgs>);
