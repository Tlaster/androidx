#!/usr/bin/env python3
"""Check the staged KMP/MinGW publication graph before uploading it."""
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

repository = Path(sys.argv[1])
modules = {
    "room3": ("3.0.3-mingw-SNAPSHOT", ["room3-common", "room3-runtime", "room3-paging"]),
    "sqlite": ("2.7.1-mingw-SNAPSHOT", ["sqlite", "sqlite-async", "sqlite-framework", "sqlite-bundled"]),
    "datastore": ("1.3.0-alpha11-mingw-SNAPSHOT", ["datastore-core", "datastore-core-okio"]),
}
upstream_groups = {f"androidx.{family}" for family in modules}
expected = {
    (f"moe.tlaster.androidx.{family}", name + suffix, version)
    for family, (version, names) in modules.items()
    for name in names
    for suffix in ("", "-mingwx64")
}
for group, artifact, version in sorted(expected):
    directory = repository / group.replace(".", "/") / artifact / version
    snapshots = ET.parse(directory / "maven-metadata.xml").getroot()
    def artifact_file(extension):
        value = next(
            item.findtext("value")
            for item in snapshots.findall("./versioning/snapshotVersions/snapshotVersion")
            if item.findtext("extension") == extension and item.find("classifier") is None
        )
        return directory / f"{artifact}-{value}.{extension}"
    metadata = json.loads(artifact_file("module").read_text())
    component = metadata["component"]
    assert (component["group"], component["module"], component["version"]) in expected
    native_targets = set()
    for variant in metadata["variants"]:
        target = variant.get("attributes", {}).get("org.jetbrains.kotlin.native.target")
        if target:
            native_targets.add(target)
        redirect = variant.get("available-at")
        if redirect:
            assert (redirect["group"], redirect["module"], redirect["version"]) in expected
        for dependency in variant.get("dependencies", []) + variant.get("dependencyConstraints", []):
            assert dependency["group"] not in upstream_groups, dependency
            if dependency["group"].startswith("moe.tlaster.androidx."):
                assert (dependency["group"], dependency["module"], dependency["version"]["requires"]) in expected
    assert native_targets == {"mingw_x64"}, (artifact, native_targets)
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    pom = ET.parse(artifact_file("pom")).getroot()
    assert pom.findtext("m:groupId", namespaces=namespace) == group
    assert pom.findtext("m:artifactId", namespaces=namespace) == artifact
    assert pom.findtext("m:version", namespaces=namespace) == version
    for dependency in pom.findall(".//m:dependency", namespace):
        assert dependency.findtext("m:groupId", namespaces=namespace) not in upstream_groups
    if not artifact.endswith("-mingwx64"):
        assert pom.findtext("m:packaging", default="jar", namespaces=namespace) == "pom"
print(f"Verified {len(expected)} publications: namespace, versions, MinGW variants and dependency graph.")
