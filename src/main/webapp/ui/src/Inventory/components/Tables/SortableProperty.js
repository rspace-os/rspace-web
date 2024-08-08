// @flow

import React, { useContext, type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import TableSortLabel from "../../../components/TableSortLabel";
import SearchContext from "../../../stores/contexts/Search";
import { type AdjustableTableRowLabel } from "../../../stores/definitions/Tables";

export type SortProperty = {
  key: string,
  label: AdjustableTableRowLabel,
  adjustColumn: boolean,
};

type SortablePropertyArgs = {|
  property: SortProperty,
|};

function SortableProperty({ property }: SortablePropertyArgs): Node {
  const { search } = useContext(SearchContext);

  const setOrder = (key: string) => {
    search.fetcher.setOrder(
      search.fetcher.isCurrentSort(key)
        ? search.fetcher.invertSortOrder()
        : search.fetcher.defaultSortOrder(key),
      key
    );
  };

  return (
    <TableSortLabel
      active={search.fetcher.isCurrentSort(property.key)}
      direction={search.fetcher.order}
      onClick={() => setOrder(property.key)}
    >
      {property.label}
    </TableSortLabel>
  );
}

export default (observer(
  SortableProperty
): ComponentType<SortablePropertyArgs>);
