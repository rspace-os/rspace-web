# Communication identifiers

`Communication` uses TABLE_PER_CLASS inheritance. Notification, MessageOrRequest,
GroupMessageOrRequest, CreateGroupMessageOrRequest and ShareRecordMessageOrRequest
store complete rows in separate tables, all using `communication_seq`. This is
not JOINED inheritance. Independent AUTO_INCREMENT counters would make IDs
ambiguous in polymorphic queries and CommunicationTarget.communication_id.
Hibernate also rejects IDENTITY with TABLE_PER_CLASS.

CommunicationTarget is a separate entity with `communication_target_seq`.
Both use Hibernate's pooled optimizer, allocationSize 50, and database sequence
INCREMENT BY 50. Each sequence value is the upper bound of a block of 50 IDs.
Database sequence allocation gives different nodes disjoint blocks. A node
restart can leave unused IDs. Sequence allocation is not rolled back, so gaps
are expected. Communication previously fetched one TABLE value per ID; targets
already used blocks of 50. IDs are assigned on persist, before flush, and JDBC
insert batching remains available.

Hibernate 6.4.4 with MariaDB106Dialect selects SequenceStyleGenerator backed by
SequenceStructure. Its callback uses the session's JdbcCoordinator to execute
`select next value for ...`, without JdbcIsolationDelegate or another connection.
See the [Hibernate 6.4.4 sequence callback source](https://github.com/hibernate/hibernate-orm/blob/6.4.4/hibernate-core/src/main/java/org/hibernate/id/enhanced/SequenceStructure.java).
RSpace supports MariaDB 10.11 and 11.8, which support these native sequences.

## Deployment

Stop all application nodes and background writers before applying the migration.
Do not run old TABLE-allocator instances alongside sequence-allocator instances.
The two allocators cannot coordinate their reservations. Take a backup, apply
Liquibase, verify both sequences increment by 50, then start only the new version.
The migration requires CREATE SEQUENCE permission, normally covered by schema
CREATE privileges. It preserves the old hibernate_sequences rows.

The migration computes high water marks at execution time across all five
communication tables and the target table, including any retained corresponding
_AUD tables. Current mappings have no Envers @Audited annotations in this
hierarchy; the application audit trail is separate. Its starting upper bound is
`(FLOOR(GREATEST(MAX(id), old next_val) / 50) + 2) * 50`. Thus even the first
pooled ID, start minus 49, exceeds both high water marks.

No foreign keys or existing IDs change. CommunicationTarget has a recipient FK
and a polymorphic communication_id without a physical communication FK.
Communication subclasses retain their user, record, group, next/previous and
collection relationships. The inbound CreateGroupMessageOrRequest_emails FK
continues to reference the same IDs. Audit revisions reuse entity IDs rather than allocate
new ones. No application native SQL inserts into these entity tables were found;
any external writer must also use this shared allocation protocol.

MariaDB DDL commits implicitly. If migration fails after creating one sequence,
keep writers stopped, inspect the failure and the two sequences before retrying.
Do not blindly drop or restart a sequence after writers have used it.

## Rollback

Stop every new-version node first. While writers remain stopped, compute the
maximum ID across the same live and retained audit tables. Also read
`next_not_cached_value` from each sequence table. With NOCACHE this is a
conservative bound above sequence values already handed to application nodes.
For each old hibernate_sequences key, Communication and CommunicationTarget,
insert the row if missing, otherwise update it to at least
`GREATEST(existing next_val, max_id, next_not_cached_value) + 100`.
Do not lower a counter. This margin covers the target allocator's pooled block.
Only then start the old application. Keep the sequences for diagnosis.

There is intentionally no automatic Liquibase rollback. To redeploy the sequence
version after an application rollback, stop writers and explicitly restart both
sequences above the newly computed live, audit and TABLE-counter high water marks
using the migration formula. Reconcile Liquibase history as part of that planned
migration. Never let an already-recorded changeset silently reuse stale sequences.
