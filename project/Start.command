#!/bin/bash
# Double-click in Finder. Resolve paths independently of Terminal's working directory.
set -euo pipefail
cd -- "$(dirname -- "$0")"
export PATH="/Library/Frameworks/Python.framework/Versions/Current/bin:/opt/homebrew/bin:/usr/local/bin:$PATH"
fail() {
    local result=$?
    printf '\n启动失败，请查看上方提示。数据库和本地配置会保留。\n'
    if [ -t 0 ]; then read -r -p '按回车键关闭窗口…' ignored || true; fi
    exit "$result"
}
trap fail ERR
if ! command -v python3 >/dev/null 2>&1; then
    printf '未找到 Python 3，请安装 Python 3 后重试。\n'
    false
fi
if selected_jdk=$(/usr/libexec/java_home -v 21 2>/dev/null); then
    export JAVA_HOME="$selected_jdk"
    export PATH="$JAVA_HOME/bin:$PATH"
fi
python3 scripts/launch.py "$@"
