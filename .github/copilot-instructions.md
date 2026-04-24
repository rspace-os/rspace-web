# RSpace Copilot Instructions

Full project instructions are in [AGENTS.md](../AGENTS.md) at the repo root. Please read that file for complete context on build, testing, architecture, and conventions.

## Quick Reference

RSpace is a Java/Spring + React/TypeScript research data management platform (ELN + Inventory).

- **Backend:** Java 11 source / Java 17 JDK, Spring MVC, MariaDB, Hibernate, Maven
- **Frontend:** TypeScript, React, Material-UI v5, Vitest, Playwright (`src/main/webapp/ui/`)
- **App:** `http://localhost:8080` · test users `user1a`–`user8h` · admin `sysadmin1`

Key commands:
```bash
mvn jetty:run -Denvironment=keepdbintact -Dspring.profiles.active=run  # run app
mvn clean test -Dfast=true                                              # fast unit tests
cd src/main/webapp/ui && npm run test                                   # frontend tests
```
