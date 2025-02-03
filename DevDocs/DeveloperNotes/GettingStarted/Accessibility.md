# Accessibility

Accessibility, often shortened as a11y, is about designing software systems to
be equally usable and feature-rich for all users, regardless of any
disabilities or impairments. There are two orthogonal axis on which to consider
relevant disabilities and impairments: kind and time period.

There are four different kinds of disabilities and impairments that are
relevant to the design of software:
<dl>
  <dt>Visual impairments</dt>
  <dd>
    This includes everyone from those that are completely blind, to those that
    have reduced vision, to those that are simply colour blind. This is perhaps
    the most relevant to our work.
  </dd>
  <dt>Hearing impairments</dt>
  <dd>
    People who are deaf or hard-of-hearing. This is less relevant to our work
    as we're not displaying audio-visual content such as videos where
    transcript ought to be provided.
  </dd>
  <dt>Mobility impairments</dt>
  <dd>
    This is a diverse group of people that include those with a variety of
    conditions from physical disabilities with their limbs, to neurological
    conditions that makes precise movement difficult. They often use input
    devices tailored for the way that they interact with the computer.
  </dd>
  <dt>Cognitive impairments</dt>
  <dd>
    This is a broad category of disabilities that includes those with
    intellectual disabilities, but also simply older folk who are in the stages
    of mental decline. It is this later category that also makes this relevant
    to our work.
  </dd>
</dl>

[For more information on the different kinds of disabilities and impairments, see this MDN doc][mdn-what-is-accessibility].

Impairments to using software don't have to be permanent; there are situations
where we all benefit from adjustments and design considerations that ensure
equal access. This temporal axis to impairments can be broken down into three
categories:

<dl>
  <dt>Permanent</dt>
  <dd>
    This includes permanent disabilities such as being deaf or blind.
  </dd>
  <dt>Temporary</dt>
  <dd>
    Temporary impairments include injuries and illnesses such as arm injuries
    or migraines.
  </dd>
  <dt>Situational</dt>
  <dd>
    Situational impairments are caused by environment, such as being in a noisy
    place or being distracted from the task as hand.
  </dd>
</dl>

The information here is taken from Microsoft Inclusive Design Toolkit.
[For a full matrix of examples, see this graphic][Microsoft-inclusive-design-toolkit-graphic].

For more information on accessibility fundamentals, see
[this article by the Web Accessibility Initiative][wai-fundamentals].

## Web Content Accessibility Guidelines

The Web Content Accessibility Guidelines (WCAG) is a collection of standards
established by the Web Accessibility Initiative (WAI) of the World Wide Web
Consortium. They specify how to design Web-based software in a way that is
accessible, from the high-level concepts to the specifics of HTML tags. WCAG
version 2.1 consists of twelve guidelines grouped under four principles:

<dl>
  <dt>Perceivable</dt>
  <dd>
    Users must be able to perceive the system using one more of their senses
    i.e. they should be able to see a button, or a screen reader should
    describe it to them.
  </dd>
  <dt>Operable</dt>
  <dd>
    Users must be able to control the UI elements: keyboard, mouse, voice
    command, etc
  </dd>
  <dt>Understandable</dt>
  <dd>
    The content must be understandable to all users.
  </dd>
  <dt>Robust</dt>
  <dd>
    The system must be designed to work reliably across difference browsers.
  <dt>
</dl>

Each guideline is broken down into a number of success criteria. Each success
criteria is categorised by the level of impact that not meeting the criteria
has on the accessibility of the system.

<dl>
  <dt>A</dt>
  <dd>
    Web developers <strong>MUST</strong> satisfy this requirement, otherwise the system will
    be impossible to use for some groups.
  </dd>
  <dt>AA</dt>
  <dd>
    Web developers <strong>SHOULD</strong> satisfy this requirement, otherwise it will be
    difficult to use for some groups.
  </dd>
  <dt>AAA</dt>
  <dd>
    Web developers <strong>MAY</strong> satisfy this requirement to make it easier for some
    groups to use.
  </dd>
</dl>

