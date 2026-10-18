"""校验矩阵、Web 路由、数据库种子路径和角色授权是否存在漂移。

单一真源 = scripts/menu_matrix.py 的 MENUS（path -> roles）。
- 路径一致性：矩阵 MENUS ↔ Web 路由 ↔ 种子 t_menu 路径
- 授权一致性：矩阵 MENUS 的 roles ↔ 种子 t_role_permission 的 (path, role)
"""
import ast
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def load_matrix():
    matrix = ast.parse((ROOT / "scripts/menu_matrix.py").read_text(encoding="utf-8"))
    ns = {}
    exec(compile(matrix, "menu_matrix.py", "exec"), ns)
    return ns["MENUS"]


def load_router_paths():
    router = (ROOT / "loan-web/src/router/index.js").read_text(encoding="utf-8")
    paths = set(re.findall(r"path:\s*'([^']+)'", router))
    return {p if p.startswith("/") else "/" + p for p in paths}


def parse_seed_auth(seed_sql):
    """解析 init-role-menu-idempotent.sql 的 t_role_permission 授权，返回 {path: set(roles)}。

    只解析显式「角色 × path」INSERT...SELECT（不含动态父级补齐段）。
    每条语句两种形态：
      1) SELECT r.role_code ... FROM (SELECT 'X' role_code UNION ALL ...) r JOIN t_menu m ON m.path IN (...)
      2) SELECT 'X',m.id ... FROM t_menu m WHERE m.path IN (...)
    """
    auth = {}
    # 按语句切分：每条 INSERT IGNORE INTO t_role_permission 到分号
    blocks = re.split(r"INSERT IGNORE INTO t_role_permission", seed_sql)
    for block in blocks[1:]:
        seg = block.split(";", 1)[0]
        # 跳过动态父级补齐（FROM t_role_permission JOIN）
        if re.search(r"FROM\s+t_role_permission\s+rp\s+JOIN", seg):
            continue
        roles = set(re.findall(r"SELECT\s+'([A-Z_]+)'", seg))
        # 路径：m.path IN (...) 或 m.path='...'
        paths = set()
        for m in re.finditer(r"m\.path\s+IN\s*\(([^)]*)\)", seg):
            paths.update(re.findall(r"'([^']+)'", m.group(1)))
        for m in re.finditer(r"m\.path\s*=\s*'([^']+)'", seg):
            paths.add(m.group(1))
        for p in paths:
            auth.setdefault(p, set()).update(roles)
    return auth


def main():
    menus = load_matrix()
    router_paths = load_router_paths()
    seed = (ROOT / "db/init-role-menu-idempotent.sql").read_text(encoding="utf-8")
    seed_paths = set(re.findall(r"'(/[^']+)'", seed))
    seed_auth = parse_seed_auth(seed)

    errors = []

    # 1. 路径一致性：矩阵 vs 路由
    for p in sorted(set(menus) - router_paths):
        errors.append(f"路由缺失: {p}")
    for p in sorted(router_paths - set(menus) - {"", "/"}):
        # 路由里允许存在矩阵未覆盖的辅助路由（redirect/403/404 等），仅告警不阻断
        if p not in ("/403", "/login") and not p.startswith("/:"):
            print(f"  提示: 路由存在但矩阵未收录: {p}")

    # 2. 路径一致性：矩阵 vs 种子 t_menu 路径
    for p in sorted(set(menus) - seed_paths):
        errors.append(f"种子路径缺失: {p}")

    # 2.5 名称一致性：矩阵 name vs 种子 t_menu 的 menu_name（叶子菜单）
    # 种子叶子格式：('名称','/path','组件','MENU',...)；domain 父级第三字段为 NULL 不参与。
    seed_menu_names = {}
    for m in re.finditer(r"'([^']*)'\s*,\s*'(/[^']+)'\s*,\s*'([^']*)'\s*,\s*'MENU'", seed):
        name, path, comp = m.groups()
        if not path.startswith("/domain/") and comp:
            seed_menu_names[path] = name
    for p, item in sorted(menus.items()):
        name = item.get("name", "") if isinstance(item, dict) else ""
        seed_name = seed_menu_names.get(p)
        if name and seed_name and name != seed_name:
            errors.append(f"名称不一致: {p} 矩阵={name!r} vs 种子={seed_name!r}")

    # 3. 授权一致性：矩阵 roles vs 种子 t_role_permission
    for p, item in sorted(menus.items()):
        roles = item.get("roles", set()) if isinstance(item, dict) else set()
        seed_roles = seed_auth.get(p, set())
        if not seed_roles:
            errors.append(f"种子授权缺失: {p} (矩阵要求 {sorted(roles)})")
            continue
        if roles != seed_roles:
            missing = roles - seed_roles
            extra = seed_roles - roles
            detail = []
            if missing:
                detail.append(f"矩阵有种子缺 {sorted(missing)}")
            if extra:
                detail.append(f"种子有矩阵缺 {sorted(extra)}")
            errors.append(f"授权不一致: {p} " + "; ".join(detail))
    # 种子里有、矩阵里没有的菜单（历史漂移）
    for p in sorted(set(seed_auth) - set(menus)):
        errors.append(f"种子里有授权但矩阵未收录: {p} -> {sorted(seed_auth[p])}")

    print(f"矩阵菜单: {len(menus)}")
    print(f"路由路径: {len(router_paths)}")
    print(f"种子路径命中: {len(set(menus) & seed_paths)}")
    print(f"种子授权项: {len(seed_auth)}")

    if errors:
        print("\n=== 漂移 ===")
        for e in errors:
            print(" - " + e)
        raise SystemExit(1)
    print("\n矩阵、路由、种子路径与角色授权一致")


if __name__ == "__main__":
    main()
