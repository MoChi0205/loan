#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成贷款小程序图标族：品牌磁贴 PNG + 单色 plain PNG。
输出：loan-mini/static/icons/<name>.png 与 <name>-<key>.png
"""
import os, subprocess

OUT = "/Users/admin/Downloads/loan-main/loan-mini/static/icons"
TMP = "/tmp/icongen/svg"
os.makedirs(OUT, exist_ok=True)
os.makedirs(TMP, exist_ok=True)

# 品牌磁贴色（与 App.vue 设计令牌同族）
TILE = {
    'home': '#2443C2', 'match': '#6D4AED', 'chart': '#F0920C', 'order': '#11A86B',
    'bank': '#2443C2', 'users': '#0E9CB0', 'mine': '#4F46E5', 'check': '#EF4D5E',
    'search': '#2443C2', 'wechat': '#11A86B', 'bolt': '#F0920C', 'support': '#0E9CB0',
    'list': '#2443C2', 'share': '#6D4AED', 'enterprise': '#4F46E5', 'person': '#4F46E5',
    'refresh': '#2443C2', 'alert': '#F0920C', 'trend': '#11A86B', 'doc': '#2443C2',
    'file': '#F0920C', 'photo': '#0E9CB0', 'lock': '#6D4AED', 'arrow': '#2443C2',
}

# 字形库：48x48 视窗，白色线性（{FG} 为前景色占位，plain 模式替换为单色）
G = {
'home': '''
<path d="M12.5 24.5 L24 15 L35.5 24.5"/>
<path d="M16 23.5 V33 a2.5 2.5 0 0 0 2.5 2.5 H29.5 a2.5 2.5 0 0 0 2.5 -2.5 V23.5"/>
<path d="M21 35.5 V28.5 H27 V35.5"/>''',
'match': '''
<circle cx="19.5" cy="24" r="7.2"/>
<circle cx="28.5" cy="24" r="7.2"/>
<circle cx="24" cy="24" r="2.4" fill="{FG}" stroke="none"/>''',
'chart': '''
<path d="M13.5 13.5 V33 a2 2 0 0 0 2 2 H35"/>
<path d="M19.5 29.5 V24"/>
<path d="M25 29.5 V19"/>
<path d="M30.5 29.5 V22.5"/>''',
'order': '''
<rect x="15.5" y="12" width="17" height="24" rx="3"/>
<path d="M20.5 18.5 H27.5" />
<path d="M20.5 23.5 H27.5" />
<path d="M20.5 30 L23 32.5 L28 27.5"/>''',
'bank': '''
<path d="M11.5 21.5 L24 13.5 L36.5 21.5"/>
<path d="M15.5 25 V31.5 M21.2 25 V31.5 M26.8 25 V31.5 M32.5 25 V31.5"/>
<path d="M12.5 35.5 H35.5"/>''',
'users': '''
<circle cx="19.5" cy="19.5" r="4.4"/>
<path d="M11.5 32.5 C11.5 26.5 27.5 26.5 27.5 32.5"/>
<circle cx="30.5" cy="21.5" r="3.6"/>
<path d="M29.5 32.5 C29.5 28.5 36.8 28.5 36.8 32.5"/>''',
'mine': '''
<circle cx="24" cy="24" r="11"/>
<circle cx="24" cy="20.5" r="3.6"/>
<path d="M17 32.5 C17.8 27.5 30.2 27.5 31 32.5"/>''',
'check': '''
<circle cx="24" cy="24" r="11"/>
<path d="M18.5 24.5 L22.5 28.5 L30 19.5"/>''',
'search': '''
<circle cx="21" cy="21" r="8.5"/>
<path d="M27.5 27.5 L34.5 34.5"/>''',
'wechat': '''
<ellipse cx="19" cy="21" rx="8.5" ry="6.2"/>
<ellipse cx="29.5" cy="28" rx="6.5" ry="4.8"/>
<circle cx="16.5" cy="20" r="1.3" fill="{FG}" stroke="none"/>
<circle cx="21.5" cy="20" r="1.3" fill="{FG}" stroke="none"/>
<circle cx="27.5" cy="27.5" r="1.1" fill="{FG}" stroke="none"/>
<circle cx="31.5" cy="27.5" r="1.1" fill="{FG}" stroke="none"/>''',
'bolt': '''
<path d="M26.5 11.5 L15.5 26 H23 L21.5 36.5 L33.5 21.5 H25.5 Z" fill="{FG}" stroke="none"/>''',
'support': '''
<path d="M15.5 26 V22 C15.5 15.5 32.5 15.5 32.5 22 V26"/>
<rect x="13" y="25.5" width="5.2" height="9" rx="2.6"/>
<rect x="29.8" y="25.5" width="5.2" height="9" rx="2.6"/>
<path d="M32 35 V33.5 C32 30.5 36.5 30.5 36.5 33.5"/>''',
'list': '''
<circle cx="13.5" cy="16.5" r="1.8" fill="{FG}" stroke="none"/>
<circle cx="13.5" cy="24" r="1.8" fill="{FG}" stroke="none"/>
<circle cx="13.5" cy="31.5" r="1.8" fill="{FG}" stroke="none"/>
<path d="M18 16.5 H34.5"/>
<path d="M18 24 H34.5"/>
<path d="M18 31.5 H34.5"/>''',
'share': '''
<circle cx="24" cy="13.5" r="4.2"/>
<circle cx="13.5" cy="33" r="4.2"/>
<circle cx="34.5" cy="33" r="4.2"/>
<path d="M21 16.5 L16.5 29.5"/>
<path d="M27 16.5 L31.5 29.5"/>''',
'enterprise': '''
<path d="M13.5 35.5 V13.5 a1.5 1.5 0 0 1 1.5 -1.5 H25 a1.5 1.5 0 0 1 1.5 1.5 V35.5"/>
<path d="M26.5 20.5 H33 a1.5 1.5 0 0 1 1.5 1.5 V35.5"/>
<path d="M11 35.5 H37"/>
<path d="M17.5 17.5 H20 M22.5 17.5 H25 M17.5 22 H20 M22.5 22 H25 M17.5 26.5 H20 M22.5 26.5 H25"/>
<path d="M29.5 25 H31.5 M29.5 29.5 H31.5"/>''',
'person': '''
<rect x="12" y="14.5" width="24" height="19" rx="2.8"/>
<circle cx="20" cy="21.8" r="2.9"/>
<path d="M16 28.8 C16.5 25.2 23.5 25.2 24 28.8"/>
<path d="M28.5 20.5 H32.5"/>
<path d="M28.5 25 H32.5"/>''',
'refresh': '''
<path d="M33 17.5 A10.5 10.5 0 1 0 34.5 27"/>
<path d="M33.5 12 V18.5 H27"/>''',
'alert': '''
<path d="M24 13.5 L35.5 32.5 a1.6 1.6 0 0 1 -1.4 2.4 H9.9? "/>''',
}

# alert 三角需闭合精确，单独覆盖（避免上面占位）
G['alert'] = '''
<path d="M24 13.6 L35.7 32.9 A1.9 1.9 0 0 1 34 35.6 H14 A1.9 1.9 0 0 1 12.3 32.9 Z"/>
<path d="M24 20.5 V26.5"/>
<circle cx="24" cy="30.6" r="1.7" fill="{FG}" stroke="none"/>'''
G['trend'] = '''
<path d="M13.5 32 L21 24.5 L26 28.5 L34.5 16.5"/>
<path d="M29.5 16.5 H34.5 V21.5"/>'''
G['doc'] = '''
<path d="M16.5 12.5 H26 L32 18.5 V33.5 a2 2 0 0 1 -2 2 H16.5 a2 2 0 0 1 -2 -2 V14.5 a2 2 0 0 1 2 -2 Z"/>
<path d="M25.5 12.5 V19 H32"/>
<path d="M20 25 H28.5"/>
<path d="M20 29.5 H28.5"/>'''
G['file'] = '''
<path d="M14 16 a2.5 2.5 0 0 1 2.5 -2.5 H21 L24.5 18 H33.5 a2 2 0 0 1 2 2 V32.5 a2 2 0 0 1 -2 2 H16.5 a2.5 2.5 0 0 1 -2.5 -2.5 Z"/>
<path d="M19.5 24.5 H30"/>
<path d="M19.5 29 H30"/>'''
G['photo'] = '''
<rect x="13" y="14.5" width="22" height="19" rx="2.8"/>
<circle cx="19.5" cy="21" r="2.1"/>
<path d="M13.8 30.5 L21.5 22.5 L26.5 27.5 L30.5 23 L34.2 26.5"/>'''
G['lock'] = '''
<rect x="15" y="21.5" width="18" height="13.5" rx="2.8"/>
<path d="M18.5 21.5 V17.5 C18.5 13.5 29.5 13.5 29.5 17.5 V21.5"/>
<circle cx="24" cy="27.5" r="2.1" fill="{FG}" stroke="none"/>'''
G['arrow'] = '''
<path d="M16.5 24 H31.5"/>
<path d="M26.5 18.5 L32.5 24 L26.5 29.5"/>'''

PLAIN = {  # 单色 plain：键 = 文件名后缀
    'gray':   '#5B6678',   # --text-secondary（搜索栏）
    'brand':  '#2443C2',   # --brand-deep（tab 选中）
    'gold':   '#FFB020',   # --gold
    'info':   '#0EA8BE',   # --info
    'success':'#11A86B',   # --success
    'white':  'rgba(255,255,255,0.78)',
}

# plain 输出计划：全量 gray + tabbar/搜索实际用到的组合
PLAN = {n: ['gray'] for n in TILE}
for combo in [
    ('home', 'brand'), ('match', 'info'), ('match', 'gold'), ('match', 'brand'),
    ('chart', 'gold'), ('chart', 'brand'), ('order', 'success'), ('order', 'brand'),
    ('mine', 'info'), ('mine', 'brand'), ('bank', 'gold'), ('bank', 'brand'),
    ('users', 'success'), ('users', 'brand'), ('lock', 'white'),
]:
    PLAN[combo[0]].append(combo[1])

def svg_doc(glyph, fg, tile=None):
    tile_svg = f'<rect x="3" y="3" width="42" height="42" rx="13" fill="{tile}"/>' if tile else ''
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48">'
        + tile_svg
        + '<g fill="none" stroke="%s" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">%s</g>'
          % (fg, glyph.replace('{FG}', fg))
        + '</svg>'
    )

def render(name, doc):
    p = os.path.join(TMP, name + '.svg')
    with open(p, 'w') as f:
        f.write(doc)
    subprocess.run(['rsvg-convert', '-w', '144', '-h', '144', '-o', os.path.join(OUT, name + '.png'), p], check=True)

count = 0
for n, tile in TILE.items():
    render(n, svg_doc(G[n], '#FFFFFF', tile)); count += 1
for n, keys in PLAN.items():
    for k in keys:
        render(f'{n}-{k}', svg_doc(G[n], PLAIN[k])); count += 1
print('generated:', count)
