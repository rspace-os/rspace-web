# Link target summaries conflate "unreadable" with "nonexistent"

Status: accepted

The link-target summary endpoint (`GET /api/inventory/v1/linkTargets/{globalId}/summary`,
RSDEV-1182) backs the link card's state pills. It is callable with any
well-formed Global ID, so its responses must not let a caller probe which
records exist. We decided that the summary's `readable: false` deliberately
covers every degraded case identically: target unshared from the viewer,
never shared with the viewer, nonexistent, or hard-deleted by another owner
all produce field-for-field identical payloads (globalId only, `name`/`type`
null, `deleted` false). A unit test pins this invariant.

The user-facing consequence is that the card's pill says **"No access"**
rather than "Item unshared": unshared-after-linking is indistinguishable from
never-shared without consulting share history, and the pill must never imply
knowledge the viewer is not entitled to.

## Considered options

* **Per-viewer share-history detection** (to label genuinely unshared targets
  differently): rejected. It needs a new query chain per link per viewer,
  and only buys a more specific word on the pill.
* **"Item unshared" wording on the generic no-access state**: rejected as
  inaccurate for never-shared viewers, who legitimately see links on records
  shared with them whose targets never were.

## Consequences

* `deleted: true` can only ever arrive alongside `readable: true` (redaction
  zeroes it), so the "Target deleted" and "No access" pills can never co-occur
  and need no precedence logic.
* The "No access" pill also appears to viewers who never had access and for
  hard-deleted targets they do not own. This is accepted: in every such case
  Open could only produce an error page.
* Inventory targets ignore `readable` in the UI: every logged-in user retains
  the limited-read view, so Open keeps working and no pill is shown.
