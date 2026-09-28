#!/usr/bin/env python3
"""只读检查当前文档是否仍引用被淘汰的结论或错误运行参数。"""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
DOC_ROOTS = [ROOT / "README.md", ROOT / "docs", ROOT / "scripts" / "README-check-kb.md"]
# docs/local-brain 是跨项目知识图谱的自动蒸馏快照，不是 loan 当前文档真值。
# 其中允许保留其他项目的历史编号和跨仓库来源，因此必须与项目文档门禁隔离。
EXCLUDED_DIRS = {ROOT / "docs" / "local-brain"}
REQUIRED = [
    ROOT / "docs/knowledge-base/README.md",
    ROOT / "docs/knowledge-base/01-角色权限模型.md",
    ROOT / "docs/knowledge-base/06-当前业务规则.md",
    ROOT / "docs/knowledge-base/09-当前业务流程.md",
]
FORBIDDEN = {
    "旧网关端口 8088": re.compile(r"(?<!\d)8088(?!\d)"),
    "旧服务端口 8080": re.compile(r"(?<!\d)8080(?!\d)"),
    "旧前端端口 5173/5174": re.compile(r"(?<!\d)517[34](?!\d)"),
    "已删除计划目录": re.compile(r"docs/plans/"),
    "历史结论编号": re.compile(r"(?<![A-Za-z0-9])[CD]\d{1,3}(?![A-Za-z0-9])"),
    "历史决策台账": re.compile(r"历史结论与决策日志|结论台账"),
}
LINK_RE = re.compile(r"\[[^\]]+\]\((?!https?://|mailto:|#)([^)]+)\)")


def text_files():
    for item in DOC_ROOTS:
        if item.is_file():
            yield item
        elif item.is_dir():
            yield from (
                p for p in item.rglob("*")
                if p.suffix.lower() in {".md", ".html", ".json"}
                and not any(excluded == p or excluded in p.parents for excluded in EXCLUDED_DIRS)
            )


def main():
    errors = []
    for path in REQUIRED:
        if not path.exists():
            errors.append(f"缺少当前文档：{path.relative_to(ROOT)}")
    for path in text_files():
        content = path.read_text(encoding="utf-8", errors="replace")
        rel = path.relative_to(ROOT)
        for label, pattern in FORBIDDEN.items():
            for match in pattern.finditer(content):
                line = content.count("\n", 0, match.start()) + 1
                errors.append(f"{rel}:{line} {label}: {match.group(0)}")
        if path.suffix.lower() == ".md":
            for match in LINK_RE.finditer(content):
                target = match.group(1).split("#", 1)[0].strip().strip("<>")
                if not target:
                    continue
                resolved = (path.parent / target).resolve()
                if not resolved.exists():
                    line = content.count("\n", 0, match.start()) + 1
                    errors.append(f"{rel}:{line} 失效链接: {target}")
    if errors:
        print("文档一致性检查失败：")
        print("\n".join(f"- {item}" for item in errors))
        return 1
    print("文档一致性检查通过：当前端口、知识库入口和链接无冲突。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
