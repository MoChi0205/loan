#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-kb-consistency.py —— 知识库 / 代码一致性自动校验脚本
=========================================================

用途
----
本轮知识治理中，人工 grep 才发现三处严重「文档与代码脱节」（表数 65/66 vs 实际 68、
渠道角色 tab 数 3 vs 实际 4、文档引用不存在的 output/ 目录）。为避免再次依赖人肉核对，
本脚本把「代码即唯一真值」的 5 项一致性断言固化下来，可日常执行，也可接入 CI。

五项检查
--------
  检查 1  表数一致性      真值 = db/loan-db-schema.sql 的 CREATE TABLE 数
                          扫描 docs/ 下 .md/.html 中的「N 表」表述，报告不符者
  检查 2  角色 tabBar 数  真值 = loan-mini/components/TabBar.vue 中
                          store.isChannel 分支 / 默认分支返回的 tab 数组长度
                          对比 docs/knowledge-base/01、08 等文档描述
  检查 3  业务包实现状态  统计 loan-service 各业务包 .java 文件数；对空包区分
                          「真空包（无实现）」与「⚠️ 包名与实现位置不一致」
  检查 4  文档指针死链    扫描 docs/knowledge-base/*.md、docs/skills/**/SKILL.md、
                          .workbuddy/skills/**/SKILL.md 中的相对路径引用并逐个验证
  检查 5  接口契约一致性  docs/knowledge-base/04-后端 API 契约.md 的接口路径
                          vs loan-service Controller 的 @*Mapping 实际路径

用法
----
    python3 scripts/check-kb-consistency.py            # 全量检查
    python3 scripts/check-kb-consistency.py --only 1,4 # 只跑指定检查项
    python3 scripts/check-kb-consistency.py --quiet    # 只输出汇总

可从任意工作目录运行（脚本内部用自身位置推导仓库根）。

退出码
------
    0  全部通过（无 FAIL）
    1  存在至少一项 FAIL（便于 CI / git pre-commit 拦截）

约定
----
- 只读脚本：不修改任何被检查的文件。
- 统一 utf-8 读取，遇到非法字节降级替换，绝不因编码问题崩溃。
- 某项检查所依赖的源文件缺失时，该项标记 SKIP 并提示，不影响其它检查。

@author loan-platform / 知识治理
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from dataclasses import dataclass, field
from typing import Dict, Iterable, List, Optional, Sequence, Set, Tuple

# --------------------------------------------------------------------------- #
# 常量与路径
# --------------------------------------------------------------------------- #

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(SCRIPT_DIR)

SCHEMA_SQL = os.path.join("db", "loan-db-schema.sql")
TABBAR_VUE = os.path.join("loan-mini", "components", "TabBar.vue")
JAVA_ROOT = os.path.join("loan-service", "src", "main", "java", "com", "loan")
API_CONTRACT_DOC = os.path.join("docs", "knowledge-base", "04-后端 API 契约.md")
DECISION_LOG = os.path.join("docs", "knowledge-base", "10-历史结论与决策日志.md")

DOCS_DIR = "docs"
KB_DIR = os.path.join("docs", "knowledge-base")
DOCS_SKILLS_DIR = os.path.join("docs", "skills")
WB_SKILLS_DIR = os.path.join(".workbuddy", "skills")

TEXT_EXT = (".md", ".html", ".htm")

# 表数表述：只认「总数型」，避免把「新增 2 张表」这类增量表述误判为脱节
MIN_TOTAL_TABLES = 20
DELTA_KEYWORDS = (
    "新增", "新表", "追加", "删除", "减少", "涉及", "跨", "合表", "关联",
    "其余", "另外", "再有", "其中", "这", "那", "join",
)
TABLE_TOTAL_RE = re.compile(r"(?<![\w.])(\d+)\s*张?表(?!达|格|单|结构|定义|关系|示|决|态)")

# 「N 表」表述中，若数字前出现这些词，说明讲的是历史/旧清单，不是当前总数
HISTORY_MARKERS = ("原", "历史", "早期", "最初", "当时", "此前", "之前", "旧", "曾经")
# 「纠错证据」类行：引用的是「被纠正掉的旧值」，属受保护信息，
# 绝不能当成脱节去改写（规则 9 例外条款）。检测到这些标记时降级为 WARN 并提示勿改。
EVIDENCE_MARKERS = (
    "纠错证据", "是错的", "实测为", "当时实测", "时点真值", "实测",
    "例外", "保留原值", "禁止抽象", "错误断言", "旧值",
)

# 检查 4：视为「文件/目录引用」的后缀
PATH_EXT = r"(?:md|html|htm|sql|py|js|jsx|ts|tsx|vue|java|yml|yaml|json|sh|csv|xml|png|jpg|jpeg|svg)"
PATH_CANDIDATE_RE = re.compile(
    r"[`\(\[\s>]([A-Za-z0-9_一-鿿\-][A-Za-z0-9_一-鿿\-./]*(?:\." + PATH_EXT + r"|/))"
    r"(?=[\s\)\]`\"'）,，。；;：:]|$)"
)

# --------------------------------------------------------------------------- #
# 数据结构
# --------------------------------------------------------------------------- #


