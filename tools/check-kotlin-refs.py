#!/usr/bin/env python3
"""
Sucht Bezeichner, die verwendet, aber weder importiert noch im selben Paket
deklariert sind.

Warum es das gibt: Der Kotlin-Compiler findet das natuerlich auch - aber nur
in der CI, und ein Durchlauf dauert acht Minuten. Wer keine Android-SDK zur
Hand hat, faellt sonst bei jedem vergessenen Import auf diese acht Minuten
zurueck. Genau so ist ein Release schon einmal an einem einzigen entfernten
Import gescheitert.

Das ist ausdruecklich KEIN Ersatz fuer den Compiler. Die Heuristik kennt
weder Typinferenz noch Erweiterungsfunktionen und sieht nur Namen, die mit
einem Grossbuchstaben beginnen und auf die '.', '(' oder '<' folgt. Sie
faengt den haeufigsten Fall ab - einen fehlenden Import - und sonst nichts.

    python3 tools/check-kotlin-refs.py
"""
from __future__ import annotations

import re
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "android/app/src/main/java"

# Namen aus der Standardbibliothek und den Annotationen, die nie importiert
# werden muessen. Die Liste ist bewusst grosszuegig: ein falsch gemeldeter
# Treffer kostet Aufmerksamkeit, und ein Pruefwerkzeug, dem man nicht glaubt,
# wird nicht benutzt.
BUILTINS = {
    "String", "Int", "Long", "Boolean", "Unit", "List", "Map", "Set", "Char",
    "Byte", "Short", "Double", "Float", "Any", "Nothing", "Pair", "Triple",
    "Array", "ByteArray", "CharArray", "IntArray", "ArrayList", "MutableList",
    "MutableMap", "MutableSet", "LinkedHashMap", "LinkedHashSet", "HashMap",
    "HashSet", "Regex", "StringBuilder", "CharSequence", "Comparator",
    "Iterable", "Sequence", "Result", "Exception", "Throwable", "Error",
    "IllegalArgumentException", "IllegalStateException", "SecurityException",
    "NumberFormatException", "Math", "System", "Character", "Charsets",
    "Volatile", "Suppress", "OptIn", "JvmStatic", "JvmField", "Synchronized",
    "Deprecated", "Throws", "R", "BuildConfig",
}


def strip_noise(text: str) -> str:
    """Kommentare und Zeichenketten raus - dort steht deutscher Fliesstext."""
    text = re.sub(r'"""(?:.|\n)*?"""', '""', text)
    text = re.sub(r"/\*(?:.|\n)*?\*/", "", text)
    text = re.sub(r"//.*$", "", text, flags=re.M)
    text = re.sub(r'"(?:\\.|[^"\\])*"', '""', text)
    return text


def declarations(text: str) -> set[str]:
    names = set(re.findall(r"\b(?:class|interface|object)\s+(\w+)", text))
    names |= set(re.findall(r"\bfun\s+(?:<[^>]+>\s+)?(?:\w+\.)?(\w+)\s*[(<]", text))
    names |= set(re.findall(r"\b(?:val|var)\s+(\w+)", text))
    return names


def main() -> int:
    files = sorted(ROOT.rglob("*.kt"))
    if not files:
        print(f"Keine Kotlin-Dateien unter {ROOT}", file=sys.stderr)
        return 2

    # Was im selben Paket liegt, braucht keinen Import.
    per_package: dict[str, set[str]] = defaultdict(set)
    stripped: dict[Path, str] = {}
    packages: dict[Path, str] = {}

    for path in files:
        raw = path.read_text()
        text = strip_noise(raw)
        stripped[path] = text
        match = re.search(r"^package (.+)$", text, re.M)
        package = match.group(1).strip() if match else ""
        packages[path] = package
        per_package[package] |= declarations(text)

    problems: list[tuple[Path, list[str]]] = []
    for path in files:
        raw = path.read_text()
        imported = {i.split(".")[-1] for i in re.findall(r"^import ([\w.]+)$", raw, re.M)}
        imported |= set(re.findall(r"^import [\w.]+ as (\w+)$", raw, re.M))

        body = re.sub(r"^(?:import|package) .+$", "", stripped[path], flags=re.M)
        # Voll qualifizierte Verwendungen brauchen keinen Import.
        body = re.sub(r"\b(?:androidx|android|java|javax|kotlin|kotlinx|com|cc)\.[\w.]+", "", body)
        used = set(re.findall(r"(?<![\w.@])([A-Z]\w+)\s*[.(<]", body))

        known = imported | per_package[packages[path]] | BUILTINS
        missing = sorted(name for name in used - known if not name.isupper())
        if missing:
            problems.append((path, missing))

    for path, missing in problems:
        print(f"{path.relative_to(ROOT)}: {', '.join(missing)}")

    if problems:
        print(f"\n{len(problems)} Datei(en) mit offenen Referenzen.")
        return 1

    print(f"{len(files)} Dateien geprueft, keine offenen Referenzen.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