For a site to be considered accessible to a particular standard, it must meet
**ALL** of the success criteria whose level of impact is so categorised.

For more information on WCAG, see [this MDN doc][mdn-understanding-wcag] and
[WCAG 2.1 document][wcag-2.1]


### WCAG 2.2

Do note that WCAG 2.2 has [some additional success criteria][wcag2.2-new] that apply to RSpace including that

* [Help should be consistently located across the user interface][consistent-help].

* [That the user should not be required to enter the same information multiple times][redundant-entry].


## EN 301 549


Whilst WCAG is the current industry standards, there are some efforts to
standardise on an even stricter standard. [EN 301 549 includes all of the
requirements of WCAG 2.1][deque-en301549] with some notable additions:

  * User Preferences, such as a high contrast mode or reduced motion must be
    respected.

  * [Authoring tools must guide the user towards creating accessible content.][hidde-authoring-tools]



## The current state of RSpace

So with all that in mind, how accessible is the RSpace application? In two
words, not very. There are some simple ways in which the design of the system
has taken the needs of those with impairments into consideration, such as
colour blindness, but by-and-large RSpace does not come close to meeting the
WCAG guidelines at any level.

This is an issue for two reasons. Primarily, as a moral imperative we, as
developers of a software system, have a duty to ensure that all of our users
have equal access and that we're not implicitly discriminating against anyone
due to how we have designed and built the system. Secondly, there is an
increasing expectation that software is accessible and it may become a risk to
the viability of the business were our customers to find themselves under a
regulatory obligation to only purchase software that meets the accessibility
standard described above. Patching up a system that has not been designed from
the beginning to be accessible is in the best case very difficult, and when
coupled with the size of the RSpace system compared with the size of our team
it would be essentially impossible to make the system compliant within a
reasonable time frame. As such, this is something we simply MUST do more about.


## So what should we be doing?


### Design

Ensuring a site is accessible is mostly about design given it is inherently
about the user interface. With that in mind, it then becomes clear the
retrofitting a bad design to be accessible is always going to be a flawed
endeavour as design mistakes are always difficult to fix once they have become
embedded across a system.

#### Colour Blindness

Quite simply, colour alone should never be used to convey information. There
should always be an icon, a label, or some other indicator in addition to
whatever it is that the colour is conveying. The Rendering tab in the Chrome
dev tools has a "Emulate vision deficiencies" option for checking that the UI
remains usable even if the user has one of the variety of different kinds of
colour blindness. For more information on the [Chrome dev tool's emulation of
vision deficiencies, see their docs][chrome-emulation]. Finally, here's an
[interesting article from The Verge about colour blindness in software design][the-verge-colour-blindness].

#### Colour Contrast

In a similar vein, all text should be of a colour that ensures there is
sufficient contrast against the background. For AA WCAG standard, general text
should have a contrast of 4.5:1, with a ratio of 7:1 for the AAA standard.
The Chrome dev tools have a useful tool for checking contrast in the
colour picker; [see this article][chrome-colour-contrast]. Alternatively,
[WebAIM have a tool for checking colour contrast][webaim-colour-contrast].
For more information on colour contrast generally, see
[the MDN docs on colour contrast][mdn-colour-contrast] and
[this video by the Chrome Developers YouTube channel][a11ycast-colour-contrast].

If there are places where providing sufficient contrast to all users would
meaningfully diminish their experience, browsers can be configured to indicate
to websites that the user has a preference for more or less contrast via a
media query. As such, the highest amount of contrast can be provided to just
such users.
[For more information on this media query, see this MDN doc][mdn-prefers-contrast].

#### Every field should have a label

A simple and quite obvious thing, but we're not always perfect. For more info,
see [the WAI tips page][wai-every-field-label].

#### Make sure hit targets are big enough

Users who have motor deficiencies may struggle with small hit targets, and the
rule of thumb is that at a minimum hit boxes should be 44x44 css pixels. Places
to watch out are icon buttons, border-less textual buttons, and drag-and-drop
targets. Again, [more info from the WAI][wai-hit-targets].

#### Keyboard use