@dataclass
class Finding:
    """一条差异明细。"""

    location: str          # 文件:行号 或 包名
    actual: str            # 实际值（文档/现状）
    expected: str          # 期望值（代码真值）
    detail: str = ""       # 补充说明
    severity: str = "FAIL"  # FAIL / WARN / INFO


@dataclass
class CheckResult:
    """单项检查结果。"""

    name: str
    title: str
    findings: List[Finding] = field(default_factory=list)
    skipped_reason: str = ""
    summary_lines: List[str] = field(default_factory=list)

    @property
    def failures(self) -> List[Finding]:
        return [f for f in self.findings if f.severity == "FAIL"]

    @property
    def warnings(self) -> List[Finding]:
        return [f for f in self.findings if f.severity == "WARN"]

    @property
    def infos(self) -> List[Finding]:
        return [f for f in self.findings if f.severity == "INFO"]

    @property
    def skipped(self) -> bool:
        return bool(self.skipped_reason)

    @property
    def status(self) -> str:
        if self.skipped:
            return "SKIP"
        return "PASS" if not self.failures else "FAIL（%d 处差异）" % len(self.failures)

    @property
    def passed(self) -> bool:
        return not self.skipped and not self.failures


# --------------------------------------------------------------------------- #
# 通用工具
# --------------------------------------------------------------------------- #


def read_text(abs_path: str) -> Optional[str]:
    """安全读取文本文件，统一 utf-8（失败时降级），读不到返回 None。"""
    try:
        with open(abs_path, "r", encoding="utf-8", errors="replace") as handle:
            return handle.read()
    except (OSError, UnicodeError):
        return None


def read_lines(abs_path: str) -> Optional[List[str]]:
    text = read_text(abs_path)
    if text is None:
        return None
    return text.splitlines()


def walk_files(base_dir: str, exts: Sequence[str]) -> List[str]:
    """递归收集 base_dir 下指定后缀的文件，返回绝对/相对混合路径（保持入参形态）。"""
    found: List[str] = []
    if not os.path.isdir(base_dir):
        return found
    for dirpath, dirnames, filenames in os.walk(base_dir):
        dirnames[:] = [d for d in dirnames if d not in {".git", "node_modules", "target", ".idea"}]
        for name in sorted(filenames):
            if name.lower().endswith(tuple(exts)):
                found.append(os.path.join(dirpath, name))
    return sorted(found)


def rel(path: str) -> str:
    """把绝对路径转成相对仓库根的路径，便于阅读。"""
    try:
        return os.path.relpath(path, REPO_ROOT)
    except ValueError:
        return path


def join_mapping(base: str, sub: str) -> str:
    """按 Spring 语义拼接类级与方法级 @RequestMapping 路径。

    注意：Spring 中方法级路径即使以 '/' 开头，也依然拼接在类级路径之后
    （只有类级无 @RequestMapping 时才视为绝对路径）。
    """
    sub = (sub or "").strip()
    if not base:
        return sub
    if not sub:
        return base
    return (base.rstrip("/") + "/" + sub.lstrip("/")).replace("//", "/")


def normalize_path_vars(path: str) -> str:
    """把 /api/x/{code}/y 归一化为 /api/x/{}/y，用于忽略占位符命名差异。"""
    return re.sub(r"\{[^}]*\}", "{}", path)


def paths_match(left: str, right: str) -> bool:
    """判断两个接口路径是否等价。

    逐段比对：段相等、或两段都是占位符（{code} vs {clientCode}，仅命名差异）时等价；
    占位符 vs 字面量时，仅当字面量全大写才等价——即文档写死的枚举型路径变量值
    （如 ALLOCATION 对应代码的 {type}）。若允许任意字面量匹配占位符，
    /order/{orderNo} 会被误判为等价于 /order/list。
    """
    left_parts, right_parts = left.split("/"), right.split("/")
    if len(left_parts) != len(right_parts):
        return False
    for lhs, rhs in zip(left_parts, right_parts):
        if lhs == rhs:
            continue
        lhs_var, rhs_var = lhs.startswith("{"), rhs.startswith("{")
        if lhs_var and rhs_var:
            continue  # 占位符命名差异
        if lhs_var != rhs_var:
            literal = rhs if lhs_var else lhs
            if literal.isupper():
                continue  # 文档写死枚举值 vs 代码路径变量
            return False
        return False
    return True


def find_match(target: str, candidates: Iterable[str]) -> Optional[str]:
    """在候选路径中找出与 target 等价的路径，找不到返回 None。"""
    for candidate in candidates:
        if paths_match(target, candidate):
            return candidate
    return None


# --------------------------------------------------------------------------- #
# 检查 1：表数一致性
# --------------------------------------------------------------------------- #


