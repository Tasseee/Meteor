#!/usr/bin/env python3
"""
Nightfall Lyrics local sidecar.

Windows: reads the active Windows System Media Transport Controls (SMTC) session
through PyWinRT. Linux: reads an MPRIS session through the playerctl executable.
Only track title/artist/album/duration are sent to LRCLIB for synchronized lyrics.
The WebSocket server listens on loopback only (127.0.0.1).
"""
from __future__ import annotations

import asyncio
import json
import logging
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any

try:
    import websockets
except ImportError:
    print("Missing dependency: websockets. Install with: python -m pip install -r requirements.txt", file=sys.stderr)
    raise

HOST = "127.0.0.1"
PORT = 8765
POLL_SECONDS = 0.35
LRCLIB = "https://lrclib.net"
USER_AGENT = "Nightfall/2.0 (https://github.com/Tasseee/Meteor)"
TIMESTAMP_RE = re.compile(r"\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\]")
CLIENTS: set[Any] = set()
logging.basicConfig(level=logging.INFO, format="[Nightfall Lyrics] %(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("nightfall-lyrics")


@dataclass(frozen=True)
class Track:
    title: str
    artist: str
    album: str
    duration: float
    position: float
    playing: bool

    @property
    def key(self) -> tuple[str, str, str]:
        return (self.title.strip().casefold(), self.artist.strip().casefold(), self.album.strip().casefold())


def sendable(value: Any) -> str:
    return str(value or "").strip()


def run_playerctl(*args: str) -> str:
    result = subprocess.run(
        ["playerctl", *args],
        capture_output=True,
        text=True,
        timeout=2.0,
        check=False,
    )
    if result.returncode != 0:
        return ""
    return result.stdout.strip()


def linux_snapshot() -> Track | None:
    if shutil.which("playerctl") is None:
        raise RuntimeError("playerctl fehlt. Installiere playerctl und starte Musik mit einem MPRIS-kompatiblen Player.")

    # Prefer Spotify's own MPRIS session when the desktop app is running.
    # Otherwise use playerctl's default session (e.g. browser, VLC, other players).
    players = run_playerctl("-l").splitlines()
    spotify_player = next((name for name in players if "spotify" in name.casefold()), "")

    def player_args(*args: str) -> tuple[str, ...]:
        return ("-p", spotify_player, *args) if spotify_player else args

    metadata = run_playerctl(
        *player_args(
            "metadata",
            "--format",
            "{{title}}\\n{{artist}}\\n{{album}}\\n{{mpris:length}}",
        )
    )
    if not metadata and spotify_player:
        # Spotify is installed but idle: fall back to the active/default player.
        spotify_player = ""
        metadata = run_playerctl(
            "metadata",
            "--format",
            "{{title}}\\n{{artist}}\\n{{album}}\\n{{mpris:length}}",
        )
    if not metadata:
        return None
    parts = metadata.splitlines()
    if len(parts) < 2:
        return None
    title = sendable(parts[0])
    artist = sendable(parts[1])
    album = sendable(parts[2]) if len(parts) > 2 else ""
    duration_us = 0
    if len(parts) > 3:
        try:
            duration_us = int(parts[3])
        except (TypeError, ValueError):
            duration_us = 0
    try:
        position = float(run_playerctl(*player_args("position")) or "0")
    except ValueError:
        position = 0.0
    status = run_playerctl(*player_args("status")).casefold()
    if not title:
        return None
    return Track(title, artist, album, duration_us / 1_000_000.0, position, status == "playing")

class WindowsMediaReader:
    def __init__(self) -> None:
        self.manager = None
        self.previous_key: tuple[str, str, str] | None = None
        self.previous_raw_position = 0.0
        self.estimated_position = 0.0
        self.previous_tick = time.monotonic()

    async def snapshot(self) -> Track | None:
        try:
            from winrt.windows.media.control import (
                GlobalSystemMediaTransportControlsSessionManager,
            )
        except ImportError as exc:
            raise RuntimeError(
                "Windows-Mediensteuerung fehlt. Installiere requirements-windows.txt "
                "mit Python 3.11 oder neuer."
            ) from exc

        if self.manager is None:
            self.manager = await GlobalSystemMediaTransportControlsSessionManager.request_async()
        session = self.manager.get_current_session()
        if session is None:
            self.previous_key = None
            return None

        props = await session.try_get_media_properties_async()
        if props is None:
            return None
        title = sendable(props.title)
        artist = sendable(props.artist)
        album = sendable(props.album_title)
        if not title:
            return None

        timeline = session.get_timeline_properties()
        playback = session.get_playback_info()
        raw_position = max(0.0, timeline.position.total_seconds())
        duration = max(0.0, timeline.end_time.total_seconds())
        status_name = str(playback.playback_status).casefold()
        playing = "playing" in status_name

        key = (title.casefold(), artist.casefold(), album.casefold())
        now = time.monotonic()
        elapsed = max(0.0, min(2.0, now - self.previous_tick))
        if key != self.previous_key:
            self.estimated_position = raw_position
            self.previous_raw_position = raw_position
        elif playing:
            # Windows may update the session timeline less frequently than our poll rate.
            # Extrapolate between reported positions, but snap to real seeks/position updates.
            if abs(raw_position - self.previous_raw_position) > 0.08:
                self.estimated_position = raw_position
                self.previous_raw_position = raw_position
            else:
                self.estimated_position += elapsed
        else:
            self.estimated_position = raw_position
            self.previous_raw_position = raw_position

        self.previous_key = key
        self.previous_tick = now
        return Track(title, artist, album, duration, self.estimated_position, playing)


def fetch_json(url: str) -> Any:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        if exc.code == 429:
            retry_after = exc.headers.get("Retry-After", "2")
            try:
                delay = max(1.0, min(30.0, float(retry_after)))
            except ValueError:
                delay = 2.0
            log.warning("LRCLIB rate limit; waiting %.1fs before one retry", delay)
            time.sleep(delay)
            with urllib.request.urlopen(request, timeout=10) as response:
                return json.loads(response.read().decode("utf-8"))
        if exc.code == 404:
            return None
        raise


def parse_lrc(raw: str) -> list[tuple[float, str]]:
    rows: list[tuple[float, str]] = []
    for raw_line in raw.splitlines():
        matches = list(TIMESTAMP_RE.finditer(raw_line))
        if not matches:
            continue
        text = TIMESTAMP_RE.sub("", raw_line).strip()
        if not text:
            continue
        for match in matches:
            minutes = int(match.group(1))
            seconds = int(match.group(2))
            fraction = match.group(3) or "0"
            if len(fraction) == 1:
                millis = int(fraction) * 100
            elif len(fraction) == 2:
                millis = int(fraction) * 10
            else:
                millis = int(fraction[:3])
            rows.append((minutes * 60 + seconds + millis / 1000.0, text))
    rows.sort(key=lambda row: row[0])
    # Drop exact duplicate timestamp/text pairs without disturbing repeated choruses.
    deduped: list[tuple[float, str]] = []
    for row in rows:
        if not deduped or row != deduped[-1]:
            deduped.append(row)
    return deduped


def fetch_synced_lyrics(track: Track) -> list[tuple[float, str]]:
    if not track.title or not track.artist:
        return []
    time.sleep(0.35)
    query = {
        "track_name": track.title,
        "artist_name": track.artist,
    }
    if track.album:
        query["album_name"] = track.album
    if 1 <= int(track.duration) <= 3600:
        query["duration"] = str(int(track.duration))
    url = LRCLIB + "/api/get?" + urllib.parse.urlencode(query)
    record = fetch_json(url)

    if not record:
        # The fallback search is only made once after an exact-signature miss.
        time.sleep(0.35)
        search_query = urllib.parse.urlencode({
            "track_name": track.title,
            "artist_name": track.artist,
        })
        results = fetch_json(LRCLIB + "/api/search?" + search_query) or []
        if isinstance(results, list):
            candidates = [item for item in results if item.get("syncedLyrics")]
            if candidates:
                candidates.sort(key=lambda item: abs(float(item.get("duration") or 0) - track.duration))
                record = candidates[0]

    if not isinstance(record, dict):
        return []
    synced = record.get("syncedLyrics")
    if not isinstance(synced, str) or not synced.strip():
        return []
    return parse_lrc(synced)


async def broadcast(payload: dict[str, Any]) -> None:
    if not CLIENTS:
        return
    message = json.dumps(payload, ensure_ascii=False)
    stale = []
    for client in tuple(CLIENTS):
        try:
            await client.send(message)
        except Exception:
            stale.append(client)
    for client in stale:
        CLIENTS.discard(client)


async def websocket_handler(websocket: Any) -> None:
    CLIENTS.add(websocket)
    log.info("Minecraft connected from %s", getattr(websocket, "remote_address", "localhost"))
    try:
        await websocket.send(json.dumps({"type": "CONNECTED", "data": {}}))
        await websocket.wait_closed()
    finally:
        CLIENTS.discard(websocket)
        log.info("Minecraft disconnected")


async def poll_media() -> None:
    windows_reader = WindowsMediaReader() if os.name == "nt" else None
    current_key: tuple[str, str, str] | None = None
    current_lines: list[tuple[float, str]] = []
    current_index = -1
    previous_playing: bool | None = None
    status_sent = ""
    last_retry_at = 0.0
    platform_error_sent = False

    while True:
        try:
            track = await windows_reader.snapshot() if windows_reader else linux_snapshot()
            if track is None:
                if current_key is not None:
                    current_key = None
                    current_lines = []
                    current_index = -1
                    await broadcast({"type": "LYRIC_CLEAR", "data": {}})
                await asyncio.sleep(POLL_SECONDS)
                continue

            if track.key != current_key:
                current_key = track.key
                current_lines = []
                current_index = -1
                previous_playing = None
                await broadcast({"type": "LYRIC_CLEAR", "data": {}})
                await broadcast({
                    "type": "TRACK",
                    "data": {
                        "title": track.title,
                        "artist": track.artist,
                        "album": track.album,
                        "duration": track.duration,
                    },
                })
                log.info("Now playing: %s — %s", track.artist, track.title)
                try:
                    current_lines = await asyncio.to_thread(fetch_synced_lyrics, track)
                except Exception as exc:
                    log.warning("LRCLIB lookup failed: %s", exc)
                    current_lines = []
                if not current_lines:
                    status_sent = "Keine synchronisierten Lyrics bei LRCLIB gefunden"
                    await broadcast({"type": "STATUS", "data": {"message": status_sent}})
                else:
                    status_sent = ""

            if track.playing != previous_playing:
                previous_playing = track.playing
                await broadcast({
                    "type": "PLAYBACK_STATE",
                    "data": {"playing": track.playing},
                })

            if current_lines and track.playing:
                # Keep a little margin for delayed media-position updates.
                import bisect
                index = bisect.bisect_right([row[0] for row in current_lines], max(0.0, track.position)) - 1
                if index >= 0 and index != current_index:
                    current_index = index
                    start, text = current_lines[index]
                    if index + 1 < len(current_lines):
                        gap_ms = int((current_lines[index + 1][0] - max(start, track.position)) * 1000)
                        duration_ms = max(900, min(12_000, gap_ms))
                    else:
                        duration_ms = 6_000
                    await broadcast({
                        "type": "LYRIC_UPDATE",
                        "data": {
                            "text": text,
                            "duration_ms": duration_ms,
                            "position": track.position,
                        },
                    })
            await asyncio.sleep(POLL_SECONDS)

        except asyncio.CancelledError:
            raise
        except Exception as exc:
            message = str(exc)
            if message != status_sent:
                status_sent = message
                log.warning("%s", message)
                await broadcast({"type": "STATUS", "data": {"message": message}})
            await asyncio.sleep(2.0)


async def main() -> None:
    log.info("Starting loopback-only Lyrics service on ws://%s:%d", HOST, PORT)
    log.info("No audio is captured. Track metadata is sent to LRCLIB only when the track changes.")
    async with websockets.serve(websocket_handler, HOST, PORT, max_size=16_384, ping_interval=20):
        await poll_media()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        log.info("Stopped")
