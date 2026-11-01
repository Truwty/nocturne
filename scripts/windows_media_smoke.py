"""Exercise only this script's muted Windows media session, never another player."""
import asyncio
import base64
import json
from datetime import timedelta
from pathlib import Path
import subprocess
import tempfile
import time
import wave

from winrt.windows.foundation import Uri
from winrt.windows.media import MediaPlaybackAutoRepeatMode, MediaPlaybackType, MediaPlaybackStatus as Status
from winrt.windows.media import SystemMediaTransportControlsTimelineProperties, SystemMediaTransportControlsButton as Button
from winrt.windows.media.control import GlobalSystemMediaTransportControlsSessionManager as Manager
from winrt.windows.media.core import MediaSource
from winrt.windows.media.playback import MediaPlayer


async def main() -> None:
    with tempfile.TemporaryDirectory(prefix="nocturne-media-test-") as temporary:
        wav = Path(temporary) / "silence.wav"
        with wave.open(str(wav), "wb") as output:
            output.setparams((1, 2, 8000, 0, "NONE", "not compressed"))
            output.writeframes(bytes(120 * 8000 * 2))
        player = MediaPlayer()
        player.volume = 0
        player.command_manager.is_enabled = False
        player.source = MediaSource.create_from_uri(Uri(wav.as_uri()))
        player.play()
        await asyncio.sleep(1)
        controls = player.system_media_transport_controls
        controls.is_enabled = True
        controls.is_play_enabled = controls.is_pause_enabled = True
        controls.is_next_enabled = controls.is_previous_enabled = True
        updater = controls.display_updater
        updater.type = MediaPlaybackType.MUSIC
        state = {"playing": True, "position": 12000.0, "track": 1, "running": True}

        def publish() -> None:
            updater.music_properties.title = f"Nocturne fixture {state['track']}"
            updater.music_properties.artist = "Nocturne isolated test"
            updater.music_properties.album_title = "Original silence"
            updater.update()

        def pressed(sender, args) -> None:
            if args.button == Button.PLAY:
                state["playing"] = True
            elif args.button == Button.PAUSE:
                state["playing"] = False
            elif args.button == Button.NEXT:
                state["track"] += 1
                publish()
            elif args.button == Button.PREVIOUS:
                state["track"] -= 1
                publish()

        def seek(sender, args) -> None:
            state["position"] = args.requested_playback_position.total_seconds() * 1000

        controls.shuffle_enabled = False
        controls.auto_repeat_mode = MediaPlaybackAutoRepeatMode.NONE
        def shuffle(sender, args) -> None:
            controls.shuffle_enabled = args.requested_shuffle_enabled
        def repeat(sender, args) -> None:
            controls.auto_repeat_mode = args.requested_auto_repeat_mode
        shuffle_token = controls.add_shuffle_enabled_change_requested(shuffle)
        repeat_token = controls.add_auto_repeat_mode_change_requested(repeat)
        button_token = controls.add_button_pressed(pressed)
        seek_token = controls.add_playback_position_change_requested(seek)
        publish()

        async def timeline() -> None:
            previous = time.monotonic()
            while state["running"]:
                now = time.monotonic()
                if state["playing"]:
                    state["position"] += (now - previous) * 1000
                previous = now
                values = SystemMediaTransportControlsTimelineProperties()
                values.start_time = values.min_seek_time = timedelta(0)
                values.end_time = values.max_seek_time = timedelta(seconds=120)
                values.position = timedelta(milliseconds=state["position"])
                controls.update_timeline_properties(values)
                controls.playback_status = Status.PLAYING if state["playing"] else Status.PAUSED
                await asyncio.sleep(0.1)

        ticking = asyncio.create_task(timeline())
        process = None
        try:
            await asyncio.sleep(0.5)
            manager = await Manager.request_async()
            fixture_id = None
            for session in manager.get_sessions():
                properties = await session.try_get_media_properties_async()
                if properties.title == "Nocturne fixture 1" and properties.artist == "Nocturne isolated test":
                    fixture_id = session.source_app_user_model_id
            if fixture_id is None:
                raise RuntimeError("Windows did not publish the isolated fixture session")
            script = Path("src/main/resources/nocturne/media-v2.ps1").read_text(encoding="utf-8")
            encoded = base64.b64encode(script.encode("utf-16le")).decode("ascii")
            process = await asyncio.create_subprocess_exec(
                "powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-EncodedCommand", encoded,
                stdin=asyncio.subprocess.PIPE, stdout=asyncio.subprocess.PIPE, stderr=subprocess.DEVNULL,
            )

            async def command(action: str, **parameters) -> None:
                payload = {"action": action, "target": fixture_id, **parameters}
                process.stdin.write((json.dumps(payload) + "\n").encode("utf-8"))
                await process.stdin.drain()

            async def wait_state(predicate, seconds: float = 12) -> dict:
                async with asyncio.timeout(seconds):
                    while True:
                        line = await process.stdout.readline()
                        if not line:
                            raise RuntimeError("Desktop helper exited")
                        if not line.startswith(b"{"):
                            continue
                        result = json.loads(line)
                        if result.get("error"):
                            raise RuntimeError(result["error"])
                        if predicate(result):
                            return result

            await command("select", app=fixture_id)
            initial = await wait_state(lambda data: data.get("title") == "Nocturne fixture 1")
            if initial.get("artist") != "Nocturne isolated test":
                raise RuntimeError("Refusing to control a session that is not the fixture")
            for capability in ("toggle", "next", "previous", "seek"):
                if not initial.get(capability):
                    raise RuntimeError(f"Fixture did not advertise {capability}")
            await command("toggle")
            await wait_state(lambda data: data.get("playing") is False)
            await command("seek", millis=30000)
            await wait_state(lambda data: abs(data.get("pos", 0) - 30000) < 500)
            await command("next")
            await wait_state(lambda data: data.get("title") == "Nocturne fixture 2")
            await command("previous")
            await wait_state(lambda data: data.get("title") == "Nocturne fixture 1")
            await command("toggle")
            await wait_state(lambda data: data.get("playing") is True)
            await command("priorities", values=[fixture_id, "Spotify"])
            await command("shuffle", millis=1)
            await wait_state(lambda data: data.get("shuffleActive") is True)
            await command("repeat", millis=1)
            await wait_state(lambda data: data.get("repeatMode") == "track")
            await command("repeat", millis=2)
            await wait_state(lambda data: data.get("repeatMode") == "context")
            await command("repeat", millis=0)
            await wait_state(lambda data: data.get("repeatMode") == "off")
            await command("audio", enabled=True)
            await wait_state(lambda data: data.get("audioAvailable") is True)
            await command("select", app="nocturne-test-missing-player")
            await wait_state(lambda data: not data.get("title") and data.get("selected") == "nocturne-test-missing-player")
            await command("select", app=fixture_id)
            await wait_state(lambda data: data.get("title") == "Nocturne fixture 1")
            await command("quit")
            await asyncio.wait_for(process.wait(), 5)
            process = await asyncio.create_subprocess_exec(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-EncodedCommand", encoded,
                stdin=asyncio.subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            )
            process.stdin.close()
            await asyncio.wait_for(process.wait(), 10)
            print("WINDOWS MEDIA TEST PASSED: selection, pause, seek, next, previous, resume, shuffle, repeat modes, priorities, output meter, strict pin, reconnect and parent EOF shutdown")
        finally:
            state["running"] = False
            await ticking
            if process is not None and process.returncode is None:
                process.terminate()
                await process.wait()
            controls.remove_shuffle_enabled_change_requested(shuffle_token)
            controls.remove_auto_repeat_mode_change_requested(repeat_token)
            controls.remove_button_pressed(button_token)
            controls.remove_playback_position_change_requested(seek_token)
            controls.is_enabled = False
            player.pause()
            player.close()


if __name__ == "__main__":
    asyncio.run(main())
