#!/usr/bin/env python3
"""
claude_splitter.py
将 Claude 返回的多文件文本拆成真实文件

用法：
    python claude_splitter.py < claude_output.txt
"""
import os
import re
import sys
from pathlib import Path

# 匹配 // path/to/file.ext\n... 的正则
# 第一组捕获文件名，第二组捕获内容（直到下一个 // 或文件结尾）
FILE_RE = re.compile(
    r'^//\s*(?P<path>\S.*?)\s*\r?\n(?P<content>.*?)(?=^//\s*\S|\Z)',
    re.MULTILINE | re.DOTALL
)

def main():
    text = sys.stdin.read()
    if not text.strip():
        print("stdin 为空，请把 Claude 的输出重定向进来", file=sys.stderr)
        sys.exit(1)

    written = 0
    for match in FILE_RE.finditer(text):
        rel_path = match.group("path").strip()
        content = match.group("content")

        abs_path = Path(rel_path).expanduser()
        abs_path.parent.mkdir(parents=True, exist_ok=True)

        # 去掉末尾多余的换行，保持统一风格
        content = content.rstrip("\r\n") + "\n"
        abs_path.write_text(content, encoding="utf-8")
        print(f"✅  {abs_path}")
        written += 1

    print(f"\n完成！共写入 {written} 个文件。")

if __name__ == "__main__":
    main()