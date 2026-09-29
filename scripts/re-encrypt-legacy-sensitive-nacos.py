#!/usr/bin/env python3
"""在所选 Nacos 数据源上将历史手机号明文重加密为当前 AES/CBC 格式。"""
import base64, hashlib, importlib.util, os, secrets, subprocess, sys
import pymysql

spec = importlib.util.spec_from_file_location('nacos_sql', os.path.join(os.path.dirname(__file__), 'apply-sql-nacos.py'))
mod = importlib.util.module_from_spec(spec); spec.loader.exec_module(mod)
if '--confirm-remote' not in sys.argv:
    raise SystemExit('必须显式传入 --confirm-remote')
addr = mod.required_env('NACOS_SERVER_ADDR'); namespace = mod.required_env('NACOS_NAMESPACE')
_, _, host, port, database, user, password = mod.load_dsn(allow_placeholder='--allow-placeholder-password' in sys.argv)
props = mod.parse_properties(mod.fetch_config(addr, namespace, os.environ.get('NACOS_GROUP','loan'), 'application.properties'))
aes_key = props.get('aes.key','')
if not aes_key: raise SystemExit('Nacos 配置缺少 aes.key')
key = hashlib.sha256(aes_key.encode()).hexdigest()

def encrypt(value):
    iv = secrets.token_bytes(16)
    proc = subprocess.run(['openssl','enc','-aes-256-cbc','-K',key,'-iv',iv.hex()], input=value.encode(), stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
    return base64.b64encode(iv + proc.stdout).decode()

conn = pymysql.connect(host=host, port=port, user=user, password=password, database=database, charset='utf8mb4', autocommit=False)
changed = 0
try:
    with conn.cursor() as cur:
        for table in ('t_lead','t_sms_record'):
            cur.execute(f"SELECT id, phone FROM `{table}` WHERE phone REGEXP '^1[0-9]{{10}}$' FOR UPDATE")
            rows = cur.fetchall()
            for row_id, plain in rows:
                cur.execute(f"UPDATE `{table}` SET phone=%s WHERE id=%s", (encrypt(plain), row_id))
                changed += 1
    conn.commit()
finally:
    conn.close()
print('受控重加密完成，记录数=', changed)
