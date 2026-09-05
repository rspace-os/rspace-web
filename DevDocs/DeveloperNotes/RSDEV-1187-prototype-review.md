# RSDEV-1187 prototype review

This review compares the supplied `BookingScheduling system for RSpace` prototype with the
Storybook prototypes and the requirements captured in [RSDEV-1187-booking-design.md](../../RSDEV-1187-booking-design.md).

## Alignment findings

The supplied HTML prototype is useful for checking the flow, but its bundled screenshots show
navigation labels, field labels, metadata, and actions overlapping. The Storybook stories now
handle narrow layouts explicitly:

- request rows wrap the date and review action instead of squeezing them into the request text;
- request and notification footers wrap long actions while keeping timestamps visible;
- activity text can wrap without pushing its timestamp outside the panel;
- fulfilment operation cards and dialog actions preserve their available width;
- sample result rows can scroll horizontally when the table cannot fit.

## Product gaps to keep visible

The sample-request stories cover the biobank request and approval path (aspect 7), not the equipment
booking requirements G2.1.4 to G2.1.8 shown in the supplied booking prototype. The ticket/design
notes explicitly leave these areas for later work:

- native bookings, recurrence, availability rules, and calendar feeds are not implemented;
- external/guest booking, usage and billing reports, and performer-linked tasks are out of scope;
- the current backend has booking configuration only, so the calendar and approval surfaces remain
  prototype-only;
- the prototype uses static fixture data, so permission filtering, busy-time privacy, and audit
  behaviour still need service-level implementation and tests.

These are scope decisions, not Storybook alignment defects.
