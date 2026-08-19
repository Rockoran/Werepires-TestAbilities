#!/usr/bin/env python3
"""Static completeness checks for WerePires registration and resources."""

from __future__ import annotations

import re
import sys
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOT = ROOT / "src" / "main" / "java"
RESOURCES = ROOT / "src" / "main" / "resources"
MAIN = JAVA_ROOT / "pow" / "crimson2" / "VampireSMPPlugin.java"
BRIGADIER = JAVA_ROOT / "pow" / "crimson2" / "commands" / "BrigadierCommands.java"


def yaml_section_keys(path: Path, section: str) -> list[str]:
    keys: list[str] = []
    active = False
    for line in path.read_text(encoding="utf-8").splitlines():
        if re.fullmatch(rf"{re.escape(section)}:\s*", line):
            active = True
            continue
        if active and re.match(r"^[A-Za-z0-9_.-]+:\s*$", line):
            break
        match = re.match(r"^  ([a-z0-9_.-]+):\s*$", line) if active else None
        if match:
            keys.append(match.group(1))
    return keys


def flattened_yaml_keys(path: Path) -> set[str]:
    result: set[str] = set()
    stack: list[tuple[int, str]] = []
    for original in path.read_text(encoding="utf-8").splitlines():
        if not original.strip() or original.lstrip().startswith(("#", "-")):
            continue
        match = re.match(r"^(\s*)([^:#][^:]*):", original)
        if not match:
            continue
        indent = len(match.group(1).replace("\t", "    "))
        key = match.group(2).strip().strip("'\"")
        while stack and stack[-1][0] >= indent:
            stack.pop()
        path_key = ".".join([item[1] for item in stack] + [key])
        result.add(path_key)
        stack.append((indent, key))
    return result


def fail(errors: list[str], message: str) -> None:
    errors.append(message)


