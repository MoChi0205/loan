#!/usr/bin/env python3
"""显式 Nacos 云库夹具 + 正常密码登录，集中验证敏感查看链路（仅开发环境）。

运行必须指定 --confirm-test-data；验证码答案由操作者看图片输入，不读 Redis。
只新增本次专用客户及其审计，不修改已有客户/额度配置/账号密码。
输出不包含令牌、手机号、密码或密钥。测试夹具保留便于追溯。
"""
import argparse
import base64
import concurrent.futures
import datetime
import hashlib
import importlib.util
import json
import os
import subprocess
import urllib.request
import urllib.error
import uuid
from pathlib import Path
import pymysql

ROOT = Path(__file__).resolve().parents[1]
BASE = os.environ.get("LOAN_TEST_BASE", "http://127.0.0.1:9088/loan/api")


def api(path, body=None, token=None, client_type="WEB"):
    headers = {"Content-Type": "application/json", "X-Client-Type": client_type}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, headers=headers,
                                 data=None if body is None else json.dumps(body).encode())
    try:
        with urllib.request.urlopen(req, timeout=60) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        return json.load(error)


def ok(path, body=None, token=None):
    result = api(path, body, token)
    if result.get("code") != 0:
        raise AssertionError(f"{path}: code={result.get('code')} message={result.get('message')}")
    return result["data"]


