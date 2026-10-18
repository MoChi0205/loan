"""校验「菜单授权 ↔ 接口授权」的模块级一致性（P0 另一半）。

链路：menu_matrix.py 角色可见菜单 -> 前端路由 component -> import 的 api 模块 ->
     api 文件 url 的后端接口模块段 -> 对照 ApiPermissionSyncService 的
     ADVISER_APIS / MANAGER_APIS 授权。

发现「角色能看到菜单，但该菜单依赖的接口模块对该角色零授权」的漂移。
仅覆盖管理端（/api/admin/**）；mini 端走 typeRules，不在本校验范围。
"""
import ast
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def load_menus():
    ns = {}
    exec(compile(ast.parse((ROOT / "scripts/menu_matrix.py").read_text(encoding="utf-8")),
                 "menu_matrix.py", "exec"), ns)
    return ns["MENUS"]


def router_path_comp():
    """逐行扫描 router，得到 {path: component文件名(不含views/)}。"""
    router = (ROOT / "loan-web/src/router/index.js").read_text(encoding="utf-8")
    mapping = {}
    cur = None
    for line in router.splitlines():
        m = re.search(r"path:\s*'([^']+)'", line)
        if m:
            p = m.group(1)
            cur = p if p.startswith("/") else "/" + p
        m = re.search(r"import\('@/views/([^']+)'\)", line)
        if m and cur:
            mapping[cur] = m.group(1)
    return mapping


def component_api_mods():
    """{component文件名: set(前端api模块名)}。"""
    src = ROOT / "loan-web/src"
    mods = {}
    for f in (src / "views").rglob("*.vue"):
        text = f.read_text(encoding="utf-8")
        names = set(re.findall(r"from '@/api/([A-Za-z0-9_-]+)'", text))
        if names:
            mods[f"{f.parent.name}/{f.name}"] = names
    return mods


def api_module_backend_mods():
    """{前端api文件名: set(后端接口模块段)}，从 url 提取 /api/admin/{mod}/。"""
    api_dir = ROOT / "loan-web/src/api"
    mods = {}
    for f in api_dir.glob("*.js"):
        if "test" in f.name:
            continue
        text = f.read_text(encoding="utf-8")
        out = set()
        for m in re.finditer(r"url:\s*'/(?:api/admin/|api/)([^/'{]+)", text):
            seg = m.group(1)
            if seg == "lead":
                continue
            out.add(seg)
        # 敏感查看：/api/admin/lead/sensitive/* -> lead:sensitive
        if "/lead/sensitive/" in text:
            out.add("lead:sensitive")
        if out:
            mods[f.stem] = out
    return mods


def extract_api_arrays():
    """从 ApiPermissionSyncService 提取 ADVISER_APIS / MANAGER_APIS 的 apiKey。"""
    sync = (ROOT / "loan-service/src/main/java/com/loan/apiperm/service/ApiPermissionSyncService.java") \
        .read_text(encoding="utf-8")

    def grab(name):
        m = re.search(r"private static final String\[\] " + name + r" = \{(.*?)\};", sync, re.S)
        return set(re.findall(r'"([^"]+)"', m.group(1))) if m else set()
    return grab("ADVISER_APIS"), grab("MANAGER_APIS")


def mod_of(key):
    if key.startswith("lead:sensitive"):
        return "lead:sensitive"
    if key.startswith("mini:"):
        return None  # mini 端不纳入管理端菜单校验
    return key.split(":", 1)[0]


def main():
    menus = load_menus()
    path_comp = router_path_comp()
    comp_mods = component_api_mods()
    api_backend = api_module_backend_mods()
    adviser_apis, manager_apis = extract_api_arrays()
    manager_all = adviser_apis | manager_apis

    adviser_mods = {mod_of(k) for k in adviser_apis if mod_of(k)}
    manager_mods = {mod_of(k) for k in manager_all if mod_of(k)}

    roles_mods = {"ADVISER": adviser_mods, "DEPT_MANAGER": manager_mods}
    problems = []
    for role, allowed_mods in roles_mods.items():
        visible = [p for p, item in menus.items() if role in item.get("roles", set())]
        for p in sorted(visible):
            comp = path_comp.get(p)
            if not comp:
                continue
            for am in comp_mods.get(comp, set()):
                for bm in api_backend.get(am, set()):
                    if bm not in allowed_mods:
                        problems.append(f"{role} 可见菜单 {p} 依赖接口模块 {bm} 但零授权")

    print(f"ADVISER 授权接口 {len(adviser_apis)} 个，模块段 {len(adviser_mods)} 个")
    print(f"DEPT_MANAGER 授权接口 {len(manager_all)} 个，模块段 {len(manager_mods)} 个")
    if problems:
        print("\n=== 菜单↔接口漂移 ===")
        for x in sorted(set(problems)):
            print(" - " + x)
        raise SystemExit(1)
    print("\n菜单与接口授权模块级一致")


if __name__ == "__main__":
    main()
