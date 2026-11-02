"""Verify process-loopback isolation with two owned, quiet sine-wave players."""
import asyncio
import base64
import json
import math
from pathlib import Path
import struct
import subprocess
import tempfile
import wave


def amplitude(samples: tuple[int, ...], frequency: float) -> float:
    real = sum(value / 32768 * math.cos(2 * math.pi * frequency * i / 44100) for i, value in enumerate(samples))
    imaginary = sum(value / 32768 * math.sin(2 * math.pi * frequency * i / 44100) for i, value in enumerate(samples))
    return 2 * math.hypot(real, imaginary) / len(samples)


async def main() -> None:
    processes: list[asyncio.subprocess.Process] = []
    async def request(process: asyncio.subprocess.Process, command: str) -> str:
        assert process.stdin is not None and process.stdout is not None
        process.stdin.write((command + "\n").encode())
        await process.stdin.drain()
        async with asyncio.timeout(10):
            value = await process.stdout.readline()
        if not value:
            raise RuntimeError("Audio fixture ended before replying")
        return value.decode().strip()

    with tempfile.TemporaryDirectory(prefix="nocturne-audio-") as temporary:
        try:
            for frequency, capture in ((440, True), (3200, False)):
                path = Path(temporary) / f"{frequency}.wav"
                with wave.open(str(path), "wb") as output:
                    output.setparams((1, 2, 44100, 0, "NONE", "not compressed"))
                    output.writeframes(b"".join(struct.pack("<h", round(math.sin(2 * math.pi * frequency * i / 44100) * 2000)) for i in range(44100)))
                args = ["powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-File",
                        str(Path("scripts/fixtures/process_audio.ps1").resolve()), "-SourcePath",
                        str(Path("src/main/resources/nocturne/process-audio.cs").resolve()), "-WavePath", str(path)]
                if capture:
                    args.append("-Capture")
                process = await asyncio.create_subprocess_exec(*args, stdin=asyncio.subprocess.PIPE,
                    stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE,
                    creationflags=subprocess.CREATE_NO_WINDOW)
                processes.append(process)
                async with asyncio.timeout(15):
                    ready = await process.stdout.readline()
                if ready.strip() != b"ready":
                    raise RuntimeError("Audio fixture failed to start: " + (await process.stderr.read()).decode())
            own, foreign = processes
            await request(foreign, "play")
            await asyncio.sleep(1)
            silent = json.loads(await request(own, "sample"))
            samples = struct.unpack("<2048h", base64.b64decode(silent["pcm"]))
            assert max(abs(sample) for sample in samples) <= 2, "Foreign application leaked into selected-process capture"
            await request(own, "play")
            await asyncio.sleep(1)
            active = json.loads(await request(own, "sample"))
            samples = struct.unpack("<2048h", base64.b64decode(active["pcm"]))
            own_level, foreign_level = amplitude(samples, 440), amplitude(samples, 3200)
            assert own_level > .02, f"Selected audio missing: {own_level} ({active['status']})"
            assert foreign_level < .002, f"Foreign audio leaked: {foreign_level}"
            await request(own, "stop")
            await asyncio.sleep(.7)
            stopped = json.loads(await request(own, "sample"))
            samples = struct.unpack("<2048h", base64.b64decode(stopped["pcm"]))
            assert max(abs(sample) for sample in samples) <= 2, "Stale audio survived stopping selected playback"
            print(json.dumps({"process_isolation": "passed", "selected_440hz_amplitude": own_level,
                              "excluded_3200hz_amplitude": foreign_level, "stop_clears_signal": True}))
        finally:
            for process in processes:
                if process.returncode is None:
                    try:
                        process.stdin.write(b"quit\n")
                        await process.stdin.drain()
                        await asyncio.wait_for(process.wait(), 3)
                    except (BrokenPipeError, ConnectionResetError, TimeoutError):
                        process.kill()
                        await process.wait()


if __name__ == "__main__":
    asyncio.run(main())
