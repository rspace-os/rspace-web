---
status: proposed
---

# Remove the Folder.children mapping

## Context

`Folder` mapped its content as `@OneToMany(mappedBy = "folder", cascade = ALL, orphanRemoval = true)
Set<RecordToFolder> children`, the inverse side of `RecordToFolder`. `BaseRecord.parents` maps the
same edges from the child side, and is loaded eagerly.

Adding or removing a child went through `children.add()` / `children.remove()`, which made Hibernate
initialise the whole set: every `RecordToFolder` row plus the record it points to. For folders with
thousands of items (Workspace root, Api Inbox, group and autoshare folders) that cost about a second
per create, share, unshare or delete on a local stack with 2000 items. A 2025 change added
`skipAddingToChildren` flags so that some add paths only touched `child.parents`. That helped
document creation, but:

- it only covered adding; removal (unshare, delete, move) still loaded the folder content;
- the Hibernate 6 migration switched sharing and document copy back to `skipAddingToChildren=false`
  (`RecordSharingManagerImpl`, `DocumentCopyManagerImpl`), so autoshare on document creation loaded
  the autoshare folder again;
- in one session `children` and `parents` could disagree. A document added with the flag set was
  missing from an already loaded `children` set, so later reads of `children` in the same session
  (for example `RecordDeletionManagerImpl.iterateAndDelete` when deleting a folder) silently missed
  it. Plain `SpringTransactionalTest`s that share and then move or delete a document were hard to
  write.

## Findings

Verified, except where marked "going by the code":

- A `SpringTransactionalTest` that shares one document into a group folder already holding 20 shared
  documents loaded 21 `StructuredDocument`s with `children` (Hibernate statistics, session and
  second-level cache cleared before the share). The test is
  `RecordSharingTest.shareUnshareAndDeleteDoNotLoadOtherItemsOfGroupFolder`. With `children`
  removed the same test passes (fewer than 20 loads for share, unshare and delete).
- `RecordToFolder` owns both foreign keys, so only `child.parents` needs to change for an edge to be
  stored. But the session factory is bootstrapped natively (`LocalSessionFactoryBean`), and with
  Hibernate 6.4 a flush cascades only the native `SAVE_UPDATE` action, not JPA `PERSIST`. A new edge
  in `child.parents` therefore fails the next auto-flush with `TransientObjectException` unless
  `session.persist(child)` runs first. This is what the Hibernate 6 migration worked around by going
  back to `children` and saving the folder. `cascade = ALL` on `BaseRecord.parents` makes the flush
  insert the edge: on 6.4 JPA `ALL` cascades every action, `SAVE_UPDATE` included.
- Three custom Liquibase update classes that walked `getChildrens()` are not referenced from any
  changelog (`AddUserPermissionToCreateFolderInGroupFoldersv028`,
  `CreateMissingUserFoldersForLabAdminsAndPIs`,
  `MoveTemplateFolderFromGalleryToHomeFolderRSPAC921_1_36`).
