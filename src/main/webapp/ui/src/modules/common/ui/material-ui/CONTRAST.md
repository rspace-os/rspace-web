# Known Storybook contrast failures

The application colors remain unchanged by decision on 2026-09-05. These
stories use the shared [RSpace theme](../../../../theme.ts), including its
component defaults. Do not substitute a different palette just to make the
examples pass.

The Chromium run on 2026-09-07 after further story refinements reported **393 passing and 82 failing
stories across 76 files**. All remaining failures reported axe's
`color-contrast` rule. These are 82 failing stories, not 86 distinct color
pairs. The results cover the rendered fixtures and states, not every possible
control combination or application screen. The overlay playground tests reopen
their overlays after checking dismissal, so the automatic scan includes the
visible overlay state.

Accessibility checks remain set to `test: "error"`; contrast checks have not
been disabled or marked as expected failures. Consequently, the full
Storybook test command currently exits nonzero. TypeScript, lint, and the
static Storybook build passed independently.

## Observed color pairs

Colors below are the computed foreground and background reported by axe;
transparency, hover states, and component defaults can change them from the
literal palette values. Ratios are rounded as reported by the test runner.

| Foreground | Background | Contrast | Example affected families |
| --- | --- | --- | --- |
| `#00adef` | `#fafafa` | 2.44:1 | Link, Tabs, Typography, Icon |
| `#00adef` | `#ffffff` | 2.55:1 | BottomNavigation, Tabs |
| `#ffffff` | `#00adef` | 2.55:1 | AppBar, Button, Badge, Toolbar |
| `#ffffff` | `#26b9f1` | 2.25:1 | Toolbar |
| `#f50057` | `#fafafa` | 4.00:1 | Link, Tabs, Typography, ButtonGroup |
| `#ffffff` | `#f50057` | 4.18:1 | AppBar, Button, Badge, FloatingActionButton |
| `#fafafa` | `#f50057` | 4.00:1 | Avatar |
| `#0f0f0f` | `#737373` | 4.04:1 | Button, Drawer, Menu, Modal, Stepper |
| `#0d3c61` | `#2196f3` | 3.66:1 | Button |
| `#9b9b9b` | `#fafafa` | 2.66:1 | FormControl helper text |
| `#fafafa` | `#bdbdbd` | 1.79:1 | Avatar, AvatarGroup |
| `#fafafa` | `#1976d2` | 4.40:1 | AvatarGroup |

The 23 affected families and their failing-story counts were: AppBar 7,
Avatar 4, AvatarGroup 5, Backdrop 3, Badge 2, BottomNavigation 3, Button 5,
ButtonGroup 7, Chip 2, Drawer 4, FloatingActionButton 1, FormControl 1,
Icon 1, Link 7, Menu 3, Modal 4, Popover 1, Snackbar 3, Stepper 3, Tabs 8,
Toolbar 5, Transitions 1, and Typography 2.

## Reproduce and maintain

Run from the repository root:

```bash
pnpm storybook:test
pnpm storybook:test Button.stories.tsx
```

The runner reports the failing story, DOM element, computed colors, and
required ratio for each violation. Use an individual story's Accessibility
panel in `pnpm storybook` to inspect it interactively.

A future theme change needs a separate review of application-wide visual
effects. Recheck text, filled buttons, links, selected navigation, avatars,
helper text, and hover/focus states. Update this baseline after rerunning the
suite; additional failing rules or stories should be investigated rather than
assumed to be part of these known failures.
