# Quirks of Material UI

This is some documentation on some quirks of the Material UI component library
and how we use it.



## Custom components that wrap `MenuItem`s

It is often handy to populate the `Menu` with `MenuItem`s wrapped by custom
components which encapsulate the behaviour of tapping that `MenuItem`, such as
opening dialog. For example,

```
const WrappedMenuItem = () => {
  const [open, setOpen] = React.useState(false);
  // some state specific to the dialog

  return (
    <>
      <MenuItem
        onClick={() => {
          setOpen(true);
        }}
      >
        Example Action
      </MenuItem>
      <Dialog open={open}>
        {/* some dialog */}
      </Dialog>
    </>
  );
};

const ExampleMenu = () => (
  <Menu {...someProps} >
    <WrapperMenuItem />
  </Menu>
);
```

However, this will lack keyboard controls. [The WAI specifies what the correct
keyboard controls for a menu are](WAI-menu): 
  * The arrow keys should change the focussed menu item
  * Enter should activate the focssed menu item
  * Escape should close the menu.
As such, without modification this menu would fall foul of the WCAG AA
Accessibility standard.

If we add the `autoFocus` to the first `MenuItem` this mostly resolves the
issue. The correct keyboard controls, as implemented by MUI, correctly adhere
to the WAI specification. The only exception being that if the menu it opened
with the mouse click, and then it is followed by the downward arrow tap, the
second `MenuItem` becomes focussed rather than the first. Eslint then has to be
suppressed as the `[Error/jsx-a11y/no-autofocus]` will (rightfully) complain
that this is a potentially accessibility issue; however what eslint cannot
determine is that the auto focussing is the expected behaviour.

Ideally, MUI would provide a mechanism to allow for a nested `MenuItem` to be
identifiered programmatically rather than assuming they will always be the
immediate child component of the `Menu`.

For more information on this issue see:
  * [MUI GitHub Issues :: Several menu items inside a component are not accessible](SO-menuitem)


[WAI-menu]: https://www.w3.org/WAI/ARIA/apg/patterns/menubar/
[SO-menuitem]: https://github.com/mui/material-ui/issues/41305



## Dialogs inside Menus

This potential issue is similar to one above, in that it occurs when complex UI
logic is implemented inside of a `Menu` and related to keyboard controls, but
is slightly different.

Long menus can be efficiently navigating by tapping the letter key that
corresponds to the first letter of the menu item; e.g. for scrolling down a
list of countries. However, if there is a `TextField` inside of a `Dialog` that
is inside of a `Menu` then any letters that occur as the first character in any
of the `MenuItems` will not be enterable into the `TextField` as the event will
be captured by the `Menu` instead.

The solution is wrap the `Dialog` in a component as defined below that prevents
the key events, and others, from propagating up to the `Menu`.

```
const EventBoundary = ({ children }: { children: Node }) => (
  // eslint-disable-next-line
  <div
    onKeyDown={(e) => {
      e.stopPropagation();
    }}
    onMouseDown={(e) => {
      e.stopPropagation();
    }}
    onClick={(e) => {
      e.stopPropagation();
    }}
  >
    {children}
  </div>
);
```

The eslint suppression is required because `div`s should not ordinarilly have
event handlers as they cannot be focussed. However, in this case, we are just
using the event listeners on the `div` to prevent the events from further down
the DOM from propagating up; the user need not interact with the `div` itself
for this to work.