- No code hard-deletes a `Folder` entity through Hibernate, so losing the `cascade = ALL` on
  `children` (which deleted a folder's edges with it) breaks nothing: user deletion removes
  `RecordToFolder` rows with SQL, and no HQL navigates `children`.
- `orphanRemoval` on `parents` does not delete edges when a stale detached copy of a record is
  merged (saved after another transaction added an edge): Hibernate takes orphans from the detached
  collection's own snapshot, so only edges removed from that copy are deleted. Removing a child
  passed in detached still deletes its edge (`FolderManagerSpringTest`:
  `savingStaleCopyOfRecordKeepsFolderEdgesAddedSinceItWasLoaded`,
  `removingDetachedChildDeletesItsEdge`).
- `orphanRemoval` does delete every edge when an Envers revision copy, whose `parents` is never
  filled because it is `@NotAudited`, is merged into the current record. Restoring a revision did
  exactly that (`AuditManager2IT.restoreRevision` lost the document's root edge) until the restore
  reused the current record's `parents`, as the revision view already did. A document thumbnail
  request for an old revision of a gallery file also saved the revision copy; a revision is no
  longer converted or saved (`ImageControllerTest.thumbnailOfRevisionIsNeitherGeneratedNorSaved`).
  No other path saves a revision copy.
- Moving a folder into a folder with 30 subfolders loads fewer than 30 `Folder`s
  (`FolderManagerSpringTest.movingFolderDoesNotLoadOtherContentOfDestination`). The old code saved
  the whole target folder tree recursively (`recursiveSave`), loading all of its content (going by
  the code).
- `Folder.addChild` must add the edge through `child.getParents()`: on a Hibernate proxy the
  `parents` field is the proxy's own unmapped set, so writing to it lost the edge
  (`FolderManagerSpringTest.addingProxiedFolderPersistsItsEdge`). The old code also added the edge
  to `children`, which saved it (going by the code).
- A content query flushes the session, and a document is dirty as soon as it is loaded
  (`StructuredDocumentLoadListener` clears its delta), so a document loaded before a query and
  changed after it is written twice. Moving a folder therefore walks its content in one top-down
  pass that gives all children of a folder their final ACL before descending into any of them. A
  first version used separate removal and add walks, and another descended before finishing the
  siblings; both wrote documents twice
  (`FolderManagerSpringTest.movingFolderWritesEachItemInsideOnce`, with a subfolder listed before
  the documents: 6 document updates for 3 documents, now 3).
- A document in both a folder and a notebook inside the moved folder is visited twice. The pass
  removes elements from an item only on its first visit; otherwise the notebook visit dropped the PI
  and lab admin read that the folder visit had added back, since notebooks don't pass it on to
  entries (`FolderManagerSpringTest.moveKeepsAclInheritedThroughFolderWhenDocIsAlsoInNotebook`;
  the old code kept it). Such a document is still written twice, as a content query flushes it
  between visits: 4 document updates for two such documents, 2 with the old code.

## Decision

Remove `Folder.children` and everything that walked it in memory:

- `BaseRecord.parents` is the only mapped side of folder content edges, with `orphanRemoval = true`
  and JPA `cascade = ALL`. `Folder.addChild` adds a `RecordToFolder` to `child.parents`,
  `Folder.removeChild` removes it. Neither touches the folder's content.
- The `skipAddingToChildren` parameters go from `Folder` and `RecordManager`.
- Model methods that walked content (`getChildrens`, `getSubfolders`, `getSystemSubFolderByName`,
  `process`, recursive `copy` and `clearACL`) go. Callers use `FolderDao` queries (`getChildren`,
  `getFolderChildrenIds`, `hasChildren`, `getSubFolders`, `getSystemSubFolderByName`) or the new
  `FolderManager.process`. `FolderManager` methods that only served the in-memory walk, or had no
  production caller, go too (`getFolder(.., SearchDepth)`, `getFromURLPath`, `getInitialisedFolder`,
  `getMediaFolderFromURLPath`, `setRecordFromFolderDeleted`, `removeRecordFromFolder`).
- ACL propagation policies only update the direct child in `onAdd` / `onRemove`. Descendants are
  updated by services: `FolderManagerImpl.addChild`, `removeBaseRecordFromFolder` and `move` walk
  the content with `FolderDao.getChildren`, applying `onAddDescendant` and
  `aclsRemovedFromDescendants`. The add walk runs only when `propagatesToDescendants()` is true (the
  inherit-from-parent policies), so adding a user's Workspace to a PI's LabGroups folder with the
  null policy doesn't walk it; the removal walk runs only when an edge was really removed and
  `aclsRemovedFromDescendants` returns elements. Sharing and unsharing go through those
  `FolderManager` methods. The unused `ACLPropagationPolicy.propagate` goes.
- The unreferenced Liquibase update classes and their tests are deleted.

Considered and rejected:

- Keep `children` as a read-only mapping for whole-tree traversals. Smaller change, but in-session
  staleness stays.
- Give the ACL policies a children lookup function. Keeps the recursion in the model but puts a
  persistence dependency into it.

## Consequences

- Adding to or removing from a folder no longer loads that folder's content. Adding, removing or
  moving a folder or notebook still walks its own content.
- In-session reads of folder content are queries, so they see edges added earlier in the same
  transaction (auto-flush), and tests no longer depend on which side of the relation was updated.
  `RecordSharingTest.sharedDocumentCanBeMovedAndDeletedInSameTransaction` shares, moves and deletes
  a document in one transaction, and `sharedNotebookCanBeMovedUnsharedAndDeletedInSameTransaction`
  does the same for a notebook, checking its entry's ACL; whether they failed with the old code was
  not checked.
- Anything that adds or removes a folder or notebook with content without going through
  `FolderManager` no longer updates the ACLs of that content. Callers that build trees bottom-up
  must use `FolderManager`, or add the folder before its content. Removing the group folder and its
  shared snippets folder from a leaving member's folders goes through
  `FolderManager.removeBaseRecordFromFolder` too; going by the code it walks nothing, because the
  member's folders hold only user ACL elements and the group's folders only group elements.
- Pure model tests can no longer read a folder's content in memory. They assert from the child side
  (`getParents`, `getParentFolders`) or use a fake `FolderDao`.
- The mapping needs no change for the planned move to Hibernate 7.1 (checked against the 7.1.0
  sources). 7.0 removed `org.hibernate.annotations.CascadeType.SAVE_UPDATE` (deprecated in 6.4.4),
  and in 7.1 every flush cascades `PERSIST_ON_FLUSH` whatever the bootstrap, which the `PERSIST`
  inside `ALL` covers. `ALL` also cascades detach, lock and replicate to the edges; nothing evicts,
  locks or replicates records. One difference no current path hits: a detached `RecordToFolder` in a
  managed record's `parents` is reattached on 6.4 but fails the flush on 7.1 ("Detached entity
  passed to persist"); no current path puts one there (see Findings on revision copies).

## Concurrency

Constraint: the change must not add concurrency problems; ones the old code had too are listed in
the appendix. Going by the code and the Hibernate 6.4.4 sources (7.1.0 where noted); nothing was run
under concurrent load.

Facts this rests on: `BaseRecord`, `Folder` and `RecordToFolder` have no `@Version` and are not in
the second-level cache (users and groups, `AbstractUserOrGroupImpl`, do have `@Version`);
`BaseRecord` has no `@DynamicUpdate` (an UPDATE writes every column of each changed table); nothing
in the folder or sharing code takes explicit locks; MariaDB runs with REPEATABLE READ (with
`innodb_snapshot_isolation = 0` in the dev stack; with it on, the lost updates in the appendix
become "record changed" errors instead); a folder's content is a set of `RecordToFolder` rows, and
changing content never writes the folder's own row.

What stays the same or improves:

- **Same writes and locks.** Adding or removing an item is still one `RecordToFolder` INSERT or
  DELETE plus an update of the child's row (ACL, type), with the same Envers audit rows (both sides
  of the relation are `@NotAudited`). The edge INSERT takes shared locks on the folder's `Folder`
  row and the child's `BaseRecord` row (foreign-key checks), as before; ACL, name and deleted flag
  are in `BaseRecord`, so a walk that updates a folder doesn't block adds into it. A removal is a
  DELETE by primary key. Concurrent adds and removes in one folder still don't block each other. A
  `FolderManager` walk updates the same rows as the old model recursion, each once with its final
  ACL, except a document reached twice in a move (see Findings). Sharing or unsharing a notebook,
  and changing the permission of a shared notebook, are also exceptions: see Failure modes below.
- **Shorter transactions.** Most of the old request time on large folders was loading the folder
  content inside the transaction (see Performance). The single-transaction bulk autoshare shrinks
  the same way going by the code, and `move` no longer loads the whole target subtree. Shorter
  transactions narrow the windows for the pre-existing races in the appendix.
- **No new locking reads.** Content is read with plain queries (consistent reads), as the lazy
  `children` load was. Under REPEATABLE READ they see the same snapshot the lazy load would have,
  plus the transaction's own earlier writes.

Failure modes that change, all low:

- The content queries auto-flush, so all pending writes of the session (for example the edge, the
  walk root's ACL, the sharee group's row in a notebook share, deleted flags in a folder deletion)
  reach the database at a walk's first query instead of at commit, and the walk's own updates go out
  level by level (in `FolderManager` walks, final except for a document reached twice in a move).
  Same locks, taken earlier and held for the rest of the walk; a conflict (an edge another
  transaction already deleted, a version conflict on the group's row) fails there instead of at
  commit. Document adds and removes run no walk query and are unaffected.
- Notebook sharing walks the entries in `RecordSharingManagerImpl` (`propagateACLAddition` /
  `propagateACLRemoval`) and then again in `FolderManager`, and the second walk's query flushes the
  first walk's result. Sharing a notebook therefore writes each entry twice; changing the
  permission of a shared notebook writes the notebook, its entries and the sharee's row twice (the
  group's version goes up twice); unsharing a notebook with a user writes the entries once more per
  removed element (the old code already wrote them twice there). Same rows in the same transaction, the
  same exclusive lock order, one Envers row per entity, and the intermediate state is never visible
  to other transactions, so this costs writes but adds no race. One ordering detail: in a notebook
  share the edge INSERT's shared lock on the target folder's `Folder` row now comes after the
  sharee row's update instead of before it; no path writes that `Folder` row and then the sharee or
  notebook row, so no deadlock follows.
- Removing a child passed in detached deletes its edge from the detached copy's snapshot. If another
  transaction removed that edge after the copy was loaded but before this transaction's first read,
  this now fails the transaction (`StaleObjectStateException` at flush, or an entity-not-found error
  while merging) instead of silently doing nothing; later removals failed before too. No caller is
  known to pass a stale detached record here; sharing, unsharing and deletion load it in the same
  transaction.

## Performance

Measured on a local Docker dev stack with a 50-member lab group, one member's 2000 autoshared
documents in Workspace root and 2000 files in their Gallery Documents folder: HTTP round trip,
medians of 10 runs from the same restored dump. Creating, sharing, unsharing and deleting
autoshared documents took 1.05 to 1.31 s with `children` and 0.18 to 0.46 s without; Gallery
uploads and the Gallery move dialog about 1 s and 0.09 to 0.20 s. An upload into the small Gallery
Api Inbox, the control, took 0.15 s with `children` and 0.18 s without (0.14 and 0.15 s in a
second run). Seeding with `children` showed the bulk autoshare slowdown: sharing existing
documents one by one in one transaction slowed from about 0.45 s to over 1 s per document as the
autoshare folder grew. Move was not measured on the stack (see Findings for its tests).

## Open questions and risks

- `FolderDao.getSystemSubFolderByName` compares names with the database collation (case and trailing
  spaces ignored) where the old model method used Java `equals`; only matters for sibling system
  folders whose names differ just in that way, and none do (going by the code).
- Whole-tree operations (export, folder deletion, restore, ACL propagation to the content of an
  added, removed or moved folder or notebook) still load whole subtrees, now by query. That is
  inherent to them, but `FolderDao.getChildren` loads records one level at a time, so deep trees
  cost one query per folder, plus one per record for its eager `parents`, as the lazy `children`
  load did. Adding a new, empty folder or notebook also runs one content query, which finds
  nothing.
- Sharing, unsharing or changing the permission of a notebook writes its entries more than once
  (see Concurrency). Still far cheaper than before; merging `propagateACLAddition` /
  `propagateACLRemoval` into the `FolderManager` walk would make it one pass.
- Code that walks content casts to `Folder` after `isFolder()` (for example `FolderManagerImpl`,
  `RecordSharingManagerImpl`, `RecordDeletionManagerImpl`, `AuditManagerImpl`); a `BaseRecord` proxy
  would fail the cast (going by the code). The old code already did such casts elsewhere.
- Any future code that merges an Envers revision copy of a record must first give it the current
  record's `parents`, or not save it (see Findings).

## Appendix: pre-existing races

These exist before and after the change, unchanged; see Concurrency for the facts they rest on.

- Two transactions adding the same item to the same folder both insert an edge (no unique
  constraint; `RecordGroupSharing` has none either). Concurrent shares with the same sharee mostly
  roll one back (versioned sharee row, below); duplicates come from paths without that write, such
  as moving an item with several parents into the same target twice. `getChildren` and
  `getSubFolders` return the item once, as Hibernate 6 removes duplicate entity results (checked
  with a duplicate row in `FolderDaoTest.contentQueries`); `getFolderChildrenIds` returns its id
  twice. The old `Set` collapsed duplicates. Removing the item deletes one of the two edges, because
  `parents` holds equal edges once, so the item stays listed in the folder after it was unshared or
  removed.
- Two overlapping transactions removing the same edge (two unshare requests): the second DELETE
  waits for the first's row lock, finds no row, and Hibernate fails that transaction. If the first
  committed before the second's first read, the second sees no edge and does nothing. Deleting a
  folder marks its content edges deleted, which fails the same way if another transaction removes
  one of those edges.
- A transaction that read a record before another changed it overwrites the whole row (ACL, deleted
  flag, name) when it saves. Two concurrent shares of the same item can instead deadlock: each one's
  edge and `RecordGroupSharing` INSERT takes a shared lock on the item's row before its ACL UPDATE
  needs an exclusive one, and InnoDB rolls one back. The descendant walk updates the same rows as
  the old model recursion, but rows may reach the database in a different order (walk order rather
  than load order).
- Every share or unshare also writes the sharee's user or group row, which is versioned. Two
  overlapping shares with the same group or user: the second waits for the first's row lock, then
  fails with an optimistic-lock error (for example two members' documents autoshared into the group
  at the same moment: one stays unshared). A bulk autoshare holds that lock until it commits, so
  other shares with the group wait up to `innodb_lock_wait_timeout` and then fail; the bulk run is
  now shorter.
- An item created in a folder while another transaction moves, shares, deletes or restores that
  folder isn't seen by that transaction's walk, so it misses the ACL change or stays live inside a
  deleted folder.
- Merging a stale detached record re-inserts an edge another transaction deleted (Hibernate 6.4
  treats the missing row as transient, and `parents` cascades MERGE before and after this change).
  On Hibernate 7.1 the merge fails with `StaleObjectStateException` instead, because the edge's id
  is generated. No caller is known to merge stale records.
- Two concurrent moves that nest two folders into each other both pass the cycle check, which reads
  parents from each transaction's snapshot. No content walk guards against cycles, before or after
  this change; once such a cycle is committed, every recursive content walk on it ends in
  `StackOverflowError`.
- Getting or creating a system subfolder (the shared snippets folder on login or share, a user's
  project groups shared folder, the time-rotated API inbox folder) checks and then creates, so two
  concurrent requests can both create it. Where the old check was in memory (shared snippets), it
  read the same snapshot as the query now. First-login content setup (`hasChildren` on the root)
  and the per-pair individual shared folder also check and create, but both write the versioned
  user row, so one of two concurrent requests rolls back. The user's ontologies folder
  (`OntologyDocManager`) is check-then-create too, reading the same snapshot as before.