def login(account):
    password = os.environ.get("LOAN_TEST_PASSWORD")
    if not password:
        raise RuntimeError("必须显式设置 LOAN_TEST_PASSWORD，脚本不提供默认登录口令")
    captcha = ok("/auth/captcha")
    print(json.dumps({"captchaFor": account, "imageBase64": captcha["imageBase64"]}), flush=True)
    answer = input().strip()
    public_key = ok("/auth/public-key")["publicKey"]
    encrypted = subprocess.run(["node", "-e", "const c=require('crypto');let s='';process.stdin.on('data',d=>s+=d);process.stdin.on('end',()=>{const x=JSON.parse(s);const k='-----BEGIN PUBLIC KEY-----\\n'+x.key+'\\n-----END PUBLIC KEY-----';process.stdout.write(c.publicEncrypt({key:k,padding:c.constants.RSA_PKCS1_PADDING},Buffer.from(x.password)).toString('base64'));});"],
                               input=json.dumps({"key": public_key, "password": password}),
                               text=True, capture_output=True, check=True).stdout
    result = ok("/auth/password-login", {"account": account, "password": encrypted,
                 "accountType": "STAFF", "captchaId": captcha["captchaId"], "captchaCode": answer})
    print(json.dumps({"loggedIn": account, "role": result["user"]["roleCode"]}), flush=True)
    return result["token"]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--confirm-test-data", action="store_true")
    args = parser.parse_args()
    if not args.confirm_test_data:
        parser.error("必须显式确认仅用于开发测试数据")
    spec = importlib.util.spec_from_file_location("nacos", ROOT / "scripts/apply-sql-nacos.py")
    nacos = importlib.util.module_from_spec(spec); spec.loader.exec_module(nacos)
    _, _, host, port, database, user, password = nacos.load_dsn(True)
    props = nacos.parse_properties(nacos.fetch_config(nacos.required_env("NACOS_SERVER_ADDR"),
                                      nacos.required_env("NACOS_NAMESPACE"), "loan", "application.properties"))
    if not props.get("aes.key"):
        raise AssertionError("所选 Nacos 缺少 aes.key")
    tokens = {role: login(account) for role, account in
              [("adviser", "ADV001"), ("manager", "DEPT001"), ("boss", "BOSS001")]}
    connection = pymysql.connect(host=host, port=port, user=user, password=password,
                                 database=database, charset="utf8mb4", autocommit=True)
    run = "sv" + datetime.datetime.now().strftime("%y%m%d%H%M%S") + uuid.uuid4().hex[:4]
    summary = {"run": run, "checks": []}

    def check(label, condition):
        if not condition:
            raise AssertionError(label)
        summary["checks"].append(label)
        print(json.dumps({"passed": label}, ensure_ascii=False), flush=True)

    def fixtures(owner, count):
        codes = [run + owner.lower() + str(i) for i in range(count)]
        phones = ["199" + str(int(uuid.uuid4().hex[:12], 16) % 100000000).zfill(8) for _ in codes]
        encryption = subprocess.run(["node", "-e", "const c=require('crypto');let s='';process.stdin.on('data',d=>s+=d);process.stdin.on('end',()=>{const x=JSON.parse(s),k=c.createHash('sha256').update(x.key).digest();process.stdout.write(JSON.stringify(x.phones.map(p=>{const iv=c.randomBytes(16),e=c.createCipheriv('aes-256-cbc',k,iv);return Buffer.concat([iv,e.update(p),e.final()]).toString('base64')})));});"],
                        input=json.dumps({"key": props["aes.key"], "phones": phones}), text=True, capture_output=True, check=True)
        encrypted = json.loads(encryption.stdout)
        with connection.cursor() as cur:
            cur.executemany("INSERT INTO t_client_profile (client_code,customer_group,contact_name,phone,phone_plain,phone_hash,owner_staff_code,source,customer_tag,status,created_by,created_at) VALUES (%s,'PERSONAL',%s,%s,%s,%s,%s,'MANUAL','NEW','ACTIVE',%s,NOW())",
                [(code, "敏感权限验收" + str(i), enc, phone, hashlib.sha256(phone.encode()).hexdigest(), owner, run)
                 for i, (code, phone, enc) in enumerate(zip(codes, phones, encrypted))])
        return codes

    def mini_sms_login(phone):
        while True:
            captcha = ok("/auth/captcha")
            print(json.dumps({"captchaFor": "H5客户短信登录", "imageBase64": captcha["imageBase64"]}), flush=True)
            answer = input().strip()
            if answer != "refresh":
                break
        sent = ok("/sms/send-code", {"phone": phone, "scene": "LOGIN",
                  "captchaId": captcha["captchaId"], "captchaCode": answer})
        if not sent.get("devCode"):
            raise AssertionError("当前环境未允许显示模拟短信码，不读取Redis绕过")
        result = ok("/mini/auth/login", {"phone": phone, "code": sent["devCode"]})
        payload = result["token"].split(".")[1]
        claims = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
        check("H5短信登录签发MINI_APP端令牌", claims.get("clientType") == "MINI_APP")
        check("H5登录响应手机号脱敏", result["user"].get("phone") == phone[:3] + "****" + phone[-4:])
        denied = api(reveal_path, {"clientCode": clients[0]}, result["token"], "WEB")
        check("真实H5令牌伪造Web头仍拒绝手机号原值", denied.get("code") == 2001)

    reveal_path = "/admin/lead/sensitive/client/apply-view"
    def reveal(token, code):
        return ok(reveal_path, {"clientCode": code}, token)

    try:
        adviser, manager, boss = (tokens[k] for k in ("adviser", "manager", "boss"))
        quota = ok("/admin/lead/sensitive/quota", token=adviser)
        remaining = quota["remaining"]
        check("顾问测试额度规模受控", 0 <= remaining <= 100)
        clients = fixtures("ADV001", remaining + 10)
        for code in clients[:max(0, remaining - 1)]:
            check("额度内首次解锁", reveal(adviser, code)["revealed"])
        pending_clients = clients[max(0, remaining - 1):max(0, remaining - 1) + 8]
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
            results = list(executor.map(lambda code: reveal(adviser, code), pending_clients))
        check("八并发不突破剩余额度", sum(bool(r["revealed"]) for r in results) == min(remaining, 1))
        check("超额请求全部进入审批且不返回原值", all(r.get("approvalNo") and not r.get("phonePlain") for r in results if not r["revealed"]))
        first = next((code for code, result in zip(pending_clients, results) if result["revealed"]), None)
        used = ok("/admin/lead/sensitive/quota", token=adviser)["used"]
        check("并发后未增加未经批准的解锁", used == max(quota["used"], quota["limit"]))
        if first:
            with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
                repeats = list(executor.map(lambda _: reveal(adviser, first), range(8)))
            check("同日重复八并发不重复扣次数", all(r["revealed"] for r in repeats) and ok("/admin/lead/sensitive/quota", token=adviser)["used"] == used)
        index = next(i for i,r in enumerate(results) if not r["revealed"])
        approval, client = results[index]["approvalNo"], pending_clients[index]
        pending = ok("/admin/lead/sensitive/approval/pending", token=manager)
        check("部门经理可看到顾问超额申请与中文姓名", any(r["approvalNo"] == approval and r.get("applicantName") and r.get("clientName") for r in pending))
        denied = api("/admin/lead/sensitive/approval/" + approval + "/audit", {"approve": True}, adviser)
        check("顾问不能自审", denied.get("code") == 2001)
        path = "/admin/lead/sensitive/approval/" + approval + "/audit"
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
            audits = list(executor.map(lambda _: api(path, {"approve": True}, manager), range(2)))
        check("并发审批仅一次成功", sum(r.get("code") == 0 for r in audits) == 1)
        check("经理批准后顾问重新解锁成功", reveal(adviser, client)["revealed"])
        forbidden = api(reveal_path, {"clientCode": clients[0]}, manager, "MINI_APP")
        check("请求头不能改变签名Web端类型", forbidden.get("code") == 0)
        manager_quota = ok("/admin/lead/sensitive/quota", token=manager)
        check("经理测试额度规模受控", manager_quota["remaining"] <= 100)
        manager_clients = fixtures("DEPT001", manager_quota["remaining"] + 2)
        for code in manager_clients[:manager_quota["remaining"]]:
            check("经理额度内无需审批", reveal(manager, code)["revealed"])
        blocked = reveal(manager, manager_clients[-1])
        check("经理超额进入审批且不泄漏原值", not blocked["revealed"] and blocked.get("approvalNo") and not blocked.get("phonePlain"))
        with connection.cursor() as cur:
            cur.execute("SELECT approval_stage FROM t_sensitive_view_approval WHERE approval_no=%s", (blocked["approvalNo"],))
            check("经理超额审批阶段为老板审核", cur.fetchone()[0] == "BOSS_REVIEW")
        ok("/admin/lead/sensitive/approval/" + blocked["approvalNo"] + "/audit", {"approve": True}, boss)
        check("老板批准后经理可解锁", reveal(manager, manager_clients[-1])["revealed"])
        check("老板无限额", ok("/admin/lead/sensitive/quota", token=boss)["unlimited"])
        for _ in range(3):
            check("老板直接查看", reveal(boss, clients[0])["revealed"])
        with connection.cursor() as cur:
            cur.execute("SELECT COUNT(*) FROM t_sensitive_view_log WHERE user_no='BOSS001' AND client_code=%s AND view_date=CURDATE()", (clients[0],))
            check("老板重复查看只有一条每日客户留痕", cur.fetchone()[0] == 1)
        # 使用无生产匹配价值的专用自然键，保留作幂等验收证据。
        batch = {"channelCode": run, "customerGroup": "ENTERPRISE", "listType": "LOCAL_BLACK", "keys": ["911" + uuid.uuid4().hex[:15].upper(), "912" + uuid.uuid4().hex[:15].upper()]}
        batch_path = "/admin/channel-user-list"
        check("名单批量首次写入两条", ok(batch_path, batch, boss) == 2)
        check("名单批量重复写入零条", ok(batch_path, batch, boss) == 0)
        cities = [{"province": "验收专用", "city": run + "a"}, {"province": "验收专用", "city": run + "b"}]
        city_path = "/admin/product-city/" + run
        check("城市批量首次写入两条", ok(city_path, cities, boss) == 2)
        check("城市批量重复写入零条", ok(city_path, cities, boss) == 0)
        batch["keys"] = ["913" + uuid.uuid4().hex[:15].upper(), "914" + uuid.uuid4().hex[:15].upper()]
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
            inserted = list(executor.map(lambda _: ok(batch_path, batch, boss), range(2)))
        check("并发相同名单批次总共只插入两条", sum(inserted) == 2)
        with connection.cursor() as cur:
            cur.execute("SELECT COUNT(*) FROM t_notification WHERE user_no='ADV001' AND related_id=%s", (approval,))
            check("审批结果通知仅生成一次", cur.fetchone()[0] == 1)
            cur.execute("SELECT phone_plain FROM t_client_profile WHERE client_code=%s", (clients[-1],))
            phone = cur.fetchone()[0]
        mini_sms_login(phone)
        with connection.cursor(pymysql.cursors.DictCursor) as cur:
            for table, predicate, params in [
                    ("t_sensitive_view_log", "user_no=%s AND view_date=CURDATE()", ("ADV001",)),
                    ("t_sensitive_view_approval", "approval_no=%s", (approval,)),
                    ("t_channel_user_list", "channel_code=%s AND customer_group='ENTERPRISE' AND list_type='LOCAL_BLACK' AND list_key=%s", (run, batch["keys"][0]))]:
                cur.execute("EXPLAIN SELECT * FROM " + table + " WHERE " + predicate, params)
                plans = cur.fetchall()
                check("索引命中:" + table, all(p["key"] is not None and p["type"] not in ("ALL", "index") for p in plans))
                summary.setdefault("queryPlans", []).append({"table": table, "type": plans[0]["type"], "key": plans[0]["key"], "rows": plans[0]["rows"]})
        summary["passed"] = True
    finally:
        connection.close()
        output = ROOT / "logs" / (run + "-sensitive-verification.json")
        output.parent.mkdir(exist_ok=True)
        # 验收结果不含个人信息或认证材料。
        output.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
        print(json.dumps(summary, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
