"""Exercise the installed MMGG panel on an unlocked test emulator with overlay permission.

Run: python diagnostics/bigbang/test_toolbar.py emulator-5554
Use an emulator without an active MMGG Root node reader (uiautomator needs that slot).
"""

import argparse
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET


PACKAGE = "com.leaf.hyperdragshare.codex"


def adb(serial, *args):
    return subprocess.check_output(["adb", "-s", serial, *args], timeout=30)


def wait_for(serial, label):
    for _ in range(4):
        output = adb(serial, "exec-out", "uiautomator", "dump", "/dev/tty")
        if b"null root node" in output:
            time.sleep(0.5)
            continue
        start = output.index(b"<?xml")
        end = output.index(b"</hierarchy>") + len(b"</hierarchy>")
        tree = ET.fromstring(output[start:end])
        if find(tree, label) is not None:
            return tree
    raise AssertionError(f"Panel did not show {label!r}")


def find(tree, label):
    return next((node for node in tree.iter("node") if node.get("package") == PACKAGE
                 and label in (node.get("text"), node.get("content-desc"))), None)


def button_bounds(tree, label):
    node = find(tree, label)
    assert node is not None, f"Missing {label!r}"
    parents = {child: parent for parent in tree.iter("node") for child in parent}
    # Compose may expose the label as a child of its clickable container.
    while node.get("clickable") != "true":
        assert node in parents, f"No click action for {label!r}"
        node = parents[node]
    assert node.get("enabled") == "true", f"Disabled {label!r}"
    return tuple(map(int, re.findall(r"-?\d+", node.attrib["bounds"])))


def tap(serial, rect):
    left, top, right, bottom = rect
    adb(serial, "shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def toolbar(nodes, selected, entity):
    target = "选中内容" if selected else "全文"
    labels = ["复制" + target, "搜索" + target, entity or "用词典查询" + target, "更多操作"]
    controls = [button_bounds(nodes, label) for label in labels]
    left, top, right, bottom = controls[0]
    assert top < bottom and left < right, controls
    for next_left, next_top, next_right, next_bottom in controls[1:]:
        assert right <= next_left < next_right and (top, bottom) == (next_top, next_bottom), controls
        right = next_right
    return controls


def exercise(serial, text, entity=None):
    adb(serial, "shell", f"am start -n {PACKAGE}/.TextSegmentationActivity "
        "-a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "
        + shlex.quote(text))
    ready = wait_for(serial, "共 " + str(len(text)) + " 字符")
    original = toolbar(ready, False, entity)
    tap(serial, button_bounds(ready, "全选"))
    selected = wait_for(serial, "取消全选")
    assert toolbar(selected, True, entity) == original, "Touch targets moved on selection"

    # Tap the original fourth slot again after the selection changed.
    tap(serial, original[3])
    wait_for(serial, "翻译")
    adb(serial, "shell", "input", "keyevent", "4")
    restored = wait_for(serial, "取消全选")
    assert toolbar(restored, True, entity) == original, "Touch targets moved after Back"
    tap(serial, button_bounds(restored, "取消全选"))
    cleared = wait_for(serial, "全选")
    assert toolbar(cleared, False, entity) == original, "Touch targets moved on deselection"
    adb(serial, "shell", "input", "keyevent", "4")
    print(f"Passed {entity or '词典'} at {original}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial", help="ADB serial of the test emulator, e.g. emulator-5554")
    serial = parser.parse_args().serial
    if not serial.startswith("emulator-"):
        parser.error("Specify a test emulator")
    exercise(serial, "周六上午十点在城市图书馆集合，记得带上水杯。")
    exercise(serial, "https://example.com", "打开链接")