def main() -> int:
    java_files = sorted(JAVA_ROOT.rglob("*.java"))
    sources = {path: path.read_text(encoding="utf-8") for path in java_files}
    corpus = "\n".join(sources.values())
    main_source = sources[MAIN]
    brigadier_source = sources[BRIGADIER]
    errors: list[str] = []

    public_types: dict[str, Path] = {}
    for path, source in sources.items():
        match = re.search(r"\bpublic\s+(?:final\s+|abstract\s+)?(?:class|interface|enum|record)\s+(\w+)", source)
        if match:
            public_types[match.group(1)] = path

    command_executors = {
        name: path
        for name, path in public_types.items()
        if re.search(rf"\bclass\s+{re.escape(name)}\b[^{{]*\bCommandExecutor\b", sources[path], re.S)
    }
    orphan_commands = [name for name in command_executors if len(re.findall(rf"\b{re.escape(name)}\b", corpus)) < 2]
    if orphan_commands:
        fail(errors, "orphan CommandExecutor classes: " + ", ".join(sorted(orphan_commands)))

    listener_types = {
        name: path
        for name, path in public_types.items()
        if re.search(rf"\bclass\s+{re.escape(name)}\b[^{{]*\bListener\b", sources[path], re.S)
    }
    allowed_listener_stubs = {"WerewolfAbilityListener", "ThrallInventoryListener"}
    empty_listeners = {
        name for name, path in listener_types.items() if "@EventHandler" not in sources[path]
    }
    unexpected_empty = empty_listeners - allowed_listener_stubs
    missing_stubs = allowed_listener_stubs - empty_listeners
    if unexpected_empty:
        fail(errors, "listener classes without event handlers: " + ", ".join(sorted(unexpected_empty)))
    if missing_stubs:
        fail(errors, "listener-stub allow-list is stale: " + ", ".join(sorted(missing_stubs)))
    orphan_listeners = [
        name for name in listener_types
        if name not in allowed_listener_stubs and len(re.findall(rf"\b{re.escape(name)}\b", corpus)) < 2
    ]
    if orphan_listeners:
        fail(errors, "orphan listener classes: " + ", ".join(sorted(orphan_listeners)))

    main_fields = {
        type_name.rsplit(".", 1)[-1]: field_name
        for type_name, field_name in re.findall(r"\bprivate\s+(?:final\s+)?([\w.]+)\s+(\w+)\s*;", main_source)
    }
    unregistered_listeners: list[str] = []
    duplicate_listener_paths: list[str] = []
    tome_manager_source = sources[JAVA_ROOT / "pow" / "crimson2" / "managers" / "TomeManager.java"]
    for name, path in listener_types.items():
        if name in allowed_listener_stubs:
            continue
        source = sources[path]
        direct_new = re.search(rf"registerEvents\(\s*new\s+(?:[\w$]+\.)*{re.escape(name)}\s*\(", corpus)
        self_registered = "registerEvents(this" in source
        field = main_fields.get(name)
        field_registered = bool(field and re.search(rf"registerEvents\(\s*this\.{re.escape(field)}\b", main_source))
        local_registered = False
        for var in re.findall(rf"\b{re.escape(name)}\s+(\w+)\s*=\s*new\s+(?:[\w$]+\.)*{re.escape(name)}\s*\(", corpus):
            if re.search(rf"registerEvents\(\s*{re.escape(var)}\b", corpus):
                local_registered = True
                break
        tome_registered = (
            "extends TomeAbility" in source
            and "registerEvents((org.bukkit.event.Listener) ability" in tome_manager_source
        )
        paths = [bool(direct_new), self_registered, field_registered, local_registered, tome_registered]
        if not any(paths):
            unregistered_listeners.append(name)
        if sum(paths) > 1:
            duplicate_listener_paths.append(name)
    if unregistered_listeners:
        fail(errors, "event-handling listeners without a registration path: " + ", ".join(sorted(unregistered_listeners)))
    if duplicate_listener_paths:
        fail(errors, "listeners with multiple registration paths: " + ", ".join(sorted(duplicate_listener_paths)))

    plugin_yml = RESOURCES / "plugin.yml"
    declared_commands = yaml_section_keys(plugin_yml, "commands")
    unmentioned_commands = [name for name in declared_commands if f'"{name}"' not in main_source]
    if unmentioned_commands:
        fail(errors, "plugin.yml commands absent from plugin registration: " + ", ".join(unmentioned_commands))

    declared_permissions = set(yaml_section_keys(plugin_yml, "permissions"))
    used_permissions = set(re.findall(r'hasPermission\("([^"]+)"\)', corpus))
    undeclared_permissions = sorted(used_permissions - declared_permissions)
    if undeclared_permissions:
        fail(errors, "Java permission literals absent from plugin.yml: " + ", ".join(undeclared_permissions))

    # Every concrete ability implementation must be instantiated by its owning manager.
    ability_classes: list[str] = []
    for name, path in public_types.items():
        source = sources[path]
        if re.search(rf"\bclass\s+{re.escape(name)}\s+extends\s+(?:VampireAbility|WerewolfAbility|TomeAbility)\b", source):
            ability_classes.append(name)
    unregistered_abilities = [
        name for name in ability_classes
        if not re.search(rf"\bnew\s+(?:[A-Za-z_$][\w$]*\.)*{re.escape(name)}\s*\(", corpus)
    ]
    if unregistered_abilities:
        fail(errors, "ability classes never instantiated: " + ", ".join(sorted(unregistered_abilities)))

    manager_classes = sorted(name for name in public_types if name.endswith("Manager"))
    uninstantiated_managers = [
        name for name in manager_classes
        if not re.search(rf"\bnew\s+(?:[A-Za-z_$][\w$]*\.)*{re.escape(name)}\s*\(", corpus)
    ]
    if uninstantiated_managers:
        fail(errors, "manager classes never instantiated: " + ", ".join(uninstantiated_managers))

    lifecycle_types: dict[str, tuple[Path, set[str]]] = {}
    for name, path in public_types.items():
        methods = set(re.findall(r"\bpublic\s+void\s+(shutdown|stop)\s*\(", sources[path]))
        if methods:
            lifecycle_types[name] = (path, methods)
    nested_lifecycle_evidence = {
        "ArmorStorageManager": (JAVA_ROOT / "pow" / "crimson2" / "managers" / "BatTransformationManager.java", "armorStorageManager.shutdown()"),
        "PhoneCallService": (JAVA_ROOT / "pow" / "crimson2" / "phone" / "PhoneManager.java", "callService.shutdown()"),
        "PhoneVoicechatPlugin": (JAVA_ROOT / "pow" / "crimson2" / "phone" / "PhoneManager.java", "callService.shutdown()"),
    }
    missing_lifecycle: list[str] = []
    for name, (path, methods) in lifecycle_types.items():
        field = main_fields.get(name)
        direct = False
        if field:
            direct = any(f"this.{field}.{method}()" in main_source for method in methods)
        nested = nested_lifecycle_evidence.get(name)
        nested_ok = bool(nested and nested[1] in sources[nested[0]])
        if not direct and not nested_ok:
            missing_lifecycle.append(name)
    if missing_lifecycle:
        fail(errors, "explicit shutdown/stop hooks without owner cleanup: " + ", ".join(sorted(missing_lifecycle)))

    # These spellings are accepted by handlers and therefore must be reachable in Brigadier.
    required_brigadier_literals = {
        "abilities", "status", "help", "fae", "list", "free",
        "toggle_permadeath", "togglepermadeath", "bloodmoon",
        "clear_stage_cap", "clear_promotion_ban", "listtomevaults",
        "listominouscurevault", "delete", "desecrate", "repair",
    }
    missing_literals = sorted(
        literal for literal in required_brigadier_literals
        if f'"{literal}"' not in brigadier_source
    )
    if missing_literals:
        fail(errors, "handler-supported Brigadier literals missing: " + ", ".join(missing_literals))

    # Report literal plugin-config reads that have no matching config.yml key.
    config_keys = flattened_yaml_keys(RESOURCES / "config.yml")
    config_reads: set[str] = set()
    direct_config_pattern = re.compile(
        r"getConfig\(\)\.get(?:Boolean|Int|Double|Long|String|StringList|ConfigurationSection)"
        r"\(\s*\"([A-Za-z0-9_.-]+)\"\s*(?=[,)])"
    )
    manager_config_pattern = re.compile(
        r"this\.config\.get(?:Boolean|Int|Double|Long|String|StringList|ConfigurationSection)"
        r"\(\s*\"([A-Za-z0-9_.-]+)\"\s*(?=[,)])"
    )
    for source in sources.values():
        config_reads.update(direct_config_pattern.findall(source))
    config_reads.update(manager_config_pattern.findall(sources[JAVA_ROOT / "pow" / "crimson2" / "managers" / "ConfigManager.java"]))
    missing_config = sorted(key for key in config_reads if key not in config_keys)
    if missing_config:
        fail(errors, "literal plugin config reads absent from config.yml: " + ", ".join(missing_config))

    config_manager_path = JAVA_ROOT / "pow" / "crimson2" / "managers" / "ConfigManager.java"
    config_manager_source = sources[config_manager_path]
    config_public_methods = set(re.findall(
        r"\bpublic\s+(?:static\s+)?[\w<>.?]+\s+(\w+)\s*\(", config_manager_source
    ))
    unused_config_methods = sorted(
        name for name in config_public_methods
        if len(re.findall(rf"\b{re.escape(name)}\s*\(", corpus)) < 2
    )
    if unused_config_methods:
        fail(errors, "unused public ConfigManager methods: " + ", ".join(unused_config_methods))

    counts = Counter(path.relative_to(JAVA_ROOT).parts[2] if len(path.relative_to(JAVA_ROOT).parts) > 2 else "root" for path in java_files)
    print(f"Java files: {len(java_files)}")
    print(f"Public types: {len(public_types)}")
    print(f"CommandExecutor classes: {len(command_executors)}")
    print(f"Bukkit commands: {len(declared_commands)}")
    print(f"Listener-capable classes: {len(listener_types)} ({len(empty_listeners)} intentional stubs)")
    print(f"Concrete ability classes: {len(ability_classes)}")
    print(f"Manager classes: {len(manager_classes)}")
    print(f"Explicit lifecycle classes: {len(lifecycle_types)}")
    print(f"Declared permissions: {len(declared_permissions)}; used Java literals: {len(used_permissions)}")
    print(f"Literal plugin config reads: {len(config_reads)}; config paths: {len(config_keys)}")
    print("Top-level package counts: " + ", ".join(f"{key}={counts[key]}" for key in sorted(counts)))

    if errors:
        print("\nAUDIT FAILED", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print("AUDIT PASSED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
