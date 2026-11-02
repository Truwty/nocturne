from __future__ import annotations
import copy
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_site_catalog import build_catalog, checked_support, trusted_url

class CatalogTest(unittest.TestCase):
    def setUp(self):
        self.name = "nocturne-1.0.0.jar"
        self.asset = {"name": self.name, "browser_download_url": "https://github.com/Truwty/nocturne/releases/download/v1.0.0/" + self.name, "digest": "sha256:" + "a" * 64}
        self.release = {"tag_name": "v1.0.0", "draft": False, "prerelease": False, "published_at": "2026-11-01T10:00:00Z", "assets": [self.asset]}
        self.audit = [{"tag": "v1.0.0", "name": self.name, "minecraft": ["26.1.2"], "loader": "Fabric", "java": 25, "sha256": "a" * 64}]
    def test_audited_build_retains_exact_download_and_compatibility(self):
        item = build_catalog([self.release], self.audit)["releases"][0]
        self.assertEqual(item["assets"][0]["url"], self.asset["browser_download_url"])
        self.assertEqual(item["assets"][0]["minecraft"], ["26.1.2"])
    def test_changed_audited_bytes_fail(self):
        self.asset["digest"] = "sha256:" + "b" * 64
        with self.assertRaises(ValueError): build_catalog([self.release], self.audit)
    def test_unknown_compatibility_is_not_invented(self):
        self.assertEqual([], build_catalog([self.release], [])["releases"])
    def test_draft_and_source_archives_are_excluded(self):
        self.release["draft"] = True
        self.assertEqual([], build_catalog([self.release], self.audit)["releases"])
        self.release["draft"] = False; self.asset["name"] = "nocturne-1.0.0-sources.jar"
        self.assertEqual([], build_catalog([self.release], self.audit)["releases"])
    def test_foreign_host_or_credentials_are_rejected(self):
        for url in ["https://evil.example/Truwty/nocturne/releases/download/v1.0.0/a.jar", "https://github.com@evil.example/Truwty/nocturne/releases/download/v1.0.0/a.jar", "http://github.com/Truwty/nocturne/releases/download/v1.0.0/a.jar"]:
            self.assertFalse(trusted_url(url, "/Truwty/nocturne/releases/download/"))
    def test_manifest_requires_matching_version_client_and_java(self):
        good = {"schema": 1, "version": "1.0.0", "minecraft": ["26.2"], "java": 25, "loader": "fabric", "environment": "client"}
        self.assertEqual(["26.2"], checked_support(good, "1.0.0")["minecraft"])
        for key, value in [("version", "0.9.0"), ("java", True), ("environment", "server"), ("minecraft", ["*"])]:
            changed = {**good, key: value}
            with self.assertRaises(ValueError): checked_support(changed, "1.0.0")
    def test_versioned_port_uses_its_own_manifest(self):
        self.asset["name"] = "nocturne-1.0.0-mc26.2.jar"
        self.asset["browser_download_url"] = "https://github.com/Truwty/nocturne/releases/download/v1.0.0/" + self.asset["name"]
        self.release["assets"].append({"name": "nocturne-mc26.2.json", "browser_download_url": "https://github.com/Truwty/nocturne/releases/download/v1.0.0/nocturne-mc26.2.json"})
        def load(url, maximum):
            return {"schema": 1, "version": "1.0.0", "minecraft": ["26.2"], "java": 25, "loader": "fabric", "environment": "client"}
        item = build_catalog([self.release], [], load)["releases"][0]
        self.assertEqual(["26.2"], item["assets"][0]["minecraft"])
    def test_preview_channel_and_trusted_changelog_preserved(self):
        self.release["prerelease"] = True; self.release["html_url"] = "https://evil.example"
        item = build_catalog([self.release], self.audit)["releases"][0]
        self.assertTrue(item["prerelease"])
        self.assertEqual("https://github.com/Truwty/nocturne/releases/tag/v1.0.0", item["notesUrl"])
    def test_each_loader_has_an_independent_manifest_and_download(self):
        self.release["assets"] = []
        for loader in ("fabric", "forge", "neoforge"):
            for name in (f"nocturne-1.0.0-{loader}-mc1.21.11.jar", f"nocturne-{loader}-mc1.21.11.json"):
                self.release["assets"].append({"name": name, "browser_download_url": "https://github.com/Truwty/nocturne/releases/download/v1.0.0/" + name})
        def load(url, maximum):
            loader = url.rsplit("/", 1)[1].split("-")[1]
            return {"schema": 1, "version": "1.0.0", "minecraft": ["1.21.11"], "java": 21, "loader": loader, "loaderMinimum": "1.0.0", "environment": "client"}
        assets = build_catalog([self.release], [], load)["releases"][0]["assets"]
        self.assertEqual({"Fabric", "Forge", "NeoForge"}, {a["loader"] for a in assets})
        self.assertEqual(3, len({a["url"] for a in assets}))
        self.assertTrue(all(a["dependencies"]["loader"] == "1.0.0" for a in assets))
    def test_loader_mismatch_fails_instead_of_serving_another_loader(self):
        self.asset["name"] = "nocturne-1.0.0-forge-mc1.21.11.jar"
        self.asset["browser_download_url"] = "https://github.com/Truwty/nocturne/releases/download/v1.0.0/" + self.asset["name"]
        self.release["assets"].append({"name": "nocturne-forge-mc1.21.11.json", "browser_download_url": "https://github.com/Truwty/nocturne/releases/download/v1.0.0/nocturne-forge-mc1.21.11.json"})
        with self.assertRaises(ValueError):
            build_catalog([self.release], [], lambda url, maximum: {"schema": 1, "version": "1.0.0", "minecraft": ["1.21.11"], "java": 21, "loader": "neoforge", "environment": "client"})

if __name__ == "__main__": unittest.main()
