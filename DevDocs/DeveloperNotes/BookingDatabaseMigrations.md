# Booking stack database migrations

`stack/booking/00-database-migrations` contains the complete database changes for
this booking stack, including development-only seed changesets. Every numbered
layer inherits this commit, so applying later layers does not change the contents
or checksums of earlier migration files. The schema and data changesets keep their
individual IDs, contexts and execution order; they are consolidated in Git history.

The migrations already present on `origin/main` remain unchanged. Follow
`src/main/resources/sqlUpdates/DatabaseChangeGuidelines.md` for subsequent schema
changes: add new changesets rather than editing an applied changeset.
