#!/usr/bin/env python3
"""Builds the static website in site/ (home, privacy policy, terms) from the app's own strings, English and Arabic.
Run from the repo root: python3 tools/build_site.py"""
import re, html, pathlib

def load(path):
    t = pathlib.Path(path).read_text(encoding="utf-8")
    d = {}
    for m in re.finditer(r'<string name="([^"]+)"[^>]*>(.*?)</string>', t, re.S):
        v = m.group(2).replace("\\'", "'").replace('\\"', '"')
        d[m.group(1)] = v
    return d

EN = load("app/src/main/res/values/strings.xml")
AR = load("app/src/main/res/values-ar/strings.xml")

# The update check is described here first; the in-app policy gets the same block with the update feature.
EN["pp_5_title"] = "Checking for updates"
EN["pp_5_body"] = "Pill Box needs an internet connection only to look for new versions (about once a week, and when you press Check for updates) and for the optional Google Drive backup. The check asks GitHub for the latest version number. It sends no personal data, no medicines and no device ID. GitHub can see your phone's internet address, as with any download. You can turn it off in Settings."
AR["pp_5_title"] = "البحث عن تحديثات"
AR["pp_5_body"] = "يحتاج التطبيق إلى الإنترنت فقط للبحث عن إصدارات جديدة (مرة في الأسبوع تقريباً، وعند الضغط على «التحقق من التحديثات») ولنسخة Google Drive الاختيارية. يسأل التطبيق موقع GitHub عن رقم أحدث إصدار. لا يُرسل أي بيانات شخصية ولا أدوية ولا معرّف الجهاز. يستطيع GitHub رؤية عنوان الإنترنت لهاتفك كما في أي تنزيل. يمكنك إيقاف ذلك من الإعدادات."
EN["pp_3_body"] = EN["pp_3_body"].replace("Pill Box works offline. Nothing is sent unless", "Pill Box works offline. Apart from checking for updates (see below), nothing is sent unless")
AR["pp_3_body"] = AR["pp_3_body"].replace("التطبيق يعمل بدون إنترنت. لا يُرسل أي شيء إلا إذا", "التطبيق يعمل بدون إنترنت. وباستثناء البحث عن التحديثات (انظر أدناه) لا يُرسل أي شيء إلا إذا")

EN["legal_updated"] = "Last updated 21 Oct 2026"
AR["legal_updated"] = "آخر تحديث ٢١ أكتوبر ٢٠٢٦"

REPO = "https://github.com/m-damer/pillbox"
RELEASES = REPO + "/releases/latest"

CSS = """
:root{--bg:#F6F7FB;--card:#fff;--text:#16192B;--muted:#4A5070;--accent:#3B3FC4;--border:#DDE1F2}
@media (prefers-color-scheme:dark){:root{--bg:#0F1220;--card:#1B2033;--text:#EDEFFA;--muted:#AEB5D3;--accent:#B4AFFF;--border:#2C3350}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--text);font:18px/1.7 system-ui,-apple-system,"Segoe UI",Roboto,"Noto Sans Arabic",sans-serif}
main{max-width:720px;margin:0 auto;padding:24px 16px 64px}
h1{font-size:2rem;line-height:1.3;margin:.2em 0 .3em}
h2{font-size:1.25rem;margin:1.6em 0 .3em}
p{margin:.4em 0}
.muted{color:var(--muted)}
.card{background:var(--card);border:1px solid var(--border);border-radius:18px;padding:8px 20px 16px;margin-top:20px}
a{color:var(--accent)}
.btn{display:inline-block;background:var(--accent);color:#fff;text-decoration:none;font-weight:700;padding:14px 24px;border-radius:999px;margin:12px 8px 0 0;min-height:48px}
@media (prefers-color-scheme:dark){.btn{color:#0F1220}}
nav{display:flex;flex-wrap:wrap;gap:8px 20px;margin-bottom:8px}
nav a{padding:12px 0;min-height:48px}
section[lang]{margin-top:40px;padding-top:8px}
section[lang=ar]{direction:rtl;text-align:right}
.btn:focus-visible,a:focus-visible{outline:3px solid var(--accent);outline-offset:3px}
"""

def page(title, intro_en, body_en, intro_ar, body_ar, name):
    nav = '<nav><a href="index.html">Home · الرئيسية</a><a href="privacy.html">Privacy policy · سياسة الخصوصية</a><a href="terms.html">Terms of use · شروط الاستخدام</a></nav>'
    return f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>{html.escape(title)}</title>
<meta name="description" content="Pill Box is a free, offline medicine reminder for Android with large, readable text. Arabic and English.">
<style>{CSS}</style>
</head>
<body>
<main>
{nav}
<section lang="en" dir="ltr">
{intro_en}
{body_en}
</section>
<section lang="ar" dir="rtl">
{intro_ar}
{body_ar}
</section>
</main>
</body>
</html>
"""

def blocks(D, keys):
    out = '<div class="card">'
    for k in keys:
        out += f"<h2>{html.escape(D[k+'_title'])}</h2><p>{html.escape(D[k+'_body'])}</p>"
    return out + "</div>"

def home():
    def intro(D, dl, desc):
        return f'<h1>{html.escape(D["app_name"])}</h1><p>{html.escape(desc)}</p><p><a class="btn" href="{RELEASES}">{html.escape(dl)}</a></p>'
    en = intro(EN, "Download for Android", "A free medicine reminder for Android with large, readable text. It works offline, has no account and no ads, and keeps your data on your phone.")
    ar = intro(AR, "تنزيل لأندرويد", "تطبيق مجاني لتذكير الدواء على أندرويد بخط كبير وواضح. يعمل بدون إنترنت، بلا حساب ولا إعلانات، وتبقى بياناتك على هاتفك.")
    en += f'<div class="card"><h2>{html.escape(EN["ab_privacy"])}</h2><p>{html.escape(EN["ab_privacy_short"])}</p><p><a href="privacy.html">{html.escape(EN["pp_title"])}</a> · <a href="terms.html">{html.escape(EN["tm_title"])}</a></p><p class="muted">{html.escape(EN["ab_disclaimer"])}</p></div>'
    ar += f'<div class="card"><h2>{html.escape(AR["ab_privacy"])}</h2><p>{html.escape(AR["ab_privacy_short"])}</p><p><a href="privacy.html">{html.escape(AR["pp_title"])}</a> · <a href="terms.html">{html.escape(AR["tm_title"])}</a></p><p class="muted">{html.escape(AR["ab_disclaimer"])}</p></div>'
    return page("Pill Box - Medicine reminder · علبة الدواء", "", en, "", ar, "index")

def legal(titlekey, keys, fname):
    def intro(D):
        return f'<h1>{html.escape(D[titlekey])}</h1><p class="muted">{html.escape(D["legal_updated"])}</p>'
    return page(EN[titlekey] + " - Pill Box", intro(EN), blocks(EN, keys), intro(AR), blocks(AR, keys), fname)

out = pathlib.Path("site")
out.mkdir(exist_ok=True)
(out / "index.html").write_text(home(), encoding="utf-8")
(out / "privacy.html").write_text(legal("pp_title", ["pp_1", "pp_2", "pp_3", "pp_5", "pp_4"], "privacy"), encoding="utf-8")
(out / "terms.html").write_text(legal("tm_title", ["tm_1", "tm_2", "tm_3", "tm_4"], "terms"), encoding="utf-8")
(out / ".nojekyll").write_text("")
print("site built")
