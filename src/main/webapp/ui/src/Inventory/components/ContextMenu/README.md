# Context Menu Components

## Overview

The ContextMenu system is a sophisticated UI component used throughout the
Inventory module to provide contextual actions for Inventory records. It allows
users to perform operations on either single records or multiple selected
records through a flexible interface that adapts to different rendering contexts
and screen sizes.

This system powers the action menus seen in search results, container content
views, card layouts, and picker dialogs throughout the Inventory application.

## Background & Use Cases

The context menu intelligently adapts to three main presentation modes:

- **Menu Bar**: Horizontal layout of buttons
- **Overflow Menu**: When horizontal space is limited, actions automatically
  collapse into a "more" menu with three dots icon
- **Card View Menu**: Direct dropdown menu for individual items, rendered as
  traditional menu items with icons and text

### Real-World Usage Examples

The ContextMenu appears in several key locations:

- Search results list view (i.e. the table)
- Search results card view
- The top of the right panel

And of course, the search results components are themselves rendered in various places including:

- The main Inventory search page
- Container content views
- Sample's subsamples listing
- Template's samples listing
- Template picker
- List of Materials picker
- etc.

Which is to say, that the context menu is the main mechanism that users mutate
the state of the Inventory system from across the product.

## Core Components

At a high level, this is what the main components in this directory do:
- **`ContextMenu.tsx`** - Main container that handles layout, overflow detection, responsive behavior, and ResizeObserver logic
- **`ContextMenuAction.tsx`** - Polymorphic wrapper that can render actions as either buttons or menu items based on context
- **`ContextMenuButton.tsx`** - Button implementation for when the context menu is rendered as a tool bar
- **`ContextMenuSplitButton.tsx`** - Split button component for actions with multiple variants (primary action + dropdown)
- **`ExtendedContextMenu.tsx`** - Enhanced version with pre-configured extension points for additional actions
- **`ContextActions.tsx`** - Central registry of all available actions with complex visibility and availability logic

## Component Details

### ContextMenuAction vs ContextMenuButton

The separation exists for maximum flexibility in rendering contexts:

```typescript
// ContextMenuAction can render as either format
<ContextMenuAction
  as="button"        // Renders as ContextMenuButton (chip style)
  icon={<EditIcon />}
  label="Edit"
  onClick={handleEdit}
/>

<ContextMenuAction
  as="menuitem"      // Renders as MenuItem with ListItemIcon
  icon={<EditIcon />}
  label="Edit"
  onClick={handleEdit}
/>
```

- **`ContextMenuAction`**: Logic wrapper that decides how to render based on the `as` prop (`"button"` or `"menuitem"`)
- **`ContextMenuButton`**: Visual button component that renders as a Material-UI Chip with custom styling, tooltips, and loading states

This polymorphic design allows the same action definition to work seamlessly whether it appears in the horizontal menu bar, overflow dropdown, or card context menu.

### Split Button Complexity

Split buttons handle actions that have multiple variants or sub-options, most commonly seen with selection actions:

```typescript
// Example from SelectAction.tsx
const options: Array<SplitButtonOption> = [
  { text: "Select All", selection: () => selectAll() },
  { text: "Select None", selection: () => selectNone() },
  { text: "Invert Selection", selection: () => invertSelection() }
];

<ContextMenuAction
  as={as}
  icon={<SelectAllIcon />}
  options={options}  // Makes it render as split button
/>
```

**Technical Implementation**:
- Uses `SplitButtonOption` type to define available options
- Renders as ButtonGroup with primary action + dropdown arrow
- Integrates with both button and menu item contexts seamlessly
- Handles complex click event handling and menu positioning

**Real Example from ContentContextMenu**:
Container views provide location-specific selection options like "All locations", "Siblings of selected subsample", "Mine", "Not Mine", etc.

### Extended Context Menu

Provides pre-configured extension points for scenarios where the standard context menu needs customization:

```typescript
// Example from ContentContextMenu.tsx
const prefixActions = [
  {
    key: "open",
    onClick: () => navigateToRecord(selectedResults[0]),
    icon: <OpenInBrowserIcon />,
    label: "Open",
    variant: "filled" as const,
  },
  {
    key: "select",
    options: locationSelectOptions,
    icon: <Badge badgeContent={selectedCount}><SelectAllIcon /></Badge>,
  },
];

<ExtendedContextMenu
  menuID="content"
  prefixActions={prefixActions}
  selectedResults={selectedResults}
/>
```

