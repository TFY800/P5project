#!/bin/bash
# Stop only application processes recorded and verified by dev.py; keep PostgreSQL/data intact.
set -euo pipefail
cd -- "$(dirname -- "$0")"
export PATH="/Library/Frameworks/Python.framework/Versions/Current/bin:/opt/homebrew/bin:/usr/local/bin:$PATH"
fail() {
    local result=$?
    printf '\n停止未完成，请查看上方提示。\n'
    if [ -t 0 ]; then read -r -p '按回车键关闭窗口…' ignored || true; fi
    exit "$result"
}
trap fail ERR
python3 scripts/dev.py stop
printf '\n应用已停止，数据库仍保留运行。再次双击 Start.command 即可启动。\n'
