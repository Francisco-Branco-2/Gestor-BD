#!/usr/bin/env python3
"""Testes UI via adb: pastas + exemplares no dispositivo físico.

Funciona em Windows, Linux e macOS. Requer `adb` no PATH ou ANDROID_HOME/SDK.
No Windows: normalmente %LOCALAPPDATA%\\Android\\Sdk\\platform-tools\\adb.exe
"""

from __future__ import annotations

import os
import re
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path


def resolve_adb() -> str:
    found = shutil.which("adb")
    if found:
        return found
    candidates: list[Path] = []
    android_home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if android_home:
        candidates.append(Path(android_home) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb"))
    home = Path.home()
    candidates.extend(
        [
            home / "Android" / "Sdk" / "platform-tools" / "adb",
            home / "AppData" / "Local" / "Android" / "Sdk" / "platform-tools" / "adb.exe",
            Path(os.environ.get("LOCALAPPDATA", "")) / "Android" / "Sdk" / "platform-tools" / "adb.exe",
        ]
    )
    for path in candidates:
        if path and path.is_file():
            return str(path)
    raise SystemExit(
        "adb não encontrado. Instala o Android SDK Platform-Tools ou define ANDROID_HOME."
    )


ADB = resolve_adb()
PKG = "pt.ubi.di.pdm.gestorbd"
OUT = Path(__file__).resolve().parents[1]
results: list[tuple[str, bool, str]] = []


def sh(*args: str, check: bool = True) -> str:
    result = subprocess.run([ADB, *args], capture_output=True, text=True)
    if check and result.returncode != 0:
        raise RuntimeError(result.stderr or result.stdout or "adb failed")
    return result.stdout


def dump() -> ET.Element:
    sh("shell", "uiautomator", "dump", "/sdcard/uidump.xml")
    return ET.fromstring(sh("shell", "cat", "/sdcard/uidump.xml"))


def all_nodes(root: ET.Element) -> list[ET.Element]:
    return list(root.iter("node"))


def texts(root: ET.Element) -> list[str]:
    return [n.attrib.get("text", "") for n in all_nodes(root) if n.attrib.get("text")]


def find_by_id(root: ET.Element, rid: str) -> ET.Element | None:
    for node in all_nodes(root):
        if node.attrib.get("resource-id") == rid:
            return node
    return None


def find_by_desc(root: ET.Element, desc: str) -> ET.Element | None:
    for node in all_nodes(root):
        if node.attrib.get("content-desc") == desc:
            return node
    return None


def find_by_text(root: ET.Element, text: str, clickable_only: bool = False) -> ET.Element | None:
    exact, partial = [], []
    for node in all_nodes(root):
        value = node.attrib.get("text", "")
        if clickable_only and node.attrib.get("clickable") != "true":
            continue
        if value == text:
            exact.append(node)
        elif text in value:
            partial.append(node)
    return (exact or partial or [None])[0]


def center(node: ET.Element) -> tuple[int, int]:
    match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib["bounds"])
    assert match
    x1, y1, x2, y2 = map(int, match.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def tap(node: ET.Element) -> None:
    x, y = center(node)
    sh("shell", "input", "tap", str(x), str(y))
    time.sleep(0.9)


def hide_keyboard() -> None:
    sh("shell", "input", "keyevent", "111")
    time.sleep(0.3)


def scroll_form_down() -> None:
    """Swipe dentro do NestedScrollView (evita acertar no botão Guardar fora do scroll)."""
    root = dump()
    scroll = find_by_id(root, f"{PKG}:id/scroll_form")
    if scroll is None:
        sh("shell", "input", "swipe", "540", "1100", "540", "500", "300", check=False)
    else:
        match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", scroll.attrib["bounds"])
        assert match
        x1, y1, x2, y2 = map(int, match.groups())
        cx = (x1 + x2) // 2
        sh("shell", "input", "swipe", str(cx), str(y2 - 80), str(cx), str(y1 + 80), "400", check=False)
    time.sleep(0.45)


def set_field(rid: str, value: str, hint: str | None = None) -> None:
    hide_keyboard()
    time.sleep(0.2)
    node = None
    for _ in range(6):
        root = dump()
        node = find_by_id(root, rid)
        if node is None and hint:
            node = find_by_text(root, hint)
        if node is not None:
            break
        scroll_form_down()
    if node is None:
        raise RuntimeError(f"Campo em falta: {rid}")
    tap(node)
    time.sleep(0.25)
    for _ in range(50):
        sh("shell", "input", "keyevent", "67")
    sh("shell", "input", "text", value.replace(" ", "%s"))
    time.sleep(0.25)
    hide_keyboard()


def check(name: str, cond: bool, detail: str = "") -> None:
    results.append((name, cond, detail))
    print(("PASS" if cond else "FAIL"), "-", name, detail)


def wake_device() -> None:
    sh("shell", "input", "keyevent", "KEYCODE_WAKEUP", check=False)
    sh("shell", "input", "keyevent", "82", check=False)
    sh("shell", "wm", "dismiss-keyguard", check=False)
    sh("shell", "input", "swipe", "540", "1800", "540", "400", "300", check=False)
    time.sleep(1)


def main() -> None:
    wake_device()
    apk = OUT / "app/build/outputs/apk/debug/app-debug.apk"
    sh("uninstall", PKG, check=False)
    sh("install", "-r", str(apk))
    wake_device()
    sh("shell", "am", "start", "-n", f"{PKG}/.ui.folders.MainActivity")
    time.sleep(2.2)

    root = dump()
    check("Lista de pastas abre", "Gestor de BD" in texts(root))
    check("Estado vazio pastas", any("Ainda não tens coleções" in t for t in texts(root)))

    # Criar pasta — validação
    tap(find_by_id(root, f"{PKG}:id/fab_add"))
    time.sleep(1)
    root = dump()
    check("Ecrã nova coleção", any("Nova coleção" in t for t in texts(root)))
    tap(find_by_id(root, f"{PKG}:id/button_save"))
    time.sleep(1.2)
    root = dump()
    check("Validação pasta", any("Campo obrigatório" in t for t in texts(root)))

    set_field(f"{PKG}:id/edit_name", "Tintin")
    set_field(f"{PKG}:id/edit_authors", "Herge")
    root = dump()
    tap(find_by_id(root, f"{PKG}:id/button_save"))
    time.sleep(1.5)
    root = dump()
    check("Pasta na lista", any("Tintin" in t for t in texts(root)) and any("Herge" in t for t in texts(root)))

    # Abrir pasta — tocar na linha clickable (não só no TextView filho).
    row = None
    for node in all_nodes(root):
        if node.attrib.get("class") == "android.widget.LinearLayout" and node.attrib.get("clickable") == "true":
            child_texts = [c.attrib.get("text", "") for c in node.iter("node")]
            if any("Tintin" == t for t in child_texts):
                row = node
                break
    assert row is not None, "Linha da pasta Tintin não encontrada"
    tap(row)
    time.sleep(1.8)
    root = dump()
    check(
        "Lista de exemplares",
        find_by_id(root, f"{PKG}:id/recycler_comics") is not None,
        str([t for t in texts(root) if t][:10]),
    )
    check(
        "Exemplares vazios",
        any("ainda não tem exemplares" in t.lower() for t in texts(root)),
        str([t for t in texts(root) if t][:10]),
    )
    check("Chip ordenação", find_by_id(root, f"{PKG}:id/chip_sort") is not None)

    # Adicionar exemplar (com certificação)
    fab = find_by_id(root, f"{PKG}:id/fab_add")
    assert fab is not None, f"FAB em falta; textos={texts(root)[:15]}"
    tap(fab)
    time.sleep(1.3)
    root = dump()
    check("Formulário exemplar", any("Novo exemplar" in t for t in texts(root)), str([t for t in texts(root) if t][:12]))
    set_field(f"{PKG}:id/edit_title", "Tintin_no_Tibete")
    set_field(f"{PKG}:id/edit_publisher", "Casterman")
    set_field(f"{PKG}:id/edit_issue", "20")
    set_field(f"{PKG}:id/edit_certification", "CGC_9.8", hint="Certificação")
    hide_keyboard()
    root = dump()
    tap(find_by_id(root, f"{PKG}:id/button_save"))
    time.sleep(1.5)
    root = dump()
    check("Exemplar guardado", any("Tintin_no_Tibete" in t for t in texts(root)))

    # Ordenação
    chip_sort = find_by_id(root, f"{PKG}:id/chip_sort")
    assert chip_sort is not None
    tap(chip_sort)
    time.sleep(0.7)
    root = dump()
    check("Diálogo ordenação", any("Ordenação" in t for t in texts(root)))
    tap(find_by_text(root, "Por grau (maior primeiro)"))
    time.sleep(0.8)
    root = dump()
    check("Ordenação aplicada", any("Por grau" in t for t in texts(root)))

    # Detalhe + certificação + editar
    tap(find_by_text(root, "Tintin_no_Tibete"))
    time.sleep(1)
    root = dump()
    check("Detalhe", "Editar" in texts(root) and "Eliminar" in texts(root))
    check("Certificação no detalhe", any("CGC_9.8" in t for t in texts(root)), str([t for t in texts(root) if t][:15]))
    tap(find_by_text(root, "Editar", clickable_only=True))
    time.sleep(1)
    set_field(f"{PKG}:id/edit_title", "Tintin_EDIT")
    hide_keyboard()
    time.sleep(0.4)
    # Sair pela seta da toolbar (KEYCODE_BACK pode só fechar o teclado no OEM).
    root = dump()
    up = find_by_desc(root, "Navegar para cima")
    if up is None:
        up = find_by_desc(root, "Navigate up")
    assert up is not None, "Seta voltar em falta no formulário"
    tap(up)
    time.sleep(0.9)
    root = dump()
    check("Descartar alterações", any("Descartar" in t for t in texts(root)), str([t for t in texts(root) if t][:12]))
    cancel = find_by_text(root, "Cancelar", clickable_only=True)
    if cancel is not None:
        tap(cancel)
        time.sleep(0.5)
    root = dump()
    tap(find_by_id(root, f"{PKG}:id/button_save"))
    time.sleep(1.3)
    root = dump()
    check("Edição exemplar", any("EDIT" in t for t in texts(root)))
    if "Editar" in texts(root):
        sh("shell", "input", "keyevent", "4")
        time.sleep(0.8)
        root = dump()

    # Stats
    stats = find_by_desc(root, "Estatísticas")
    if stats is None:
        stats = find_by_id(root, f"{PKG}:id/action_stats")
    if stats is not None:
        tap(stats)
        time.sleep(1)
        root = dump()
        check("Stats pasta", any("exemplar" in t for t in texts(root)))
        sh("shell", "input", "keyevent", "4")
        time.sleep(0.8)
        root = dump()
    else:
        check("Stats pasta", False, f"ícone em falta; textos={texts(root)[:12]}")

    # Exportar CSV (abre chooser do sistema)
    more = find_by_desc(root, "Mais opções")
    assert more is not None
    tap(more)
    time.sleep(0.5)
    root = dump()
    export_item = find_by_text(root, "Exportar CSV")
    check("Menu exportar", export_item is not None)
    if export_item is not None:
        tap(export_item)
        time.sleep(1.5)
        root = dump()
        check(
            "Chooser exportação",
            any("Exportar" in t or "CSV" in t or "Partilhar" in t or "Share" in t for t in texts(root))
            or find_by_desc(root, "Fechar") is not None
            or any("Drive" in t or "Gmail" in t or "Bluetooth" in t for t in texts(root)),
            str([t for t in texts(root) if t][:15]),
        )
        sh("shell", "input", "keyevent", "4")
        time.sleep(0.8)
        root = dump()

    # Editar pasta via menu
    more = find_by_desc(root, "Mais opções")
    assert more is not None
    tap(more)
    time.sleep(0.5)
    root = dump()
    tap(find_by_text(root, "Editar coleção"))
    time.sleep(1)
    root = dump()
    check("Editar coleção", any("Editar coleção" in t for t in texts(root)))
    set_field(f"{PKG}:id/edit_authors", "Herge_EDIT")
    root = dump()
    tap(find_by_id(root, f"{PKG}:id/button_save"))
    time.sleep(1.2)
    root = dump()

    # Voltar à lista de pastas e ver autores atualizados
    sh("shell", "input", "keyevent", "4")
    time.sleep(1)
    root = dump()
    check("Autores atualizados", any("Herge_EDIT" in t for t in texts(root)), str([t for t in texts(root) if t][:12]))

    # Reabrir e eliminar pasta
    root = dump()
    row = None
    for node in all_nodes(root):
        if node.attrib.get("class") == "android.widget.LinearLayout" and node.attrib.get("clickable") == "true":
            child_texts = [c.attrib.get("text", "") for c in node.iter("node")]
            if any("Tintin" == t for t in child_texts):
                row = node
                break
    assert row is not None
    tap(row)
    time.sleep(1)
    root = dump()
    tap(find_by_desc(root, "Mais opções"))
    time.sleep(0.5)
    root = dump()
    tap(find_by_text(root, "Eliminar coleção"))
    time.sleep(0.7)
    root = dump()
    check("Confirmação eliminar pasta", any("Eliminar coleção" in t for t in texts(root)))
    buttons = [n for n in all_nodes(root) if n.attrib.get("text") == "Eliminar" and n.attrib.get("clickable") == "true"]
    tap(buttons[-1])
    time.sleep(1.3)
    root = dump()
    check("Pasta eliminada", any("Ainda não tens coleções" in t for t in texts(root)) or not any("Tintin" in t for t in texts(root)))

    # Persistência
    sh("shell", "am", "force-stop", PKG)
    time.sleep(0.4)
    sh("shell", "am", "start", "-n", f"{PKG}/.ui.folders.MainActivity")
    time.sleep(2)
    root = dump()
    check("Persistência (lista vazia)", any("Ainda não tens coleções" in t for t in texts(root)))

    print("\n===== RESUMO =====")
    passed = sum(1 for _, ok, _ in results if ok)
    failed = sum(1 for _, ok, _ in results if not ok)
    for name, ok, detail in results:
        print(f"{'PASS' if ok else 'FAIL'} | {name} | {detail}")
    print(f"Total: {passed} pass, {failed} fail")
    if failed:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