def check_table_count() -> CheckResult:
    result = CheckResult("1", "表数一致性（真值：db/loan-db-schema.sql）")

    schema_path = os.path.join(REPO_ROOT, SCHEMA_SQL)
    schema_text = read_text(schema_path)
    if schema_text is None:
        result.skipped_reason = "未找到 %s，无法取得表数真值" % SCHEMA_SQL
        return result

    truth = len(re.findall(r"CREATE\s+TABLE", schema_text, re.IGNORECASE))
    result.summary_lines.append("真值：%s 中共 %d 张表（CREATE TABLE）" % (SCHEMA_SQL, truth))

    docs_root = os.path.join(REPO_ROOT, DOCS_DIR)
    for doc_path in walk_files(docs_root, TEXT_EXT):
        # D15 要求历史决策台账保留当时数字，不参与当前真值校验。
        if rel(doc_path) == DECISION_LOG:
            continue
        lines = read_lines(doc_path)
        if lines is None:
            continue
        for lineno, line in enumerate(lines, 1):
            for match in TABLE_TOTAL_RE.finditer(line):
                number = int(match.group(1))
                if number < MIN_TOTAL_TABLES:
                    continue  # 不可能是全库总数，属增量/局部表述
                prefix = line[max(0, match.start() - 12): match.start()]
                if any(keyword in prefix for keyword in DELTA_KEYWORDS):
                    continue  # 增量型表述（新增/追加/跨 N 表…）
                if number == truth:
                    continue
                if any(marker in prefix for marker in HISTORY_MARKERS):
                    continue  # 历史叙述（「原 61 表清单…」），非当前总数断言
                # 同一行内已出现正确表数：多为勘误/新旧对照，降级为提示，不阻塞 CI
                already_correct = re.search(r"(?<![\w.])%d\s*张?表(?!达|格|单)" % truth, line) is not None
                has_errata = bool(re.search(r"勘误|实际为|以.{0,12}为准", line))
                # 纠错证据行：引用的是被纠正掉的旧值，属受保护信息（规则 9 例外），不可改写
                is_evidence = any(marker in line for marker in EVIDENCE_MARKERS)
                if is_evidence:
                    hint = "【纠错证据】该行引用的是被纠正掉的旧值，属受保护信息，请勿改写此行数字"
                elif already_correct or has_errata:
                    hint = "该行已含正确值/勘误说明，建议直接改写正文、删除旧数字"
                else:
                    hint = ""
                result.findings.append(
                    Finding(
                        location="%s:%d" % (rel(doc_path), lineno),
                        actual="%d 表" % number,
                        expected="%d 表" % truth,
                        detail="原文：%s%s" % (line.strip()[:160], ("（%s）" % hint) if hint else ""),
                        severity="WARN" if (already_correct or has_errata or is_evidence) else "FAIL",
                    )
                )
    return result


# --------------------------------------------------------------------------- #
# 检查 2：角色 tabBar 数量一致性
# --------------------------------------------------------------------------- #


def parse_tabbar(abs_path: str) -> Optional[Tuple[int, List[str], int, List[str]]]:
    """解析 TabBar.vue，返回 (渠道 tab 数, 渠道标签, 默认 tab 数, 默认标签)。"""
    text = read_text(abs_path)
    if text is None:
        return None

    block_re = re.compile(r"return\s*\[(.*?)\];", re.S)
    anchor = text.find("store.isChannel")
    if anchor < 0:
        return None
    channel_match = block_re.search(text, anchor)
    if not channel_match:
        return None
    default_match = block_re.search(text, channel_match.end())
    if not default_match:
        return None

    def parse_block(block: str) -> Tuple[int, List[str]]:
        keys = re.findall(r"key:\s*['\"]([^'\"]+)['\"]", block)
        labels = re.findall(r"label:\s*['\"]([^'\"]+)['\"]", block)
        return len(keys), labels

    ch_count, ch_labels = parse_block(channel_match.group(1))
    df_count, df_labels = parse_block(default_match.group(1))
    return ch_count, ch_labels, df_count, df_labels


