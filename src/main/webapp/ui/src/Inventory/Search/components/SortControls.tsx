import ListItemText from "@mui/material/ListItemText";
import MenuItem from "@mui/material/MenuItem";
import SvgIcon from "@mui/material/SvgIcon";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useContext, useState } from "react";
import DropdownButton from "../../../components/DropdownButton";
import StyledMenu from "../../../components/StyledMenu";
import SearchContext from "../../../stores/contexts/Search";
// biome-ignore lint/style/useImportType: initial biome migration
import { type AdjustableTableRowLabel } from "../../../stores/definitions/Tables";
import { sortProperties } from "../../../stores/models/InventoryBaseRecord";
import { match, toTitleCase } from "../../../util/Util";
// biome-ignore lint/style/useImportType: initial biome migration
import { type SortProperty } from "../../components/Tables/SortableProperty";

const SortAZIcon = ({ disabled }: { disabled: boolean }) => (
  <SvgIcon
    focusable="false"
    viewBox="0 0 50 50"
    role="presentation"
    sx={(theme) => ({
      borderRadius: theme.spacing(1),
      color: disabled ? "inherit" : theme.palette.standardIcon.main,
    })}
  >
    <g>
      <path
        d="M22.108,37.725l-2.543-7.074H9.827l-2.491,7.074h-4.39l9.529-25.574h4.529l9.529,25.574H22.108z M18.467,27.063
		l-2.387-6.933c-0.174-0.464-0.416-1.196-0.723-2.195c-0.308-0.999-0.52-1.73-0.636-2.195c-0.313,1.429-0.772,2.991-1.376,4.686
		l-2.299,6.637H18.467z"
      />
      <path d="M45.905,37.725H27.683v-2.893l12.84-19.006H28.03v-3.571h17.525v2.926L32.665,34.152h13.24V37.725z" />
    </g>
  </SvgIcon>
);

function SortControls(): React.ReactNode {
  const { search } = useContext(SearchContext);

  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);

  const handleClick = (event: { currentTarget: HTMLElement }) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = (): void => {
    setAnchorEl(null);
  };

  const setOrder = ({ key, label, adjustColumn }: SortProperty) => {
    search.fetcher.setOrder(
      search.fetcher.isCurrentSort(key) ? search.fetcher.invertSortOrder() : search.fetcher.defaultSortOrder(key),
      key,
    );
    if (adjustColumn) search.setAdjustableColumn(label, 0);
    handleClose();
  };

  const menuItemLabel = (key: string, label: AdjustableTableRowLabel) =>
    `${label} ${match<string, string>([
      [(k) => !search.fetcher.isCurrentSort(k), ""],
      [(k) => search.fetcher.isCurrentSort(k) && search.fetcher.isOrderDesc, "(A-Z)"],
      [(k) => search.fetcher.isCurrentSort(k) && !search.fetcher.isOrderDesc, "(Z-A)"],
    ])(key)}`;

  const disabled = search.searchView === "IMAGE" || search.searchView === "GRID";
  return (
    // biome-ignore lint/complexity/noUselessFragments: initial biome migration
    <>
      <DropdownButton
        name={<SortAZIcon disabled={disabled} />}
        onClick={handleClick}
        disabled={disabled}
        title={disabled ? `Cannot sort ${toTitleCase(search.searchView)} view.` : "Sort by"}
      >
        <StyledMenu anchorEl={anchorEl} keepMounted open={Boolean(anchorEl)} onClose={handleClose}>
          {sortProperties.map(({ key, label, adjustColumn }) => {
            return (
              <MenuItem
                key={key}
                onClick={() =>
                  setOrder({
                    key,
                    label,
                    adjustColumn,
                  })
                }
                selected={search.fetcher.isCurrentSort(key)}
                aria-current={search.fetcher.isCurrentSort(key)}
              >
                <ListItemText primary={menuItemLabel(key, label)} />
              </MenuItem>
            );
          })}
        </StyledMenu>
      </DropdownButton>
    </>
  );
}

export default observer(SortControls);
