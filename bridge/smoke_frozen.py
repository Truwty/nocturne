"""Check a frozen bridge, including pipe shutdown, without controlling any player."""
import json
import subprocess
import sys


def main() -> None:
    process = subprocess.Popen([sys.argv[1]], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE, text=True, encoding="utf-8")
    try:
        # Send quit after enough time to observe startup; a bounded communicate prevents CI hangs.
        import threading
        timer = threading.Timer(12, process.kill)
        timer.start()
        try:
            assert process.stdout is not None
            state = json.loads(process.stdout.readline())
            if not state.get("connected"):
                raise RuntimeError(state.get("error", "Bridge did not connect"))
            process.communicate('{"action":"quit"}\n', timeout=5)
            if process.returncode != 0:
                raise RuntimeError(f"Bridge exited with {process.returncode}")
            print("Frozen bridge connected and shut down cleanly")
        finally:
            timer.cancel()
    finally:
        if process.poll() is None:
            process.kill()
            process.wait()


if __name__ == "__main__":
    main()
