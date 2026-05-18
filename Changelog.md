## Changelog

## 2.0.0 2026-04-29
- Spring 6 / Hibernate 6 / Jakarta namespace migration
- Switch to rspace-parent 3.0.0

## 1.2.0 2026-05-15
- adding global prefixes support for Instrument and InstrumentTemplate

## 1.1.0 2026-01-26
- switch to parent-pom 2.1.3
- harden XML parsing to avoid XXE attacks
- upgrade minor version of packages (commons-exec 1.3 -> 1.4.0, google.zxing 3.4.0 -> 3.5.4)

## 1.0.5 2026-01-14
- switch to parent-pom 2.1.2 (spring 5.3.25 -> 5.3.39, commons-codec 1.11 -> 1.13)
- update commons-compress (1.26.2 -> 1.28.0) and commons-io (2.14 -> 2.20)
- sort internally conflicting dependencies 
- some refactoring of ImageUtils, including more robust validation of incoming base64 image string 

## 1.0.4 2026-01-05
- switch to parent-pom 2.1.0 (upgrades various apache-commons dependencies)
- move away from apache commons-lang dependency (use commons-lang3 instead) 

## 1.0.3 2025-11-21
- switch to parent-pom 2.0.2 (upgrades shiro dependency 1.9.0 -> 1.13.0) 

## 1.0.2 2025-08-04 
- allow usernames with '#' (hash) char

## 1.0.1 2024-07-23
- version buildable with jitpack & downloadable from https://jitpack.io/#rspace-os/rspace-core-util
 
## 1.0.0 2024-06-20

- initial open-source release
- update apache commons-compress dependency (1.21 -> 1.26.2)
