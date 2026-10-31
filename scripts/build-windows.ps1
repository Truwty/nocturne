$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    python -m venv .venv
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create Python environment' }
    & .\.venv\Scripts\python.exe -m pip install -r bridge/requirements.txt
    if ($LASTEXITCODE -ne 0) { throw 'Cannot install bridge dependencies' }
    & .\.venv\Scripts\python.exe -m unittest discover -s bridge -v
    if ($LASTEXITCODE -ne 0) { throw 'Bridge tests failed' }
    & .\.venv\Scripts\python.exe -m PyInstaller --noconfirm --onefile --console --name nocturne-bridge --collect-all winrt --distpath bridge/dist --workpath bridge/build --specpath bridge bridge/nocturne_bridge.py
    if ($LASTEXITCODE -ne 0) { throw 'Bridge packaging failed' }
    New-Item -ItemType Directory -Force src/main/resources/native | Out-Null
    Copy-Item bridge/dist/nocturne-bridge.exe src/main/resources/native/nocturne-bridge.exe -Force
    & .\gradlew.bat build
    if ($LASTEXITCODE -ne 0) { throw 'Mod build failed' }
} finally {
    Pop-Location
}