def check_tabbar() -> CheckResult:
    result = CheckResult("2", "角色 tabBar 数量一致性（真值：loan-mini/components/TabBar.vue）")

    tabbar_path = os.path.join(REPO_ROOT, TABBAR_VUE)
    parsed = parse_tabbar(tabbar_path)
    if parsed is None:
        result.skipped_reason = "未找到或无法解析 %s（缺少 store.isChannel 分支）" % TABBAR_VUE
        return result

    ch_count, ch_labels, df_count, df_labels = parsed
    result.summary_lines.append(
        "真值：渠道（store.isChannel）= %d tab [%s]；客户/员工（默认）= %d tab [%s]"
        % (ch_count, "·".join(ch_labels), df_count, "·".join(df_labels))
    )

    # 角色关键词 → 期望数量
    role_expectations = {
        "渠道": (ch_count, ch_labels),
        "客户": (df_count, df_labels),
        "员工": (df_count, df_labels),
    }
    tab_re = re.compile(r"(\d+)\s*(?:个)?\s*tab", re.IGNORECASE)
    # 文档已自证「这是已知待修问题」或属「纠错证据」（规则 9 例外）的行：
    # 引用他人错误值而非自身断言，不计差异
    SELF_AWARE_MARKERS = (
        "不一致", "待修正", "待更新", "文档错误", "勘误", "已修正", "以 `TabBar.vue` 为准"
    ) + EVIDENCE_MARKERS
    ROLE_WINDOW = 30  # 角色关键词与数字的关联窗口（字符数）
    ROLE_OPPOSITE = {"渠道": "客户", "客户": "渠道", "员工": "渠道"}
    # 「其余/其他」指代非渠道角色
    OTHER_ROLE_WORDS = ("其余", "其他", "其它")
    # 角色关键词若紧跟这些字符，说明它是 tab 名/路径片段（如「录入客户」），不是角色
    LABEL_CONTEXT_CHARS = ("入", "录", "「", "·", "、", "/", "页")

    def find_role(text: str, pos: int) -> Optional[str]:
        """找出离数字最近的「渠道/客户/员工」角色关键词，并处理否定与标签上下文。"""
        best_role: Optional[str] = None
        best_idx = -1
        best_dist = ROLE_WINDOW + 1
        for role in role_expectations:
            for match in re.finditer(re.escape(role), text):
                if text[max(0, match.start() - 1): match.start()] in LABEL_CONTEXT_CHARS:
                    continue  # 「录入客户」「/客户」等标签上下文，不算角色声明
                dist = abs(match.start() - pos)
                if dist < best_dist:
                    best_dist, best_role, best_idx = dist, role, match.start()
        if best_role is None:
            return None
        # 「非渠道」取反；「渠道 3 tab / 其余 5 tab」中的「其余」在角色词之后，
        # 故需在「角色词 ↔ 数字」区间内双向查找
        negated = "非" in text[max(0, best_idx - 2): best_idx]
        if not negated:
            lo, hi = min(best_idx, pos), max(best_idx, pos)
            segment = text[max(0, lo - 12): hi]
            negated = any(word in segment for word in OTHER_ROLE_WORDS)
        if negated:
            return ROLE_OPPOSITE.get(best_role, best_role)
        return best_role

    docs_root = os.path.join(REPO_ROOT, DOCS_DIR)
    for doc_path in walk_files(docs_root, TEXT_EXT):
        lines = read_lines(doc_path)
        if lines is None:
            continue
        for lineno, line in enumerate(lines, 1):
            if any(marker in line for marker in SELF_AWARE_MARKERS):
                continue
            for match in tab_re.finditer(line):
                number = int(match.group(1))
                if number <= 0:
                    continue
                hit_role = find_role(line, match.start())
                if hit_role is None:
                    continue  # 无角色上下文（如 tabsFor/原型描述），无法判定，跳过
                expected, expected_labels = role_expectations[hit_role]
                if number == expected:
                    continue
                # 提取文档里括号中的标签列表，便于对照
                doc_labels = re.findall(r"[（(]([^（）()]*[·、][^（）()]*)", line)
                label_hint = ""
                if doc_labels:
                    label_hint = "；文档标签「%s」vs 代码标签「%s」" % (
                        doc_labels[0].strip(),
                        "·".join(expected_labels),
                    )
                result.findings.append(
                    Finding(
                        location="%s:%d" % (rel(doc_path), lineno),
                        actual="%s %d tab" % (hit_role, number),
                        expected="%s %d tab" % (hit_role, expected),
                        detail="原文：%s%s" % (line.strip()[:160], label_hint),
                    )
                )
    return result


# --------------------------------------------------------------------------- #
# 检查 3：业务包实现状态
# --------------------------------------------------------------------------- #


def check_packages() -> CheckResult:
    result = CheckResult("3", "业务包实现状态（loan-service 各业务包 .java 文件数）")

    java_root = os.path.join(REPO_ROOT, JAVA_ROOT)
    if not os.path.isdir(java_root):
        result.skipped_reason = "未找到 %s" % JAVA_ROOT
        return result

    all_java = walk_files(java_root, (".java",))
    package_counts: Dict[str, int] = {}
    for entry in sorted(os.listdir(java_root)):
        pkg_dir = os.path.join(java_root, entry)
        if not os.path.isdir(pkg_dir):
            continue
        package_counts[entry] = len(walk_files(pkg_dir, (".java",)))

    empty_packages = sorted(name for name, count in package_counts.items() if count == 0)
    result.summary_lines.append(
        "业务包 %d 个，其中 %d 个 .java 文件数为 0：%s"
        % (len(package_counts), len(empty_packages), "、".join(empty_packages) if empty_packages else "无")
    )

    truly_empty: List[str] = []
    relocated: List[Tuple[str, List[str]]] = []

    for pkg in empty_packages:
        token = pkg.lower()
        related_files = [f for f in all_java if token in os.path.basename(f).lower()]
        locations = sorted({os.path.dirname(rel(f)) for f in related_files})
        if locations:
            relocated.append((pkg, locations))
        else:
            truly_empty.append(pkg)

    for pkg in truly_empty:
        result.findings.append(
            Finding(
                location="%s/%s/" % (JAVA_ROOT, pkg),
                actual="0 个 .java，且全仓无同名/相关类",
                expected="有实现文件，或删除该空包目录",
                detail="真空包（无实现）",
            )
        )

    for pkg, locations in relocated:
        shown = "、".join(locations[:6])
        if len(locations) > 6:
            shown += " 等 %d 处" % len(locations)
        result.findings.append(
            Finding(
                location="%s/%s/" % (JAVA_ROOT, pkg),
                actual="0 个 .java，但实现散落在：%s" % shown,
                expected="包名与实现位置一致（归并实现或删除空包）",
                detail="⚠️ 包名与实现位置不一致",
                severity="WARN",
            )
        )
    return result


# --------------------------------------------------------------------------- #
# 检查 4：文档指针死链
# --------------------------------------------------------------------------- #


