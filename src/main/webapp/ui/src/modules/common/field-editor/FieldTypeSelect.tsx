import Avatar from "@mui/material/Avatar";
import ListItemAvatar from "@mui/material/ListItemAvatar";
import ListItemText from "@mui/material/ListItemText";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import MenuList from "@mui/material/MenuList";
import Paper from "@mui/material/Paper";
import { type ReactNode, useId, useState } from "react";

/** One field type a host offers; each product has its own set. */
export type FieldTypeOption<T extends string> = {
  readonly value: T;
  readonly label: string;
  readonly description?: string;
  readonly icon?: ReactNode;
};

function OptionContent<T extends string>({ option }: { option: FieldTypeOption<T> }): ReactNode {
  return (
    <>
      {option.icon && (
        <ListItemAvatar>
          <Avatar>{option.icon}</Avatar>
        </ListItemAvatar>
      )}
      <ListItemText primary={option.label} secondary={option.description} />
    </>
  );
}

type FieldTypeSelectProps<T extends string> = {
  value: T;
  options: ReadonlyArray<FieldTypeOption<T>>;
  onChange: (value: T) => void;
  /** Accessible name of the button that opens the menu, naming the current type. */
  label: string;
};

/** A button showing the current field type that opens a menu of the other types. */
export default function FieldTypeSelect<T extends string>({
  value,
  options,
  onChange,
  label,
}: FieldTypeSelectProps<T>): ReactNode {
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const menuId = useId();
  const current = options.find((option) => option.value === value);
  return (
    <>
      <Paper>
        {/* A standalone MenuItem needs a surrounding MenuList, or MUI throws "MenuListContext is missing". */}
        <MenuList disablePadding>
          <MenuItem
            onClick={(event) => setAnchorEl(event.currentTarget)}
            aria-haspopup="menu"
            aria-controls={anchorEl ? menuId : undefined}
            aria-expanded={Boolean(anchorEl)}
            aria-label={label}
            sx={{ py: 1, px: 2 }}
          >
            {current && <OptionContent option={current} />}
          </MenuItem>
        </MenuList>
      </Paper>
      <Menu id={menuId} anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={() => setAnchorEl(null)}>
        {options.map((option) => (
          <MenuItem
            key={option.value}
            selected={option.value === value}
            onClick={() => {
              setAnchorEl(null);
              if (option.value !== value) onChange(option.value);
            }}
            sx={{ py: 1, px: 2 }}
          >
            <OptionContent option={option} />
          </MenuItem>
        ))}
      </Menu>
    </>
  );
}
