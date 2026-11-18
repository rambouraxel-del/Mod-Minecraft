#!/usr/bin/env python3
"""Run a repeatable two-boot dedicated-server worldgen smoke scenario.

The scenario JSON supplies console commands and log markers. This keeps the
runner independent of a particular Minecraft/Fabric command API while still
exercising real chunk generation and the saved world on both boots.
"""

from __future__ import annotations

import argparse
import json
import os
import queue
import re
import shutil
import shlex
import subprocess
import sys
import threading
import time
from pathlib import Path


READY_RE = re.compile(r"Done \(.*\)! For help, type \"help\"")


class ServerRun:
    def __init__(self, command: list[str], cwd: Path, env: dict[str, str]):
        self.process = subprocess.Popen(
            command, cwd=cwd, env=env, stdin=subprocess.PIPE,
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
            bufsize=1,
        )
        self.lines: queue.Queue[str | None] = queue.Queue()
        self.history: list[str] = []
        self.reader = threading.Thread(target=self._read, daemon=True)
        self.reader.start()

    def _read(self) -> None:
        assert self.process.stdout
        for line in self.process.stdout:
            line = line.rstrip("\n")
            self.history.append(line)
            print(line, flush=True)
            self.lines.put(line)
        self.lines.put(None)

    def wait_for(self, patterns: list[str], timeout: float, label: str) -> None:
        compiled = [re.compile(pattern) for pattern in patterns]
        found = [False] * len(compiled)
        deadline = time.monotonic() + timeout
        # Include prior output, useful if a stage marker appeared early.
        for line in self.history:
            for i, pattern in enumerate(compiled):
                found[i] |= bool(pattern.search(line))
        while not all(found):
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                missing = [patterns[i] for i, ok in enumerate(found) if not ok]
                raise RuntimeError(f"Timed out during {label}; missing log marker(s): {missing}")
            try:
                line = self.lines.get(timeout=min(remaining, 0.5))
            except queue.Empty:
                if self.process.poll() is not None:
                    raise RuntimeError(f"Server exited during {label} (code {self.process.returncode})")
                continue
            if line is None:
                raise RuntimeError(f"Server closed its output during {label}")
            for i, pattern in enumerate(compiled):
                found[i] |= bool(pattern.search(line))

    def send(self, command: str) -> None:
        if self.process.poll() is not None:
            raise RuntimeError("Cannot send a command: server process has exited")
        assert self.process.stdin
        print(f"> {command}", flush=True)
        self.process.stdin.write(command + "\n")
        self.process.stdin.flush()

    def stop(self, timeout: float) -> None:
        if self.process.poll() is None:
            self.send("stop")
        try:
            code = self.process.wait(timeout=timeout)
        except subprocess.TimeoutExpired as exc:
            self.process.terminate()
            try:
                self.process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.process.kill()
            raise RuntimeError("Server did not stop cleanly") from exc
        if code != 0:
            raise RuntimeError(f"Server exited with status {code}")


def stage(server: ServerRun, spec: dict, timeout: float, label: str) -> None:
    for command in spec.get("commands", []):
        server.send(command)
    markers = spec.get("expect_logs", [])
    if markers:
        server.wait_for(markers, timeout, label)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scenario", type=Path, required=True, help="JSON scenario file")
    parser.add_argument("--cwd", type=Path, default=Path.cwd(), help="server working directory")
    parser.add_argument("--command", default="./gradlew runServer", help="launch command, parsed with shell quoting")
    parser.add_argument("--timeout", type=float, default=240, help="seconds to wait for startup and each stage")
    args = parser.parse_args()

    scenario = json.loads(args.scenario.read_text())
    command = shlex.split(args.command)
    cwd = args.cwd.resolve()
    env = os.environ.copy()
    env.update(scenario.get("environment", {}))
    ready = scenario.get("ready_regex", READY_RE.pattern)

    # Explicitly provision the dedicated-server settings used to create the
    # world. This is particularly important for custom `level-type` presets:
    # changing it after the first boot cannot retrofit an existing save.
    settings = scenario.get("server_properties", {})
    if settings:
        properties_file = cwd / scenario.get("server_properties_file", "server.properties")
        properties_file.parent.mkdir(parents=True, exist_ok=True)
        if scenario.get("eula", False):
            eula_file = cwd / scenario.get("eula_file", str(properties_file.parent / "eula.txt"))
            eula_file.parent.mkdir(parents=True, exist_ok=True)
            eula_file.write_text("eula=true\n")
        existing = {}
        if properties_file.exists():
            for line in properties_file.read_text().splitlines():
                if line.strip() and not line.lstrip().startswith("#") and "=" in line:
                    key, value = line.split("=", 1)
                    existing[key.strip()] = value.strip()
        existing.update({str(k): str(v) for k, v in settings.items()})
        properties_file.write_text("".join(f"{key}={value}\n" for key, value in existing.items()))
        if scenario.get("clean_world", False):
            world_name = settings.get("level-name", "world")
            if Path(world_name).name != world_name or world_name in ("", ".", ".."):
                raise RuntimeError("clean_world requires a simple level-name without path components")
            world_path = properties_file.parent / world_name
            if world_path.exists():
                print(f"Removing configured smoke-test save: {world_path}", flush=True)
                shutil.rmtree(world_path)

    server = ServerRun(command, cwd, env)
    try:
        server.wait_for([ready], args.timeout, "first server startup")
        stage(server, scenario.get("first_boot", {}), args.timeout, "first boot checks")
        server.stop(args.timeout)

        server = ServerRun(command, cwd, env)
        server.wait_for([ready], args.timeout, "server reload")
        stage(server, scenario.get("reload", {}), args.timeout, "reload checks")
        server.stop(args.timeout)
    except Exception as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        if server.process.poll() is None:
            server.process.terminate()
        return 1

    print("PASS: dedicated server generated, saved, and reloaded the configured world")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