def collect_pointer_files() -> List[str]:
    """收集需要做死链检查的文档：知识库 + 各 SKILL.md。

    历史决策台账受 D15 保护，其中旧路径是时点证据，不代表当前文档指针。
    """
    files: List[str] = []
    kb_root = os.path.join(REPO_ROOT, KB_DIR)
    if os.path.isdir(kb_root):
        files.extend(p for p in walk_files(kb_root, (".md",)))
    for skills_root in (os.path.join(REPO_ROOT, DOCS_SKILLS_DIR), os.path.join(REPO_ROOT, WB_SKILLS_DIR)):
        for path in walk_files(skills_root, (".md",)):
            if os.path.basename(path).lower() == "skill.md":
                files.append(path)
    return sorted(path for path in set(files) if rel(path) != DECISION_LOG)


def is_tree_line(line: str) -> bool:
    """判断是否为 ASCII 目录树示意行（形如 '│   ├── ocr/   # xxx'）。

    只认制表符类字符（│├└┬），不能把 ASCII 的 '|' '+' 算进来——
    markdown 表格行以 '|' 开头，误判会整片吞掉死链。
    """
    return bool(re.match(r"^\s*[│├└┬]", line)) or bool(re.search(r"[├└]", line))


_REPO_INDEX: Optional[Set[str]] = None


def repo_index() -> Set[str]:
    """全仓相对路径索引（文件 + 目录），用于「模块内相对路径」的模糊解析。"""
    global _REPO_INDEX
    if _REPO_INDEX is not None:
        return _REPO_INDEX
    items: Set[str] = set()
    ignore = {".git", "node_modules", "target", ".idea", "dist", "build", ".DS_Store"}
    for dirpath, dirnames, filenames in os.walk(REPO_ROOT):
        dirnames[:] = [d for d in dirnames if d not in ignore]
        for name in filenames:
            items.add(rel(os.path.join(dirpath, name)))
        for name in dirnames:
            items.add(rel(os.path.join(dirpath, name)) + "/")
    _REPO_INDEX = items
    return items


def resolve_reference(doc_dir: str, candidate: str) -> Optional[str]:
    """解析文档中的路径引用，返回命中目标的相对路径；未命中返回 None。

    解析顺序：① 相对文档自身目录；② 相对仓库根；③ 按路径后缀模糊匹配
    （文档常写模块内相对路径，如 `service.sh`、`src/layout/Layout.vue`）。
    """
    base = candidate.split("#")[0].strip().rstrip("/")
    if not base:
        return ""

    def probe(target: str) -> Optional[str]:
        if os.path.exists(os.path.join(doc_dir, target)):
            return os.path.relpath(os.path.join(doc_dir, target), REPO_ROOT)
        if os.path.exists(os.path.join(REPO_ROOT, target)):
            return target
        for path in repo_index():
            if path == target or path.rstrip("/").endswith("/" + target):
                return path
        return None

    hit = probe(base)
    if hit is not None:
        return hit
    # 文档常带仓库目录名前缀（如 `loan-main/docs/...`），去掉前缀再试一次
    repo_name = os.path.basename(REPO_ROOT)
    if base.startswith(repo_name + "/"):
        return probe(base[len(repo_name) + 1:])
    return None


# 形如 `NN-模块名.md` 的命名模板占位，不是真实文件
PLACEHOLDER_RE = re.compile(r"(NN-|XXX+|YYY+|ZZZ+|\{|模块名|某某)")
# 引用出现在报错文本里（如 `ENOENT: src/manifest.json`），不是文档指针
ERROR_QUOTE_MARKERS = ("ENOENT", "Exception", "Error:", "error:", "报错", "抛异常")
# 文档自己声明该引用已失效（如「禁止引用 `output/`（已不存在）」），属护栏而非脱节
SELF_DECLARED_DEAD = (
    "禁止引用", "禁止再引用", "勿再引用", "均不存在", "已不存在", "全仓不存在",
    "不存在", "从未落盘", "已删除", "失效",
)
# 构建产物目录，文档引用的是「构建后会生成」的路径，不按死链处理
BUILD_OUTPUT_SEGMENTS = ("dist/", "build/", "target/", "unpackage/", "node_modules/")
# 框架构建期生成、不入库的文件（uni-app 由 manifest.json 编译产出 app.json）
BUILD_OUTPUT_BASENAMES = {"app.json", "app.wxss", "project.config.json"}
# 反引号/链接内的完整路径（允许空格，文件名如「04-后端 API 契约.md」含空格）
SPAN_PATH_RE = re.compile(
    r"^[A-Za-z0-9_一-鿿\-][A-Za-z0-9_一-鿿\-./ ]*"
    r"(?:\.(?:md|html|htm|sql|py|js|jsx|ts|tsx|vue|java|yml|yaml|json|sh|csv|xml|png|jpg|jpeg|svg)|/)$"
)


def extract_candidates(line: str) -> List[str]:
    """从一行中提取路径引用候选。

    优先级：① 反引号内的完整片段（允许空格）；② markdown 链接目标；
    ③ 行内裸写路径（不含空格）。
    """
    candidates: List[str] = []
    spans = re.findall(r"`([^`]+)`", line) + re.findall(r"\]\(([^)]+)\)", line)
    for span in spans:
        span = span.strip().strip("。；;，,")
        if SPAN_PATH_RE.match(span):
            candidates.append(span)
    residual = re.sub(r"`[^`]*`", " ", line)
    residual = re.sub(r"\]\([^)]*\)", " ", residual)
    for match in PATH_CANDIDATE_RE.finditer(residual):
        candidates.append(match.group(1).strip())
    return candidates