In addition to thinking about how a design could be used with mouse or
touchscreen, we ought to also be considering keyboard only. This doesn't just
mean adding keyboard shortcuts to make a few common actions easier, but rather
considering the whole application and all of the user flows within it when
using the keyboard alone. Common reasons for this would be because the user has
RSI and so chooses to use various different input device to reduce the over-use
of any one muscle group. Primarily, this means ensuring that UI elements follow
the standard behaviour expectations for their role (tapping space on a button,
arrowing though a select menu, etc).

##### Focus

A big part of being accessible to heavy keyboard users to for the focus state
to be consciously considered. For example, tabbing through interactive elements
should focus them in a sensible order and elements that are not visible should
not be reachable. For more information on how that can be achieved, see
[this a11ycast video on the roving tab index][a11ycast-roving-tab] and
[this MDN doc which has a section titled "Focus order makes sense"][mdn-focus-order].
Furthermore, it is key that the focus indicator is visually obvious (many Web
designers have a bad habit of hiding it because they consider it ugly). There's
also a
[MDN doc with a section titled "Focused elements should be visibly focused"][mdn-show-highlight].

#### Meaningful link text

The text placed inside of anchor tags, ought to clearly and concisely
communicate where the user will be navigated to. "Click here" is generally
considered poor form. See
[this MDN doc, where there is a section titled "Link text conveys meaning"][mdn-meaningful-link-text].


### Coding

There are some considerations that ought to be made that are specific to the
code though. These are primarily cases where the Web page ought to be conveying
additional information that may not be included in the visual design of the
site, but are consumed by other software.


#### Alt-text

Users with poor or no eye-sight rely on images to have alt-text to describe
what is in an image. This is not a particularly high priority for us, given the
demographic make up of our user base, and is complicated substantially by the
fact that the vast majority of the images in our system are user generated.
Nonetheless, there are websites &mdash;most notably mastodon&mdash; that have nurtured
a culture of providing descriptive text when uploading images and this is
something we may need to consider to be fully spec compliant. The
[WAI have some documentation on alt-text][wai-alt-text].


#### Use headings semantically

The difference between a `h1` and a `h2` isn't just size, but that the text
within a `h2` is a subheading to content that has heading indicated with a
`h1`. This hierarchy is important and SHOULD NOT be discarded because
developers are too lazy to adjust the styling. The
[MUI Typography component][mui-typography] has separate props for `component`
and `variant` specifically for this reason, where `component` specifies the
HTML tag to be used and `variant` is the style defined in terms of their
default heading styles. So this would be a small top level heading
```
<Typography component="h1" variant="h5">Small heading</Typography>
```
and this would be a big sub-subheading
```
<Typography component="h3" variant="h2">Big sub-subheading</Typography>
```
It would be unusual to have bigger subheadings than headings on the same page,
but one page might use variants h2, h4, and h5 for components h1, h2, and h3
respectively whilst another might use variants h1, h3, and h4 for components
h1, h2, and h3, respectively. Why MUI couldn't come up with new names for their
styles I don't know. The key thing is that every page has a h1 and all
subheadings descend in order, without skipping any levels, from there. For more
information on
[why semantic headings are important, see this comprehensive guide on headings by the a11yproject][a11yproject-headings].

