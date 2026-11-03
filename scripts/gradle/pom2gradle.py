"""Print pom.xml's direct dependencies as Gradle (Groovy DSL) declarations.

Usage: mvn help:effective-pom -Doutput=/tmp/effective-pom.xml
       python3 scripts/gradle/pom2gradle.py /tmp/effective-pom.xml
Paste the output into the dependencies block of build.gradle.
"""
import sys
import xml.etree.ElementTree as ET

NS = {"m": "http://maven.apache.org/POM/4.0.0"}
CONFIG = {
    "compile": "implementation",
    "provided": "providedCompile",
    "runtime": "runtimeOnly",
    "test": "testImplementation",
}


def text(element, name):
    child = element.find(f"m:{name}", NS)
    return child.text.strip() if child is not None and child.text else None


def main(path):
    project = ET.parse(path).getroot()
    for dep in project.find("m:dependencies", NS):
        group, artifact, version = text(dep, "groupId"), text(dep, "artifactId"), text(dep, "version")
        scope = text(dep, "scope") or "compile"
        if group == "org.projectlombok":
            for conf in ("compileOnly", "annotationProcessor", "testCompileOnly", "testAnnotationProcessor"):
                print(f"  {conf} '{group}:{artifact}:{version}'")
            continue
        coord = f"{group}:{artifact}:{version}"
        if text(dep, "classifier"):
            coord += f":{text(dep, 'classifier')}"
        if text(dep, "type") not in (None, "jar"):
            coord += f"@{text(dep, 'type')}"
        exclusions = dep.find("m:exclusions", NS)
        rules = [] if exclusions is None else [(text(e, "groupId"), text(e, "artifactId")) for e in exclusions]
        if not rules:
            print(f"  {CONFIG[scope]} '{coord}'")
            continue
        print(f"  {CONFIG[scope]}('{coord}') {{")
        for g, a in rules:
            if g == "*" and a == "*":
                print("    transitive = false")
            elif a == "*":
                print(f"    exclude group: '{g}'")
            else:
                print(f"    exclude group: '{g}', module: '{a}'")
        print("  }")


if __name__ == "__main__":
    main(sys.argv[1])
