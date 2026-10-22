---
status: accepted
---

# Inventory descriptions get their own 2000-character cap

Decided with Nico on 2026-10-21.

## Decision

1. **Override, don't raise, the shared cap.** `EditInfo.DESCRIPTION_LENGTH` (250) also caps
   tags, gallery captions, ELN descriptions and the cryomedium of the cryopreserve operation.
   Raising it would raise all of those and let a cryomedium overflow the 1000-character
   template field value it is stored in.
2. **The column is exactly the validated cap, with no headroom.** Java and JavaScript count
   UTF-16 code units, never fewer than the characters MariaDB counts in a `utf8mb4` varchar, so
   anything that passes validation fits, and a write path that skips validation fails at the
   database instead of storing more than users are promised.
3. **The info card scrolls, capped at 40% of the viewport.** It opens in a tooltip that closes
   when the page scrolls, so a long description must scroll inside the card. 50% does not fit
   beside a badge in the middle of the screen.

## Consequences

- Descriptions saved at 2000 characters cannot be shrunk back.