def is_noise_path(candidate: str) -> bool:
    """过滤掉非文件路径的误报（URL、Java 包名、通配符、模板占位等）。"""
    if not candidate or "*" in candidate or "?" in candidate:
        return True
    if PLACEHOLDER_RE.search(candidate):
        return True
    if any(segment in candidate for segment in BUILD_OUTPUT_SEGMENTS):
        return True  # 构建产物路径，构建后才存在
    if os.path.basename(candidate) in BUILD_OUTPUT_BASENAMES:
        return True
    if candidate.startswith(("http://", "https://", "www.", "/", "#", "@")):
        return True
    if re.match(r"^(?:com|org|net|io|cn)\.[a-z]", candidate):
        return True  # Java 包名，如 com.loan.mini
    if re.match(r"^[a-z]+\.[a-z]+\.[a-z]+$", candidate) and "/" not in candidate:
        return True
    if candidate.count(".") > 2:
        return True
    return False


def check_dead_links() -> CheckResult:
    result = CheckResult("4", "文档指针死链（相对路径引用可达性）")

    files = collect_pointer_files()
    if not files:
        result.skipped_reason = "未找到 docs/knowledge-base/*.md 或 **/SKILL.md"
        return result
    result.summary_lines.append("扫描 %d 份文档（docs/knowledge-base + */SKILL.md）" % len(files))

    checked = 0
    for doc_path in files:
        lines = read_lines(doc_path)
        if lines is None:
            continue
        doc_dir = os.path.dirname(doc_path)
        in_fence = False
        for lineno, line in enumerate(lines, 1):
            if line.lstrip().startswith("```"):
                in_fence = not in_fence
                continue
            if in_fence or is_tree_line(line):
                continue  # 目录树 / 代码块是结构示意，不是路径引用
            if any(marker in line for marker in SELF_DECLARED_DEAD):
                continue  # 文档自身在警示该路径失效，属护栏而非脱节
            for candidate in extract_candidates(line):
                candidate = candidate.rstrip(".,;:)]}")
                if is_noise_path(candidate):
                    continue
                # 引用出现在报错原文中（如 `ENOENT: src/manifest.json`），不是文档指针
                pos = line.find(candidate)
                if pos > 0 and any(marker in line[:pos] for marker in ERROR_QUOTE_MARKERS):
                    continue
                # 去掉 markdown 锚点
                candidate_clean = candidate.split("#")[0].strip()
                if not candidate_clean:
                    continue
                checked += 1
                if resolve_reference(doc_dir, candidate_clean) is not None:
                    continue
                note = ""
                if candidate_clean.rstrip("/").endswith("output"):
                    note = "（历史失效前缀 output/，该目录已不存在，建议删除该引用）"
                result.findings.append(
                    Finding(
                        location="%s:%d" % (rel(doc_path), lineno),
                        actual="引用路径 %s 不存在" % candidate,
                        expected="存在的文件/目录，或删除该引用",
                        detail="原文：%s%s" % (line.strip()[:160], note),
                        # 纯目录引用可能是「尚未创建的归档约定」，降级为告警
                        severity="WARN" if candidate_clean.endswith("/") else "FAIL",
                    )
                )
    result.summary_lines.append("共校验 %d 处路径引用" % checked)
    return result


# --------------------------------------------------------------------------- #
# 检查 5：接口契约一致性
# --------------------------------------------------------------------------- #


def collect_code_endpoints() -> Dict[str, List[str]]:
    """从所有 Controller 提取实际接口路径：path -> [controller 相对路径]。"""
    java_root = os.path.join(REPO_ROOT, JAVA_ROOT)
    endpoints: Dict[str, List[str]] = {}
    if not os.path.isdir(java_root):
        return endpoints

    class_ann_re = re.compile(r"@RequestMapping\(\s*\"([^\"]+)\"")
    method_ann_re = re.compile(r"@(?:Get|Post|Put|Delete|Patch|Request)Mapping(?:\(\s*\"([^\"]*)\"|(?=\s|\)|$))")
    class_decl_re = re.compile(r"class\s+\w*Controller\b")

    for file_path in walk_files(java_root, (".java",)):
        if not os.path.basename(file_path).endswith("Controller.java"):
            continue
        text = read_text(file_path)
        if text is None:
            continue

        class_match = class_decl_re.search(text)
        split_at = class_match.start() if class_match else 0
        header, body = text[:split_at], text[split_at:]

        base_match = class_ann_re.search(header)
        base = base_match.group(1).strip() if base_match else ""

        for match in method_ann_re.finditer(body):
            sub = match.group(1) or ""
            full = join_mapping(base, sub)
            if full:
                endpoints.setdefault(full, []).append(rel(file_path))
    return endpoints