## Complex Action Availability Logic

### Menu Context System

The system uses `menuIDs` to determine which actions are available in different contexts:

```typescript
// From ContextActions.tsx
const hideInPickerAndWhenNotAllCurrent =
  menuID === menuIDs.PICKER || allSelectedDeleted || mixedSelectedStatus;

// Different contexts have different action sets
menuIDs.RESULTS    // Main search results
menuIDs.PICKER     // Selection dialogs
menuIDs.CONTENT    // Container contents
menuIDs.CARD       // Card view
menuIDs.STEPPER    // The top of the right panel
```

This was a mistake. It tightly couples each action to the place in the product
in which the menu is located, making it hard to utilise the context menu in new
places and making it hard to add new actions: each new use cases requires
careful thought about which actions should be available and each new action
requires careful thought about which contexts it should be available in. Much
better to have generic options that are passed into the context menu component
to control behaviour.

### State-Dependent Action Visibility

Actions have complex logic determining when they're available. We NEVER hide
context actions, we only disable them with an explanation as to why they are not
available. When the context action is rendered as a menu item this can be placed
inside of the MUI menu item but in the toolbar we rely on tooltips which is not
ideal on mobile devices.

```typescript
// Example from ContextActions.tsx showing complex availability logic
const allSelectedAvailable = selectedResults.every(r => !r.deleted);
const allSelectedDeleted = selectedResults.every(r => r.deleted);
const mixedSelectedStatus = selectedResults.some(r => r.deleted) &&
                           selectedResults.some(r => !r.deleted);

// RestoreAction only shows for deleted items
hidden: menuID === menuIDs.PICKER || allSelectedAvailable || mixedSelectedStatus

// EditAction hides in pickers and for deleted items
hidden: hideInPickerAndWhenNotAllCurrent
```

There is also logic for disabling the context menu entirely based on the state
of the rest of the page.

## Responsive Overflow System

### Dynamic Button Management

The most complex part of the system is the automatic overflow detection, which
uses ResizeObserver to detect container size changes, measures actual button
widths using refs, dynamically moves buttons between visible area and overflow
menu, and handles edge cases like all buttons fitting after resize. It is really
quite complex and fragile and as such **do not replicate this pattern**. For new
development, prefer a simple "Actions" menu instead of this pattern.

### Why This Pattern Should Be Avoided

1. **Mobile Complexity**: The overflow logic is extremely difficult to make work
   properly on mobile devices and continues to have bugs. Touch interactions
   with split buttons and dynamic overflow menus create numerous edge cases.

2. **Accessibility Nightmares**:
   - Keyboard navigation between visible buttons and overflow menu is problematic
   - Screen reader support requires complex ARIA relationships
   - Focus management during overflow state changes is fragile

3. **Complex Rendering Logic**:
   - The `ContextActions.tsx` file contains over 200 lines of complex logic just to determine which actions are visible when
   - State-dependent visibility rules make the system difficult to reason about
   - Multiple rendering paths (`button` vs `menuitem`) create inconsistent behavior

4. **Maintenance Burden**:
   - Any change to action availability requires understanding the entire context system
   - Testing requires covering all combinations of menu contexts, selection states, and permission levels
   - Performance issues from constant DOM measurements and ResizeObserver callbacks

For most new features, a simple dropdown "Actions" menu provides:
- Better mobile experience
- Clearer accessibility story
- Easier maintenance
- More predictable behavior

Examples include the Gallery, the IGSN Management Page, and the Sysadmin User Management Page.

## Working with Existing ContextMenu Code

If you're maintaining or extending existing ContextMenu code:

- **Follow Established Patterns**: Use the existing action component structure
- **Test Thoroughly**: Always test overflow behavior, mobile interactions, and accessibility
- **Be Conservative**: Avoid making changes to the core overflow logic unless absolutely necessary
- **Add New Actions Carefully**: Follow the pattern in `ContextActions.tsx` for visibility rules

The ContextMenu system serves as both a powerful tool for the Inventory module
and a cautionary tale about the complexity that can accumulate when trying to
solve too many problems in a single component system.
