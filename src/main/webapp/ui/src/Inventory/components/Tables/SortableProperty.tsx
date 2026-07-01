import { observer } from "mobx-react-lite";
import type React from "react";
import { useContext } from "react";
import { useTranslation } from "react-i18next";
import TableSortLabel from "../../../components/TableSortLabel";
import SearchContext from "../../../stores/contexts/Search";
import type { AdjustableTableRowLabel } from "../../../stores/definitions/Tables";
import { translateAdjustableTableLabel } from "./adjustableTableLabels";

export type SortProperty = {
  key: string;
  label: AdjustableTableRowLabel;
  adjustColumn: boolean;
};

type SortablePropertyArgs = {
  property: SortProperty;
};

function SortableProperty({ property }: SortablePropertyArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  const { search } = useContext(SearchContext);

  const setOrder = (key: string) => {
    search.fetcher.setOrder(
      search.fetcher.isCurrentSort(key) ? search.fetcher.invertSortOrder() : search.fetcher.defaultSortOrder(key),
      key,
    );
  };

  return (
    <TableSortLabel
      active={search.fetcher.isCurrentSort(property.key)}
      direction={search.fetcher.order}
      onClick={() => setOrder(property.key)}
    >
      {translateAdjustableTableLabel(property.label, t)}
    </TableSortLabel>
  );
}

export default observer(SortableProperty);
