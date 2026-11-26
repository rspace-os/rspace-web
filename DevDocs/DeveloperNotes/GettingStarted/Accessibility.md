# Accessibility

Design and test RSpace so people with visual, hearing, motor, or cognitive
impairments can use it. Impairments can be permanent, temporary, or situational.
For background, see [WAI accessibility fundamentals][wai-fundamentals].

## Standards

Use the [Web Content Accessibility Guidelines][wcag] as the reference for
accessible design. WCAG groups requirements under four principles: perceivable,
operable, understandable, and robust. Conformance requires meeting all applicable
success criteria at the chosen level, including the lower levels.

[WCAG 2.2 additions][wcag2.2-new] include consistently located help and avoiding
repeated entry of information. [EN 301 549][en301549] also covers requirements
beyond web content, including [accessible authoring tools][authoring-tools].
For RSpace, consider both the application and the content users create.

## Design and implementation

| # | Area | Guidance |
| --- | --- | --- |
| 1. | Colour | Never convey information through colour alone. Add text, an icon, or another indicator. |
| 2. | Contrast | Normal text needs at least 4.5:1 contrast for AA and 7:1 for AAA. Check with [Chrome DevTools][chrome-colour-contrast] or [WebAIM][webaim-colour-contrast]. Respect `prefers-contrast`; optional high-contrast styling does not replace sufficient default contrast. |
| 3. | Labels | Give every form field and control an accessible name. Use descriptive link text rather than "click here". |
| 4. | Target size | Aim for hit targets of at least 44 by 44 CSS pixels, especially for icon buttons and drag-and-drop targets. |
| 5. | Keyboard and focus | Make complete user flows work with the keyboard. Follow expected control behaviour, keep focus visible and ordered, and exclude hidden controls from navigation. |
| 6. | Images | Provide meaningful alternative text for informative images and empty alt text for decorative images. See [WAI's image guidance][wai-alt-text]. |
| 7. | Headings | Give each page an `h1` and nest headings in order. In MUI `Typography`, use `component` for the semantic level and `variant` for appearance. |
| 8. | Motion | Disable unnecessary animations and transitions when [`prefers-reduced-motion`][mdn-reduced-motion] is set. |
| 9. | Dark mode | Take colours from the theme rather than literals so they follow the light and dark schemes. Every decorated JSP page and the React pages in `DARK_MODE_PATHS` ([colorScheme.ts][colorScheme]) render dark. After changing a legacy stylesheet, run `pnpm run css:legacy-dark` to regenerate its dark overrides ([generator][legacyDarkGenerator]); fix what the generated colours get wrong in [legacy-dark.css][legacyDark]. User-authored rich text stays light, like paper. |

### Semantic HTML and ARIA

Use semantic HTML and existing controls before building custom widgets. Native
controls provide keyboard behaviour and semantics that custom controls must
implement themselves.

Use landmarks such as `main` and `nav` to identify page regions. Add ARIA where
HTML alone cannot communicate a control's name, state, or relationships. Announce
relevant dynamic updates, such as notifications or search-result counts, through
appropriately configured live regions. See the [ARIA Authoring Practices
Guide][apg] for widget patterns and [this live-region guide][live-regions] for
announcements.

ARIA adds information for assistive technologies; it does not implement keyboard
interaction or focus management. Inspect the [accessibility tree][chrome-a11y-tree]
when checking the roles and names exposed to screen readers.

## RSpace components

Prefer MUI components over custom controls. If a custom widget is necessary,
document the reason and verify its keyboard and screen-reader behaviour.

| # | Component | Use |
| --- | --- | --- |
| 1. | [Accented theme][accentedTheme] | Wrap new pages, panels, and dialogs in this theme. It adjusts accent colours when the user requests more contrast. |
| 2. | [AccessibilityTips][accessibilityTips] | Place in the app bar to explain supported accessibility settings. Keep help in a consistent location. |
| 3. | [DialogBoundary][DialogBoundary] | Use its overlay components so floating elements such as alerts remain accessible while dialogs are open. |
| 4. | `DialogTitle` | Give every dialog a title and verify that it supplies the dialog's accessible name through `aria-labelledby`. |
| 5. | [Dynamic heading levels][dynamicHeadingLevel] | Use `Heading` and `HeadingContext` to keep reusable components' headings at the correct level. Set the root context to match surrounding headings. |
| 6. | [VisuallyHiddenHeading][VisuallyHiddenHeading] | Add a heading for assistive navigation when a visible heading would be redundant. |
| 7. | [IconButtonWithTooltip][IconButtonWithTooltip] | Use for icon buttons with tooltips; it supplies the accessible label. |

## Verification

Combine automated checks with manual testing. Automated tools cannot establish
full accessibility or WCAG conformance.

Run frontend commands from the repository root. Follow the repository's
[Testing Library](../../../.agents/skills/react-testing-library/SKILL.md) and
[Browser Mode](../../../.agents/skills/rspace-browser-tests/SKILL.md) playbooks
for test structure and setup.

| # | Check | Use and limits |
| --- | --- | --- |
| 1. | `pnpm lint` | Biome's accessibility rules catch JSX issues such as missing alt text and invalid ARIA attributes. Static checks cannot assess runtime behaviour. |
| 2. | `pnpm test <file>` | Vitest/jsdom tests check rendered markup, including states after interactions. Use `toBeAccessible` through the shared [expectAccessible helper][expectAccessible]. jsdom cannot check visual contrast or browser layout. |
| 3. | `pnpm test-browser <file>` | Vitest Browser Mode runs component tests in Chromium, Firefox, and WebKit. Check keyboard behaviour and contrast in the browser. Use the shared [accessibility helpers][browser-accessibility]; media emulation helpers are Chromium-only. |
| 4. | Lighthouse | Use Chrome's [Lighthouse audit][lighthouse] on full pages. Audit relevant interaction states as well as initial load. |
| 5. | Manual checks | Complete flows using only the keyboard, check focus and screen-reader announcements, and test contrast and reduced-motion preferences. Include disabled users in evaluation where possible. |

For a browser contrast-test example, see
[ValidatingSubmitButton.spec.tsx][contrast-example]. Check both default and
high-contrast states. Component checks do not replace full-page checks of
landmarks, heading structure, dialogs, and complete user flows.

[wai-fundamentals]: https://www.w3.org/WAI/fundamentals/
[wcag]: https://www.w3.org/TR/WCAG22/
[wcag2.2-new]: https://www.w3.org/TR/WCAG22/#new-features-in-wcag-2-2
[en301549]: https://www.deque.com/blog/301549-improve-accessibility/
[authoring-tools]: https://hidde.blog/authoring-tools-in-en-301-549/
[chrome-colour-contrast]: https://developer.chrome.com/docs/devtools/accessibility/contrast/
[webaim-colour-contrast]: https://webaim.org/resources/contrastchecker/
[wai-alt-text]: https://www.w3.org/WAI/tips/developing/#include-alternative-text-for-images
[mdn-reduced-motion]: https://developer.mozilla.org/en-US/docs/Web/CSS/@media/prefers-reduced-motion
[apg]: https://www.w3.org/WAI/ARIA/apg/
[live-regions]: https://www.sarasoueidan.com/blog/accessible-notifications-with-aria-live-regions-part-1
[chrome-a11y-tree]: https://developer.chrome.com/blog/full-accessibility-tree/
[lighthouse]: https://developer.chrome.com/docs/lighthouse/
[accentedTheme]: ../../../src/main/webapp/ui/src/accentedTheme.ts
[colorScheme]: ../../../src/main/webapp/ui/src/colorScheme.ts
[legacyDarkGenerator]: ../../../scripts/generate-legacy-dark-css.mjs
[legacyDark]: ../../../src/main/webapp/styles/dark/legacy-dark.css
[accessibilityTips]: ../../../src/main/webapp/ui/src/components/AccessibilityTips.tsx
[DialogBoundary]: ../../../src/main/webapp/ui/src/components/DialogBoundary.tsx
[dynamicHeadingLevel]: ../../../src/main/webapp/ui/src/components/DynamicHeadingLevel.tsx
[VisuallyHiddenHeading]: ../../../src/main/webapp/ui/src/components/VisuallyHiddenHeading.tsx
[IconButtonWithTooltip]: ../../../src/main/webapp/ui/src/components/IconButtonWithTooltip.tsx
[expectAccessible]: ../../../src/main/webapp/ui/src/__tests__/accessibility.ts
[browser-accessibility]: ../../../src/main/webapp/ui/src/__tests__/pageObjects/accessibility.ts
[contrast-example]: ../../../src/main/webapp/ui/src/components/ValidatingSubmitButton.spec.tsx