def collect_doc_endpoints(abs_path: str) -> Tuple[Dict[str, List[int]], Dict[str, List[int]]]:
    """从契约文档提取接口路径，返回 (生效路径, 已废弃路径)，值为行号列表。

    markdown 中 ~~xxx~~ 表示已废弃；只把 ~~ 包裹内部捕获到的路径算作「已废弃」，
    同一行里 ~~ 之外的「改用 xxx」属于推荐路径，必须算作生效路径。
    """
    active: Dict[str, List[int]] = {}
    deprecated: Dict[str, List[int]] = {}

    lines = read_lines(abs_path)
    if lines is None:
        return active, deprecated

    # 不要求右反引号紧跟路径：文档常写成 `GET /api/x/pending?type=ALL`
    path_re = re.compile(r"`\s*(?:GET|POST|PUT|DELETE|PATCH)?\s*(/api/[A-Za-z0-9/_{}.\-]+)", re.IGNORECASE)
    strike_re = re.compile(r"~~(.+?)~~")

    for lineno, line in enumerate(lines, 1):
        struck_spans = strike_re.findall(line)
        residual = strike_re.sub(" ", line)
        for path in path_re.findall(residual):
            if path.endswith("/"):
                continue  # 「/api/admin/**」这类作用域声明，不是具体接口
            active.setdefault(path, []).append(lineno)
        for span in struck_spans:
            for path in path_re.findall(span):
                if path.endswith("/"):
                    continue
                deprecated.setdefault(path, []).append(lineno)
    return active, deprecated


def check_api_contract() -> CheckResult:
    result = CheckResult("5", "接口契约一致性（04-后端 API 契约.md vs Controller）")

    doc_path = os.path.join(REPO_ROOT, API_CONTRACT_DOC)
    if not os.path.isfile(doc_path):
        result.skipped_reason = "未找到 %s" % API_CONTRACT_DOC
        return result

    endpoints = collect_code_endpoints()
    if not endpoints:
        result.skipped_reason = "未在 %s 下找到任何 Controller" % JAVA_ROOT
        return result

    active, deprecated = collect_doc_endpoints(doc_path)
    # 文档声明的作用域：取文档中占比最高的前缀（该文档标题与 Base URL 声明覆盖 /api/mini/*，
    # 少量 /api/admin 引用属旁证，不应把 admin 端全量接口算作「文档漏写」）
    prefix_counter: Dict[str, int] = {}
    for path in active:
        segments = path.split("/")
        if len(segments) > 2:
            prefix = "/api/%s" % segments[2]
            prefix_counter[prefix] = prefix_counter.get(prefix, 0) + 1
    dominant = max(prefix_counter, key=lambda k: prefix_counter[k]) if prefix_counter else "/api/mini"
    scope_prefixes = (dominant,)

    result.summary_lines.append(
        "代码侧实际接口 %d 个（其中文档声明作用域 %s 内 %d 个）；文档侧生效路径 %d 个、标记废弃 %d 个"
        % (
            len(endpoints),
            "/".join(scope_prefixes),
            sum(1 for p in endpoints if p.startswith(scope_prefixes)),
            len(active),
            len(deprecated),
        )
    )

    # ① 文档有、代码没有
    for raw in sorted(active):
        if find_match(raw, endpoints) is not None:
            continue
        lines_hint = "、".join(str(n) for n in active[raw][:3])
        result.findings.append(
            Finding(
                location="%s:%s" % (API_CONTRACT_DOC, lines_hint),
                actual="文档声明 %s" % raw,
                expected="Controller 中存在该路径",
                detail="文档有而代码没有（接口已下线或路径写错）",
            )
        )

    # ② 代码有、文档没写（限定在文档声明的作用域内，避免 admin 端全量噪音）
    #    文档已标记废弃但代码仍在（兼容期）的接口由 ④ 提示，此处不重复计差异
    for path in sorted(endpoints):
        if not path.startswith(scope_prefixes):
            continue
        if find_match(path, active) is not None or find_match(path, deprecated) is not None:
            continue
        controllers = sorted(set(endpoints[path]))[:2]
        result.findings.append(
            Finding(
                location="%s | %s" % (", ".join(controllers), path),
                actual="代码存在该接口",
                expected="文档 %s 中登记该接口" % os.path.basename(API_CONTRACT_DOC),
                detail="代码有而文档没写（新增接口未同步契约）",
            )
        )

    # ③ 路径写法不一致（同一接口的不同写法，属轻度漂移，仅告警）
    for raw in sorted(active):
        matched = find_match(raw, endpoints)
        if matched is None or matched == raw:
            continue
        same_shape = normalize_path_vars(raw) == normalize_path_vars(matched)
        result.findings.append(
            Finding(
                location="%s:%s" % (API_CONTRACT_DOC, active[raw][0]),
                actual="文档 %s" % raw,
                expected="代码 %s" % matched,
                detail="占位符命名不一致（同一接口，参数名不同）"
                if same_shape
                else "文档写死具体值，代码用路径变量（同一接口的不同写法）",
                severity="WARN" if same_shape else "INFO",
            )
        )

    # ④ 已废弃路径的现状（仅提示，不计入差异）
    for path in sorted(deprecated):
        status = (
            "代码仍存在（兼容期，符合预期）"
            if find_match(path, endpoints) is not None
            else "代码已移除（符合废弃声明）"
        )
        lines_hint = "、".join(str(n) for n in deprecated[path][:2])
        result.findings.append(
            Finding(
                location="%s:%s" % (API_CONTRACT_DOC, lines_hint),
                actual="文档标记废弃 %s" % path,
                expected="无需处理",
                detail=status,
                severity="INFO",
            )
        )
    return result


