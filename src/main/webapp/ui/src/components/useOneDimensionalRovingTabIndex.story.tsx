import React from "react";
import useOneDimensionalRovingTabIndex from "./useOneDimensionalRovingTabIndex";
import ListItemText from "@mui/material/ListItemText";
import ListItemButton from "@mui/material/ListItemButton";
import ListItem from "@mui/material/ListItem";
import List from "@mui/material/List";
import Button from "@mui/material/Button";

/**
 * A basic example of how to use useOneDimensionalRovingTabIndex
 */
export function SimpleExample(): React.ReactNode {
  const { getTabIndex, getRef, eventHandlers } =
    useOneDimensionalRovingTabIndex<HTMLDivElement>({ max: 1 });
  return (
    <>
      <Button>Before the list</Button>
      <List {...eventHandlers}>
        <ListItem>
          <ListItemButton
            tabIndex={getTabIndex(0)}
            ref={getRef(0)}
            onClick={() => {}}
          >
            <ListItemText primary="One Thing" />
          </ListItemButton>
        </ListItem>
        <ListItem>
          <ListItemButton
            tabIndex={getTabIndex(1)}
            ref={getRef(1)}
            onClick={() => {}}
          >
            <ListItemText primary="Two Thing" />
          </ListItemButton>
        </ListItem>
      </List>
      <Button>After the list</Button>
    </>
  );
}
