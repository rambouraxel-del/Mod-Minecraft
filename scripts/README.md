# Dedicated worldgen smoke test

`worldgen_smoke.py` starts the configured server twice in the same working
directory. The first boot runs the scenario's generation commands and checks
log markers, then saves and stops. The second boot checks reload markers. This
uses the server console, so it does not need RCON or a client.

Run it from the mod project root after providing a scenario JSON:

```sh
python3 scripts/worldgen_smoke.py --scenario scripts/worldgen-smoke.json
```

The checked-in miniature planet scenario is run with:

```sh
python3 scripts/worldgen_smoke.py --scenario scripts/miniature-planet-smoke.json
```

It uses `run/azerya-smoke-world` as its dedicated test save and removes only
that configured save before a run (`clean_world: true`). Do not reuse that
level name for a personal world.

By default it launches `./gradlew runServer`. Pass `--command` for a deployed
dedicated server, for example `--command 'java -jar fabric-server-launch.jar
nogui'`, and `--cwd` for the server directory. The scenario can configure
environment variables, a nonstandard startup regex, and console commands.
`server_properties_file` can point at the properties file inside a Loom run
directory, while `eula: true` writes the adjacent EULA file for an unattended
test run.
Commands should trigger terrain generation (forceloads, custom diagnostic
commands, etc.) and emit a unique `say`/logger marker that can be matched in
`expect_logs`. Keep generation coordinates and expected probe results in the
scenario file so the test is reproducible for a fixed seed.

Example shape:

```json
{
  "environment": {},
  "ready_regex": "Done \\(.*\\)! For help, type \\\"help\\\"",
  "first_boot": {
    "commands": ["forceload add 0 0", "worldborder get"],
    "expect_logs": ["Azerya spawn region: x=-?\\d+, z=-?\\d+", "border=240000", "240000"]
  },
  "reload": {
    "commands": ["worldborder get"],
    "expect_logs": ["Azerya planet active: seed=42, border=240000", "240000"]
  }
}
```

For actual terrain probes, use the mod's diagnostic command or add a temporary
console command that samples the relevant block/surface and prints a unique
marker. Vanilla console commands alone do not provide a reliable way to read a
generated column's surface height.
