"""Private, line-delimited JSON transport over the parent Minecraft process's pipes."""
from __future__ import annotations

import asyncio
import base64
import json
import math
import queue
import sys
import threading
from datetime import datetime, timezone
from typing import Any

from winrt.windows.media.control import (
    GlobalSystemMediaTransportControlsSessionManager as Manager,
    GlobalSystemMediaTransportControlsSessionPlaybackStatus as Status,
)
from winrt.windows.storage.streams import DataReader

COMMANDS = {"toggle", "next", "previous", "seek", "select", "quit"}


def parse_command(line: str) -> dict[str, Any]:
    if len(line) > 4096:
        raise ValueError("Command too long")
    value = json.loads(line)
    if not isinstance(value, dict) or value.get("action") not in COMMANDS:
        raise ValueError("Unknown command")
    if value["action"] == "seek":
        seconds = value.get("seconds")
        if isinstance(seconds, bool) or not isinstance(seconds, (int, float)):
            raise ValueError("Invalid seek position")
        if not math.isfinite(seconds) or not 0 <= seconds <= 604800:
            raise ValueError("Invalid seek position")
    if value["action"] == "select" and not isinstance(value.get("source"), str):
        raise ValueError("Invalid source")
    return value


def emit(value: dict[str, Any]) -> None:
    print(json.dumps(value, ensure_ascii=True, separators=(",", ":")), flush=True)


def read_commands(commands: queue.Queue[dict[str, Any]]) -> None:
    try:
        for line in sys.stdin:
            try:
                commands.put(parse_command(line), timeout=2)
            except (ValueError, queue.Full) as error:
                print(f"Rejected command: {error}", file=sys.stderr, flush=True)
    finally:
        # Parent closed the pipe; never leave an orphan media process.
        commands.put({"action": "quit"})


async def artwork(media: Any) -> str:
    if media.thumbnail is None:
        return ""
    stream = await media.thumbnail.open_read_async()
    try:
        if not 0 < stream.size <= 2_000_000:
            return ""
        reader = DataReader(stream)
        try:
            size = int(stream.size)
            loaded = await reader.load_async(size)
            if loaded != size:
                return ""
            data = bytearray(size)
            reader.read_bytes(data)
            return base64.b64encode(data).decode("ascii")
        finally:
            reader.detach_stream()
            reader.close()
    finally:
        stream.close()


class Bridge:
    def __init__(self, manager: Any) -> None:
        self.manager = manager
        self.selected = ""
        self.cover_key: tuple[str, str, str, str] | None = None
        self.cover = ""
        self.error = ""

    def session(self) -> Any:
        sessions = list(self.manager.get_sessions())
        if self.selected:
            return next((s for s in sessions if s.source_app_user_model_id == self.selected), None)
        return self.manager.get_current_session()

    async def command(self, command: dict[str, Any]) -> None:
        action = command["action"]
        if action == "select":
            self.selected = command["source"]
            self.error = ""
            return
        session = self.session()
        if session is None:
            self.error = "No media session. Start playback in your music app."
            return
        methods = {
            "toggle": session.try_toggle_play_pause_async,
            "next": session.try_skip_next_async,
            "previous": session.try_skip_previous_async,
        }
        if action == "seek":
            timeline = session.get_timeline_properties()
            seconds = min(command["seconds"], timeline.end_time.total_seconds())
            accepted = await session.try_change_playback_position_async(int(seconds * 10_000_000))
        else:
            accepted = await methods[action]()
        self.error = "" if accepted else "Your music app declined this control."

    async def snapshot(self) -> dict[str, Any]:
        sessions = list(self.manager.get_sessions())
        sources = [s.source_app_user_model_id for s in sessions]
        session = self.session()
        if session is None:
            return {"connected": True, "title": "Nothing playing", "sources": sources,
                    "selected": self.selected, "error": self.error, "art": ""}
        media = await session.try_get_media_properties_async()
        if media is None:
            return {"connected": True, "title": "Waiting for track details", "sources": sources,
                    "selected": self.selected, "error": self.error, "art": ""}
        info = session.get_playback_info()
        timeline = session.get_timeline_properties()
        source = session.source_app_user_model_id
        key = (source, media.title, media.artist, media.album_title)
        if key != self.cover_key:
            self.cover_key = key
            try:
                self.cover = await asyncio.wait_for(artwork(media), 2)
            except (OSError, RuntimeError, TimeoutError) as error:
                self.cover = ""
                print(f"Artwork unavailable: {error}", file=sys.stderr, flush=True)
        playing = info.playback_status == Status.PLAYING
        duration = max(0, timeline.end_time.total_seconds())
        position = timeline.position.total_seconds()
        if playing:
            elapsed = (datetime.now(timezone.utc) - timeline.last_updated_time).total_seconds()
            position += max(0, min(elapsed, duration))
        controls = info.controls
        return {"connected": True, "title": media.title or "Untitled track",
                "artist": media.artist, "album": media.album_title, "source": source,
                "sources": sources, "selected": self.selected, "playing": playing,
                "position": max(0, min(position, duration)), "duration": duration,
                "canToggle": controls.is_play_pause_toggle_enabled,
                "canNext": controls.is_next_enabled, "canPrevious": controls.is_previous_enabled,
                "canSeek": controls.is_playback_position_enabled,
                "art": self.cover, "error": self.error}


async def main() -> None:
    commands: queue.Queue[dict[str, Any]] = queue.Queue(maxsize=32)
    threading.Thread(target=read_commands, args=(commands,), daemon=True).start()
    bridge = Bridge(await asyncio.wait_for(Manager.request_async(), 8))
    while True:
        try:
            for _ in range(32):
                try:
                    command = commands.get_nowait()
                except queue.Empty:
                    break
                if command["action"] == "quit":
                    return
                await asyncio.wait_for(bridge.command(command), 3)
            emit(await asyncio.wait_for(bridge.snapshot(), 4))
        except (OSError, RuntimeError, TimeoutError, ValueError) as error:
            emit({"connected": True, "error": f"Media session: {error}", "title": "Reconnecting"})
        await asyncio.sleep(0.5)


if __name__ == "__main__":
    sys.stdin.reconfigure(encoding="utf-8")
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    try:
        asyncio.run(main())
    except (OSError, RuntimeError) as error:
        emit({"connected": False, "error": str(error), "title": "Media bridge unavailable"})
        sys.exit(1)
