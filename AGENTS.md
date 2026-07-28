# AGENTS.md

Project instructions for AI coding agents (Claude Code, OpenAI Codex, Gemini CLI, Cursor, etc.).

## Scope

These instructions apply to the entire repository unless a deeper `AGENTS.md` overrides them.

## Repository Shape & Working Rules

- This is a pure Java library — no runnable application, no frontend.
- Build outputs (`target/`) must stay out of diffs.
- Keep changes narrowly scoped to the user request. Do not refactor unrelated areas.
- Match existing code style and conventions in the touched area.

## Project Overview

`rspace-core-model` is the JPA/Hibernate domain model library for RSpace ELN (Electronic Lab Notebook). It defines persistent entities, permissions, DTOs, and enums shared across the RSpace application — consumed by `rspace-web` and other sibling modules.

Published via JitPack. Parent POM: `rspace-parent` (from `rspace-os` — controls dependency versions). Sibling dependencies: `rspace-core-util`, `rspace-document-conversion-spi`, `rspace-audit`, `rspace-test-util`.

## Tech Stack

- Java 17 JDK and language level
- Jakarta Persistence (`jakarta.persistence`, not `javax`)
- Jakarta Bean Validation (`jakarta.validation`) with Hibernate Validator
- Hibernate ORM + Hibernate Search (Lucene indexing via `@Indexed`, `@FullTextField`)
- Maven 3.9+ build tool
- Lombok (`@Data`, `@EqualsAndHashCode`, etc.)

## Build & Test

```bash
mvn clean install                       # build + all tests
mvn test                                # unit tests only
mvn test -Dtest=FooTest                 # single test class
mvn test -Dtest=FooTest#barMethod       # single test method
```

Some Hibernate integration tests (`src/test/java/.../model/test/Hibernate*Test.java`) require a local MySQL database:

```sql
CREATE DATABASE hibtest;
GRANT ALL ON hibtest.* TO 'rspacedbuser'@'localhost';
```

## Architecture

### Entity hierarchy

`BaseRecord` (`model/record/`) is the root of the document/folder tree. Key subtypes:
- `StructuredDocument` — an ELN document with fields
- `Folder` / `Notebook` — containers in the record tree
- `EcatMediaFile` and subtypes (`EcatImage`, `EcatVideo`, `EcatAudio`, `EcatDocumentFile`, `EcatChemistryFile`) — attached files

`InventoryRecord` (`model/inventory/`) is the root for inventory entities: `Sample`, `SubSample`, `Container`.

`User` and `Group` model users and lab groups. `Community` groups multiple labs.

### Permissions

`model/permissions/` — constraint-based permission system. `ConstraintBasedPermission` with `ACLElement` entries checked by `ConstraintPermissionResolver`. Entities expose permissions via `AbstractEntityPermissionAdapter` subclasses.

### Key packages

- `model/record/` — record tree, forms, fields, initialization policies
- `model/inventory/` — inventory domain (samples, containers, barcodes)
- `model/comms/` — internal messaging/notifications
- `model/netfiles/` — external filestore integrations (Samba, SFTP, iRODS, etc.)
- `model/dto/` — data transfer objects for API responses
- `model/field/` — field type definitions for structured documents
- `model/audit/` — audit trail data model
- `model/views/` — search-related view objects
- `model/stoichiometry/` — chemical stoichiometry calculations

## Conventions

- Entities use Lombok — be careful with `equals`/`hashCode` on JPA entities (exclude lazy collections).
- Enums often implement `com.researchspace.core.util.IDescribable` for display names.
- Use JUnit 5 for new test classes unless the file already uses JUnit 4.
- **TABLE id generation:** every `@GeneratedValue(strategy = TABLE)` entity carries an explicit named
  `@TableGenerator` keyed to its own row in `hibernate_sequences`. This preserves the one-counter-per-table
  layout Hibernate 5 used, which Hibernate 6's enhanced generator would otherwise collapse into a single
  shared `default` segment. Never add a bare `@GeneratedValue(strategy = TABLE)` without a named generator.
  The full rationale, the upgrade-time DB reseed, and the guard test live in rspace-web
  (`sqlUpdates/changeLog-rsdev-444.xml`, `TableIdGeneratorConfigTest`, `DevDocs/DeveloperNotes/UpgradingToSpring6.md`).

## Common Pitfalls

1. **Lombok + JPA:** `@EqualsAndHashCode` must not include lazy-loaded collections — causes `LazyInitializationException` outside a session.
2. **Hibernate tests need MySQL:** The `Hibernate*Test` classes in `model/test/` won't pass without the `hibtest` database set up locally.
3. **Parent POM versions:** If a dependency version isn't in this project's `pom.xml`, check `rspace-parent`.

## Agent-Specific Config Files

- **AGENTS.md** (this file): Primary instructions for all AI agents
- **CLAUDE.md**: Imports AGENTS.md via `@AGENTS.md` for Claude Code / Anthropic agents
