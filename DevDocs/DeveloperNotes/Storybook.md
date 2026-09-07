# Storybook

RSpace uses Storybook as a browser-based catalogue for React component in our
design system. It lets developers inspect states and prop combinations in isolation,
document component behaviour, and run interaction and accessibility checks in 
real browser.

## Start Storybook

Run these commands from the repository root:

```bash
pnpm storybook             # development server at http://localhost:6006
pnpm storybook:build      # write the static catalogue to storybook-static
pnpm storybook:test        # run Storybook stories in Chromium
pnpm storybook:test Button.stories.tsx  # focus on matching stories
```

The root scripts change into `src/main/webapp/ui`, where Storybook's Vite
configuration and module aliases are defined. Storybook discovers stories
under the frontend source tree, including files named `*.stories.tsx` and
`*.compound.stories.tsx`.

## How the catalogue is organised

The Material UI catalogue is under
`src/modules/common/ui/material-ui/components`. Each component family normally
has a story file with a Docs page and one or more named examples. Compound
examples use `*.compound.stories.tsx` and demonstrate how related MUI exports
work together. The stories use the application's RSpace theme and the MUI
packages installed by the application; they do not define a parallel wrapper
API. See the [upstream attribution and catalogue notes](../../src/main/webapp/ui/src/modules/common/ui/material-ui/UPSTREAM.md)
for the source of the adapted MUI examples.

## Writing a story

Use Storybook's React/Vite types and keep the story close to the component it
documents:

```tsx
import type { Meta, StoryObj } from "@storybook/react-vite";
import { Button } from "@mui/material";

const meta = {
  component: Button,
  title: "Components/Button",
  args: { children: "Save" },
} satisfies Meta<typeof Button>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};
export const Disabled: Story = { args: { disabled: true } };
```

Prefer stories that expose meaningful states through `args`, so reviewers can
use Controls to vary props. For stateful controls such as menus, dialogs, and
snackbars, keep transient state in the story or synchronize it with Storybook
args. Use `play` functions for user-visible behaviour and import interaction
helpers from `storybook/test` (`userEvent`, `within`, `expect`, and `waitFor`).
Use accessible names and relationships in every interactive example. Portal
content should be asserted while it is open; the catalogue's Docs examples
render in isolated frames to avoid collisions between examples.

Do not add network calls or application-specific authentication to a story.
Use a stable fixture and render the component at the boundary where its props
are supplied. Add a short source comment when adapting an external example and
record its exact revision in the relevant attribution file.

## Verification

After changing stories, run `pnpm tsc` and `pnpm lint`. Run a focused
`pnpm storybook:test` command while iterating, then the full Storybook test
command before review. Run `pnpm storybook:build` when changing Storybook
configuration or documentation metadata. Browser checks cover the rendered
states and declared interactions; review new keyboard, focus, responsive, and
visual states manually as well. Known Material UI contrast failures and their
reproduction commands are documented in
[`CONTRAST.md`](../../src/main/webapp/ui/src/modules/common/ui/material-ui/CONTRAST.md).

## Reviewing a built catalogue in RSpace

To preview the static catalogue in a local RSpace development server:

1. Run `pnpm run storybook:build`.
2. Set `dev.storybook.preview.enabled=true` in `deployment.properties`.
3. Open `/public/storybook` on that instance.

The route prefers the Storybook bundle packaged at `WEB-INF/storybook` and
falls back to the local, gitignored `storybook-static` directory. A WAR build
includes the bundle only when Storybook generation is enabled:

```bash
mvn clean package -DgenerateReactDist -DgenerateStorybook=true -DskipTests=true
```

Jenkins exposes this as the **STORYBOOK** option. The preview route is disabled
unless the property is enabled, and should remain disabled on production
instances.
