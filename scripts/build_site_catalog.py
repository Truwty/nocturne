"""Generate the static download catalog from audited JARs and release manifests."""
from __future__ import annotations
import json
import os
from pathlib import Path
import re
import urllib.parse
import urllib.request
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]
REPO = "https://github.com/Truwty/nocturne"
API = "https://api.github.com/repos/Truwty/nocturne/releases"
TAG = re.compile(r"v?[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z.-]+)?")
GAME = re.compile(r"[0-9]+(?:\.[0-9]+){1,2}")

def trusted_url(value: str, prefix: str) -> bool:
    try:
        url = urllib.parse.urlsplit(value)
        return url.scheme == "https" and url.netloc == "github.com" and url.path.startswith(prefix) and not url.query and not url.fragment
    except (TypeError, ValueError):
        return False

def fetch_json(url: str, maximum: int = 2_097_152) -> object:
    headers = {"User-Agent": "Nocturne-Pages-catalog", "Accept": "application/vnd.github+json"}
    if urllib.parse.urlsplit(url).netloc == "api.github.com" and os.environ.get("GITHUB_TOKEN"):
        headers["Authorization"] = "Bearer " + os.environ["GITHUB_TOKEN"]
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=30) as response:
        body = response.read(maximum + 1)
    if len(body) > maximum:
        raise ValueError("Catalog response exceeds limit")
    return json.loads(body)

def checked_support(data: dict, version: str) -> dict:
    games = data.get("minecraft")
    java = data.get("java")
    if data.get("schema") != 1 or data.get("version") != version or not isinstance(games, list) or not games or len(games) > 100 or not all(isinstance(g, str) and GAME.fullmatch(g) for g in games):
        raise ValueError("Invalid compatibility manifest")
    if data.get("loader") not in {"fabric", "forge", "neoforge"} or data.get("environment") != "client" or not isinstance(java, int) or isinstance(java, bool) or not 8 <= java <= 30:
        raise ValueError("Invalid loader/Java/environment requirements")
    return {"minecraft": games, "loader": data["loader"].capitalize(), "java": java}

def build_catalog(releases: list, audited: list, load=fetch_json) -> dict:
    known = {(item["tag"], item["name"]): item for item in audited}
    result = []
    for release in releases:
        tag = release.get("tag_name", "")
        if release.get("draft") or not TAG.fullmatch(tag):
            continue
        version = tag.removeprefix("v")
        prefix = "/Truwty/nocturne/releases/download/" + urllib.parse.quote(tag, safe="") + "/"
        assets = release.get("assets", [])
        targets = []
        for asset in assets:
            name = asset.get("name", "")
            match = re.fullmatch(r"nocturne-" + re.escape(version) + r"(?:-mc([0-9.]+))?\.jar", name)
            url = asset.get("browser_download_url", "")
            if not match or not trusted_url(url, prefix):
                continue
            support = known.get((tag, name))
            checksum = asset.get("digest", "").removeprefix("sha256:") if asset.get("digest") else None
            if support:
                if checksum and checksum != support["sha256"]:
                    raise ValueError("Audited release bytes changed: " + name)
                checksum = support["sha256"]
                support = {key: support[key] for key in ("minecraft", "loader", "java")}
            else:
                game = match.group(1) or "26.1.2"
                marker = next((a for a in assets if a.get("name") == "nocturne-mc" + game + ".json"), None)
                if marker is None:
                    continue
                marker_url = marker.get("browser_download_url", "")
                if not trusted_url(marker_url, prefix):
                    raise ValueError("Untrusted compatibility URL")
                support = checked_support(load(marker_url, 65536), version)
                if game not in support["minecraft"]:
                    raise ValueError("Compatibility marker target mismatch")
            if checksum is not None and not re.fullmatch(r"[a-fA-F0-9]{64}", checksum):
                raise ValueError("Invalid SHA-256 digest")
            targets.append({**support, "name": name, "url": url, "sha256": checksum})
        if targets:
            result.append({"version": version, "tag": tag, "publishedAt": release["published_at"], "prerelease": bool(release["prerelease"]), "notesUrl": REPO + "/releases/tag/" + tag, "assets": targets})
    result.sort(key=lambda item: item["publishedAt"], reverse=True)
    return {"schema": 1, "generatedAt": datetime.now(timezone.utc).isoformat(), "releases": result}

def main() -> None:
    releases = []
    for page in range(1, 11):
        batch = fetch_json(API + f"?per_page=100&page={page}")
        if not isinstance(batch, list):
            raise ValueError("Invalid GitHub releases response")
        releases.extend(batch)
        if len(batch) < 100:
            break
    audited = json.loads((ROOT / "compatibility/published-artifacts.json").read_text(encoding="utf-8"))["artifacts"]
    catalog = build_catalog(releases, audited)
    if not catalog["releases"]:
        raise RuntimeError("No verified releases; refusing to publish an empty download catalog")
    output = ROOT / "site/releases.json"
    output.write_text(json.dumps(catalog, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"Generated {len(catalog['releases'])} verified releases in {output}")

if __name__ == "__main__":
    main()
