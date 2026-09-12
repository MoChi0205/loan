"""校验矩阵、Web 路由和数据库种子路径是否存在漂移。"""
import ast
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
matrix = ast.parse((ROOT / "scripts/menu_matrix.py").read_text(encoding="utf-8"))
names = {n.id for n in ast.walk(matrix) if isinstance(n, ast.Name)}
ns = {}
exec(compile(matrix, "menu_matrix.py", "exec"), ns)
expected = set(ns["MENUS"])
router = (ROOT / "loan-web/src/router/index.js").read_text(encoding="utf-8")
router_paths = set(re.findall(r"path:\s*'([^']+)'", router))
router_paths = {p if p.startswith("/") else "/" + p for p in router_paths}
seed = (ROOT / "db/menu-permission-seed-2026-09-01.sql").read_text(encoding="utf-8")
seed_paths = set(re.findall(r"'(/[^']+)'", seed))
missing_router = sorted(p for p in expected if p not in router_paths)
missing_seed = sorted(p for p in expected if p not in seed_paths)
print(f"矩阵菜单: {len(expected)}")
print(f"路由路径: {len(router_paths)}")
print(f"种子路径命中: {len(expected & seed_paths)}")
if missing_router:
    print("路由缺失:", ", ".join(missing_router))
if missing_seed:
    print("种子缺失:", ", ".join(missing_seed))
if missing_router or missing_seed:
    raise SystemExit(1)
print("菜单矩阵、路由、种子路径一致")