To avoid skipping any levels, it might be necessary to [add invisible headings](#invisible-headings).


#### Reduce motion

Some people with vestibular disorders find animations and transitions
disorientating. For that reason, CSS provides a media query to detect if the
browser is requesting that the website reduce motion. Whenever we have an
animation or transition, we should be disabling it if this media query is set.
[For more information on vesitibular disorders see this a11y project article][a11yproject-vestibular-disorders]
and
[for more information on the reduced motion media query, see this MDN doc][mdn-reduced-motion].

#### WAI-ARIA

The WAI's Accessible Rich Internet Applications (ARIA) standard is a
specification for adding semantic information to HTML markup so that the
technologies originally designed for simple documents provide enough
information of accessibility technologies &mdash;like input and output
devices&mdash; to make rich Web applications accessible to all. There are three
parts to the standard:

<dl>
  <dt>Roles</dt>
  <dd>
    These describe what an element is or does. Semantic HTML elements like
    <code>&lt;nav&gt;</code> and <code>&lt;aside&gt;</code> have built-in roles
    but other semantic-less elements like <code>&lt;div&gt;</code> and
    <code>&lt;span&gt;</code> do not. Roles can be set with the
    <code>role</code> HTML attribute. In addition to aiding with accessibility,
    good roles also aid with testing as they semantic describe the elements
    that should be selected.
  </dd>
  <dt>Properties</dt>
  <dd>
    These are HTML attributes for adding extra meaning to an element. They are
    prefixed with <code>aria-</code> and include things like
    <code>aria-label</code> and <code>aria-required</code>.
  </dd>
  <dt>States</dt>
  <dd>
    These are HTML attributes, again prefixed with <code>aria-</code>, that
    signify the current state of an element e.g. <code>aria-disabled</code>.
  </dd>
</dl>

None of this affects how the page appears or functions; it's all just about
providing extra information to other software like screen readers or the
firmware for hardware devices. Testing tools also count as "other software" so
providing this semantic information also makes Web applications easier to test.

To make all of this easier for developers, the WAI has an [Authoring Practices
Guide (APG)][apg]. The best place to start with all this stuff is their [Read
Me First article][read-me-first]. For more information on ARIA generally,
[MDN has an article on the basics][mdn-wai-aria-basics].

Also do note that [each HTML tag has a set of valid roles][w3-docconformance]
and
[each type of content should be contained in an element with a specific role][w3-allowed-descendants-of-aria-roles].


As a general rule of thumb, always ensure that the correct roles are being used
and double-check the right aria- attributes are being used when the content
matches on of these categories:

<dl>
  <dt>Signposts & Landmarks</dt>
  <dd>
      A bunch of HTML elements and roles are designed to describe the structure
      of the web application. The main tag is the <code>&lt;nav&gt;</code> tag,
      but there's also <code>&lt;main&gt;</code>, <code>&lt;aside&gt;</code>,
      <code>&lt;header&gt;</code>, <code>&lt;footer&gt;</code>, and others.
      There are also some attributes that are used for describing layout like
      <code>&lt;input&gt;</code>'s <code>type="search"</code> that marks the
      textbox as a search box. Essentially just make sure that sections,
      headings, sidebars, tabs, etc are correctly annotated so that
      accessibility tooling can efficiently parse the layout to the page.
  </dd>
  <dt>Dynamically Updated Content</dt>
  <dd>
      Parts of the page that change dynamically can be especially difficult for
      accessibility tooling (and all software that reads webpages like crawlers
      for that matter). It is important that such regions are annotated with
      <code>aria-live</code> with the appropriate value. These include obvious
      things like notifications and popup dialogs, but also anything that is
      generated from a network request like search results or table sorting.
      [Sara Soueidan has a comprehensive guide on live regions.][sara-live-regions].
  </dd>
  <dt>Non-semantic Controls</dt>
  <dd>
      The built in controls &mdash;<code>&lt;input&gt;</code>,
      <code>&lt;textarea&gt;</code>, etc.&mdash; come with a bunch of
      functionality that has to be manually added in by the developer when
      custom controls are built. These include keyboard controls like arrowing
      throwing options and tabbing through fields, but also semantic
      information about the control like min and max values. Getting this stuff
      right is hard, but the Authoring Practices Guides are especially helpful.
  </dd>
</dl>

For a list complete list of aria- attributes, see either
[the MDN doc on aria attributes][mdn-attributes]
or the
[Definitions of States and Properties section of the WAI-ARIA 1.1 standard][aria-states-and-properties].

One final concept to be aware of is the accessibility tree. All of this semantic
information is bundled into a data structure that mimics the DOM, but contains
more high level information that is available to accessibility technologies.
[Chrome has various ways of visualising this tree.][chrome-a11y-tree].
When testing, it is this tree that is being traversed when calls to `findByRole`
are composed.


## Verifying

Verifying that a site is accessible in an efficient way requires a
multi-faceted approach. Manually testing everything is ideal, but requires a
lot of knowledge about what to look for and is time consuming. The gold standard
is to work with people who have various different disabilities, but is
obviously difficult for various reasons.

To make the process of verifying that we're building the right thing easy,
there's a bunch of tools that we have in our stack. They're listed here in
order from easiest to use to the requiring the most work.


### Eslint

The [jsx-a11y eslint plugin][eslint-plugin-jsx-a11y] can catch a variety of
issues with HTML written using JSX notation.

#### Pros

* It requires to no work to use, it just runs as part of our existing eslint
  workflow.
* It can catch issues like missing alt attributes and invalid aria- attributes

#### Cons

* The build does not fail on issues, so error could be introduced without us
  noticing.
* Static analysis of code can never catch all issues, as some can arise when
  the code is executed.


### Jest

With the aid of [jest-axe][], jest tests can be written that assert that the
DOM does not have an accessibility violations.
```
/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import { axe, toHaveNoViolations } from "jest-axe";

expect.extend(toHaveNoViolations);

describe("some tests", () => {
  test("some test", async () => {

    const { container } = render(
      <ComponentUnderTest />
    );

    // $FlowExpectedError[incompatible-call] See expect.extend above
    expect(await axe(container)).toHaveNoViolations();
  });
});
```

#### Pros

* Pretty easy to write and run; just follow template above.
* Will fail the build if a violation is introduced.
* Can assert there are no violations after some code execution, such as after a
  user interaction.

#### Cons

* Doesn't render anything, so can't assert behaviour like keyboard controls or
  colour contrast.


### Cypress

Cypress runs the tests in an actual browser so can assert even more dynamic
behaviour.

#### Pros

* Renders code, so can assert behaviour like keyboard controls.
* Is also run by jenkins so we'll be alerted to any breakage.

#### Cons

* Requires quite some time to write and maintain.
* Takes some time to run, and the feedback cycle is quite long.


### Lighthouse

Built into Chrome is a tool for audit webpages, with an option for looking for
accessibility issues. For more information on how to use, see
[the Chrome developer docs on Lighthouse][lighthouse].

#### Pros

* Requires no effort, just run it
* Runs in browser so can check things like colour contrast

#### Cons

* Only looks at page load, so can't check other states of the system
* Can't be run automatically


### Manual audit

As mentioned, performing a manual audit can take quite some time and effort,
but it may be the best approach in some circumstances. In
[this episode of a11ycast][a11ycast-audit], Rob Dobson goes through the steps
he goes through to manually evaluate the accessibility of a website.

#### Pros

* Some human judgement is just required with this kind of thing.
  [Automated testing tools just can't catch everything][wai-cannot].

#### Cons

* Requires a good amount of knowledge about what to check and what is correct
* Is time consuming to do thoroughly


Ultimately a bit of judgement is need depending on the complexity of the
website/component at hand, but as a rule of thumb the levels here should mirror
the testing pyramid.


## Specifics to our codebase

Everything above is pretty generic to any project, but there are some things
worth documenting that are specific to our codebase.


### MUI over custom controls

Use MUI controls and other components wherever possible as they generally have
good defaults and they've put a good amount of work into ensuring that all of
the keyboard controls are correct. Any deviation from those components where we
have to roll our own should probably be heavily documented why and would
probably warrant higher levels of verification e.g. manual auditing, to ensure
that they're correct.


### Accented Theme

Having said that, all new pages, panels, and dialogs should be wrapped by an
instance of the [Accented Theme][accentedTheme], which re-styles the colours of
a lot of the MUI components. It is paramaterised by an accented colour, with a
number of variations, so that the page (or section thereof) can be styled
with distinctive branding. This is intended to reinforce the sense that the
functionality is linking together different systems; either where we're
integrating with third-party services, and thus are mimicking their branding, or
where different parts of the RSpace product are linking together to form a
greater whole.

What this has to do with accessibility is that the colours have been set up to
dynamically adjust to provide a high-contrast mode when the user's device
requests it. Through the "prefers-contrast" media query the user's device can
instruct the user agent, and thus the webpage to adapt the page to the user's
visual needs. When more contrast is requested, we strip out this accent colour
which is not strictly necessary for the operation of the functionality, and
provide a cleaner UI. This same method can also be used to provide a reduced
motion adjustment to any animations.


### Appbar with accessibility tips

To accompany this high contrast mode, we have an
[Accessibility Tips][accessibilityTips] component that is designed to sit in
the top right corner of an app bar. It informs the user of the configuration
options that the page/dialog supports as without it it is not apparent that
this capability is provided. The app bar also provides a consistent place to access the help functionality, a key part of basic compliance with WCAG 2.2.


### DialogBoundary

We sometimes wish to display elements floating above open dialogs, such as alert
toasts and floating action buttons. To ensure that these elements are reachable
by accessibility technologies, use [DialogBoundary][] to prevent MUI from making
these floating elements inert.


### Make sure every Dialog has a DialogTitle


If I Dialog has a DialogTitle then Material UI will automatically wire up the
latter to the former with an `aria-labelledby` attribute. Every DOM node with
role "dialog" must have a label and this is the simplest way to achieve that
given that sighted user will need a title too.


### Automatically adjusted heading levels

It is important that heading levels are rendered in order without skipping to
different levels, however components are often reused at different places in
the UI within different heading scopes. For this reason, the
[Dynamic Heading Level][dynamicHeadingLevel] functionality exists: components
can render a heading without specifying its level, with the level of headings
simply being one level deeper than the headings above it. At the root sits a
heading context so this whole setup can be added to pages that are not using
the mechanism for all of their headings.


### Invisible Headings

There are times when a particular heading is not needed for sighted users as
it is clear from context, especially when it comes to web apps rather than
websites, but users using accessibility technologies will still require those
headings to aid with navigation. For this purpose, the [VisuallyHiddenHeading][]
component provides such a mechanism.


### Use IconButtonWithTooltip

Just a simple one: use the [IconButtonWithTooltip][] component rather than an
IconButton wrapped in a CustomTooltip as the former does the aria-label
automatically.


### Asserting contrast of theme colours

Write tests asserting [relativeLuminosity][] of colours defined in the theme,
and then just refer to those colours. This way, we can move the contrast
checking into an automated step of verification.


## More links

Here are just an assortment of more helpful links
* [a11ycasts, a YouTube series by Google Developers](https://www.youtube.com/watch?v=HtTyRajRuyY&list=PLNYkxOF6rcICWx0C9LVWWVqvHlYJyqw7g)
* [a11yproject.com/](https://www.a11yproject.com/)
* [hidde.blog/common-a11y-issues/](https://hidde.blog/common-a11y-issues/)
* [sarasoueidan.com/blog/](https://www.sarasoueidan.com/blog/)
* [accessibility.blog.gov.uk](https://accessibility.blog.gov.uk)
* [webaim.org](https://www.webaim.org)




[mdn-what-is-accessibility]: https://developer.mozilla.org/en-US/docs/Learn/Accessibility/What_is_accessibility
[Microsoft-inclusive-design-toolkit-graphic]: https://www.bitovi.com/hubfs/1.%20How%20to%20be%20an%20A11y-1.png
[wai-fundamentals]: https://www.w3.org/WAI/fundamentals/
[mdn-understanding-wcag]: https://developer.mozilla.org/en-US/docs/Web/Accessibility/Understanding_WCAG
[wcag-2.1]: https://www.w3.org/TR/WCAG21/
[chrome-emulation]: https://developer.chrome.com/docs/devtools/rendering/apply-effects/#emulate-vision-deficiencies
[the-verge-colour-blindness]: https://www.theverge.com/23650428/colorblindness-design-ui-accessibility-wordle
[chrome-colour-contrast]: https://developer.chrome.com/docs/devtools/accessibility/contrast/
[mdn-colour-contrast]: https://developer.mozilla.org/en-US/docs/Web/Accessibility/Understanding_WCAG/Perceivable/Color_contrast
[webaim-colour-contrast]: https://webaim.org/resources/contrastchecker/
[a11ycast-colour-contrast]: https://www.youtube.com/watch?v=LBmLspdAtxM
[read-me-first]: https://www.w3.org/WAI/ARIA/apg/practices/read-me-first/
[apg]: https://www.w3.org/WAI/ARIA/apg/
[mdn-wai-aria-basics]: https://developer.mozilla.org/en-US/docs/Learn/Accessibility/WAI-ARIA_basics
[wai-every-field-label]: https://www.w3.org/WAI/tips/developing/#associate-a-label-with-every-form-control
[wai-hit-targets]: https://www.w3.org/WAI/WCAG21/Understanding/target-size.html
[a11ycast-roving-tab]: https://www.youtube.com/watch?v=uCIC2LNt0bk
[mdn-focus-order]: https://developer.mozilla.org/en-US/docs/Web/Accessibility/Cognitive_accessibility#focus_order_makes_sense
[mdn-show-highlight]: https://developer.mozilla.org/en-US/docs/Web/Accessibility/Cognitive_accessibility#focused_elements_should_be_visibly_focused
[wai-alt-text]: https://www.w3.org/WAI/tips/developing/#include-alternative-text-for-images
[mdn-meaningful-link-text]: https://developer.mozilla.org/en-US/docs/Web/Accessibility/Cognitive_accessibility#link_text_conveys_meaning
[a11yproject-headings]: https://www.a11yproject.com/posts/how-to-accessible-heading-structure
[mui-typography]: https://mui.com/material-ui/react-typography/
[w3-docconformance]: https://www.w3.org/TR/html-aria/#docconformance
[w3-allowed-descendants-of-aria-roles]: https://www.w3.org/TR/html-aria/#allowed-descendants-of-aria-roles
[mdn-attributes]: https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Attributes
[aria-states-and-properties]: https://www.w3.org/TR/wai-aria-1.1/#state_prop_def
[eslint-plugin-jsx-a11y]: https://www.npmjs.com/package/eslint-plugin-jsx-a11y
[jest-axe]: https://www.npmjs.com/package/jest-axe
[lighthouse]: https://developer.chrome.com/docs/lighthouse/
[a11ycast-audit]: https://www.youtube.com/watch?v=cOmehxAU_4s
[wai-cannot]: https://www.w3.org/WAI/test-evaluate/tools/selecting/#cannot
[IconButtonWithTooltip]: /src/main/webapp/ui/src/components/IconButtonWithTooltip.js
[relativeLuminosity]: /src/main/webapp/ui/src/__tests__/theme/palette/record.test.js
[a11yproject-vestibular-disorders]: https://www.a11yproject.com/posts/understanding-vestibular-disorders/
[mdn-reduced-motion]: https://developer.mozilla.org/en-US/docs/Web/CSS/@media/prefers-reduced-motion
[mdn-prefers-contrast]: https://developer.mozilla.org/en-US/docs/Web/CSS/@media/prefers-contrast
[chrome-a11y-tree]: https://developer.chrome.com/blog/full-accessibility-tree/
[DialogBoundary]: /src/main/webapp/ui/src/components/DialogBoundary.js
[VisuallyHiddenHeading]: /src/main/webapp/ui/src/components/VisuallyHiddenHeading.js
[sara-live-regions]: https://www.sarasoueidan.com/blog/accessible-notifications-with-aria-live-regions-part-1
[accentedTheme]: /src/main/webapp/ui/src/accentedTheme.js
[accessibilityTips]: /src/main/wepapp/ui/src/components/AccessibilityTips.js
[dynamicHeadingLevel]: /src/main/webapp/ui/src/components/DynamicHeadingLevel.js
[deque-en301549]: https://www.deque.com/blog/301549-improve-accessibility/
[hidde-authoring-tools]: https://hidde.blog/authoring-tools-in-en-301-549/
[consistent-help]: https://www.w3.org/TR/WCAG22/#consistent-help
[wcag2.2-new]: https://www.w3.org/TR/WCAG22/#new-features-in-wcag-2-2
[redundant-entry]: https://www.w3.org/TR/WCAG22/#redundant-entry