# --------------------------------------------------------------------------- #
# 输出与汇总
# --------------------------------------------------------------------------- #


SEPARATOR = "=" * 78
SUB_SEPARATOR = "-" * 78

FIX_SUGGESTIONS = {
    "1": (
        "把 docs/ 中所有「N 表」表述统一改为 db/loan-db-schema.sql 的真实表数；"
        "带勘误说明的行请直接改写正文，不要保留历史数字。"
    ),
    "2": (
        "以 loan-mini/components/TabBar.vue 的 tabList 为准更新 docs/knowledge-base/01-角色权限模型.md，"
        "补齐渠道角色的「录入客户」tab，并同步团队记忆中的 tab 数。"
    ),
    "3": (
        "真空包：确认后删除目录（或补实现）；"
        "包名与实现位置不一致：把实现归并到同名包，或在文档中标注「实现位于 X」后删除空包。"
    ),
    "4": (
        "修正或删除死链引用；特别注意已不存在的 output/ 前缀，"
        "引用统一指向 docs/ 下的归档文件。"
    ),
    "5": (
        "文档有而代码没有：确认接口是否已下线，是则删除文档条目；"
        "代码有而文档没写：把新接口补进 04-后端 API 契约.md；"
        "占位符命名不一致：统一为代码中的 @PathVariable 名称。"
    ),
}


def print_findings(result: CheckResult) -> None:
    groups = (("FAIL", result.failures), ("WARN", result.warnings), ("INFO", result.infos))
    for label, items in groups:
        if not items:
            continue
        print("  【%s】%d 条" % (label, len(items)))
        for item in items:
            print("    · %s" % item.location)
            print("        实际值：%s" % item.actual)
            print("        期望值：%s" % item.expected)
            if item.detail:
                print("        说明  ：%s" % item.detail)
        print()


def print_result(result: CheckResult, quiet: bool) -> None:
    print(SEPARATOR)
    print("检查 %s · %s" % (result.name, result.title))
    print(SEPARATOR)

    if result.skipped:
        print("  SKIP —— %s\n" % result.skipped_reason)
        return

    for line in result.summary_lines:
        print("  %s" % line)
    print("  结果：%s\n" % result.status)

    if not quiet:
        print_findings(result)


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(
        description="校验知识库文档与代码的一致性（表数 / tabBar / 业务包 / 死链 / 接口契约）",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--only", metavar="N[,N...]", default="", help="只运行指定检查项，如 --only 1,4")
    parser.add_argument("--quiet", action="store_true", help="只输出汇总，不展开差异明细")
    args = parser.parse_args(argv)

    selected: Set[str] = {item.strip() for item in args.only.split(",") if item.strip()} if args.only else set()

    print()
    print(SEPARATOR)
    print("知识库 / 代码一致性校验 —— %s" % REPO_ROOT)
    print(SEPARATOR)
    print()

    checks: List[CheckResult] = []
    for check_id, runner in (
        ("1", check_table_count),
        ("2", check_tabbar),
        ("3", check_packages),
        ("4", check_dead_links),
        ("5", check_api_contract),
    ):
        if selected and check_id not in selected:
            continue
        try:
            checks.append(runner())
        except Exception as exc:  # 单项检查异常不应中断整轮校验
            failed = CheckResult(check_id, "执行异常")
            failed.findings.append(
                Finding(location="检查 %s" % check_id, actual="运行时异常：%s" % exc, expected="正常完成", detail="请检查脚本或仓库结构")
            )
            checks.append(failed)

    for result in checks:
        print_result(result, args.quiet)

    total = len(checks)
    passed_count = sum(1 for r in checks if r.passed)
    failed_checks = [r for r in checks if r.failures]
    skipped_checks = [r for r in checks if r.skipped]

    print(SEPARATOR)
    if total == len(skipped_checks):
        print("汇总：无可执行项（%d 项全部跳过，请用 --only 指定或确认仓库结构）" % total)
        print()
        print(SEPARATOR)
        print("结论：未执行任何检查，无法判定一致性。退出码 0")
        print(SEPARATOR)
        return 0

    print("汇总：%d/%d 项通过" % (passed_count, total))
    if skipped_checks:
        print("      跳过 %d 项：%s" % (len(skipped_checks), "、".join("检查 %s" % r.name for r in skipped_checks)))
    if failed_checks:
        print("      未通过 %d 项：%s" % (len(failed_checks), "、".join("检查 %s" % r.name for r in failed_checks)))
        print()
        print("修复建议：")
        for result in failed_checks:
            print("  · 检查 %s（%s）：%s" % (result.name, result.title, FIX_SUGGESTIONS.get(result.name, "请对照差异逐条修正")))
        print()
        print(SEPARATOR)
        print("结论：存在文档与代码脱节，请按上述建议修复后重跑。退出码 1")
        print(SEPARATOR)
        return 1

    print()
    print(SEPARATOR)
    print("结论：全部通过，文档与代码一致。退出码 0")
    print(SEPARATOR)
    return 0


if __name__ == "__main__":
    sys.exit(main())
