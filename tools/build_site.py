#!/usr/bin/env python3
"""Builds the website in site/ (landing page, privacy policy, terms), Arabic first with an English switch.
Legal text comes from the app's own strings. Run from the repo root: python3 tools/build_site.py"""
import re, html, pathlib

def load(path):
    t = pathlib.Path(path).read_text(encoding="utf-8")
    d = {}
    for m in re.finditer(r'<string name="([^"]+)"[^>]*>(.*?)</string>', t, re.S):
        d[m.group(1)] = m.group(2).replace("\\'", "'").replace('\\"', '"')
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
DIRECT = REPO + "/releases/latest/download/PillBox.apk"
E = html.escape

def bi(ar, en, tag="span", cls=""):
    c = (" " + cls) if cls else ""
    return f'<{tag} class="ar{c}" lang="ar">{ar}</{tag}><{tag} class="en{c}" lang="en">{en}</{tag}>'

def img(name, ar_alt, en_alt, eager=False):
    lz = "" if eager else ' loading="lazy"'
    return (f'<img class="ar" src="img/ar/{name}.webp" width="540" height="1168" alt="{E(ar_alt)}"{lz}>'
            f'<img class="en" src="img/en/{name}.webp" width="540" height="1168" alt="{E(en_alt)}"{lz}>')

def phone(name, ar_alt, en_alt, eager=False):
    return f'<div class="phone">{img(name, ar_alt, en_alt, eager)}</div>'

LOGO = '<img src="logo.svg" width="40" height="40" alt="">'

def head(title_ar, title_en, desc_ar, desc_en):
    return f"""<!doctype html>
<html lang="ar" dir="rtl" data-lang="ar" data-title-ar="{E(title_ar)}" data-title-en="{E(title_en)}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>{E(title_ar)}</title>
<meta name="description" content="{E(desc_ar)}">
<meta property="og:title" content="{E(title_ar)}">
<meta property="og:description" content="{E(desc_ar)}">
<meta name="theme-color" content="#4338CA">
<link rel="preload" href="fonts/baloo.woff2" as="font" type="font/woff2" crossorigin>
<link rel="icon" href="logo.svg" type="image/svg+xml">
<link rel="stylesheet" href="style.css">
</head>
<body>
<header><div class="wrap">
<a class="brand" href="index.html">{LOGO}<span class="ar" lang="ar">علبة الدواء</span><span class="en" lang="en">Pill Box</span></a>
<div class="tools">
<div class="sizes" role="group" aria-label="Text size"><button type="button" aria-pressed="true">A</button><button type="button" aria-pressed="false">A</button><button type="button" aria-pressed="false">A</button></div>
<button type="button" id="lang" lang="en">English</button>
</div></div></header>
"""

def foot():
    made = f'<span class="ar" lang="ar">{E(AR["settings_made_with"])}</span><span class="en" lang="en">{E(EN["settings_made_with"])}</span>'
    return f"""<footer><div class="wrap">
<nav>{bi("<a href='privacy.html'>سياسة الخصوصية</a>", "<a href='privacy.html'>Privacy policy</a>")}{bi("<a href='terms.html'>شروط الاستخدام</a>", "<a href='terms.html'>Terms of use</a>")}{bi(f"<a href='{REPO}'>الشفرة المصدرية</a>", f"<a href='{REPO}'>Source code</a>")}</nav>
<div>{made}</div>
</div></footer>
<script src="site.js"></script>
</body></html>
"""

def feature(tone, flip, h_ar, h_en, p_ar, p_en, visual, bullets=None):
    li = ""
    if bullets:
        li = bi("<ul>" + "".join(f"<li>{a}</li>" for a, _ in bullets) + "</ul>", "<ul>" + "".join(f"<li>{e}</li>" for _, e in bullets) + "</ul>", "div")
    return f"""<section class="feature {tone}{' flip' if flip else ''}"><div class="wrap">
<div class="text">{bi(h_ar, h_en, "h2")}{bi(p_ar, p_en, "p")}{li}</div>
{visual}
</div></section>
"""

def home():
    out = head("علبة الدواء - تذكير بالدواء بخط كبير وواضح", "Pill Box - Medicine reminder with large, readable text",
               "علبة الدواء تطبيق مجاني لتذكير الدواء على أندرويد. خط كبير وواضح، يعمل بدون إنترنت، وبياناتك تبقى على هاتفك.",
               "Pill Box is a free medicine reminder for Android. Large, readable text, works offline, and your data stays on your phone.")
    out += f"""<main>
<section class="hero"><div class="wrap">
<div>
{bi("لا تفوّت جرعة دواء", "Never miss a dose", "h1")}
{bi("علبة الدواء تذكّرك بكل دواء في وقته، بخط كبير وواضح. مجانية، تعمل بدون إنترنت، وتبقى بياناتك على هاتفك.", "Pill Box reminds you of every medicine on time, in large, readable text. It is free, works offline, and keeps your data on your phone.", "p", "lead")}
<a class="btn" href="{DIRECT}" download>{bi("تنزيل لأندرويد", "Download for Android")}</a>
{bi("لأندرويد ٨ أو أحدث. مجاني، بلا حساب وبلا إعلانات.", "For Android 8 or newer. Free, no account, no ads.", "p", "note")}
</div>
<div class="sun">{phone("notification", "شاشة التذكير الكاملة عند موعد الدواء", "The full-screen reminder when a dose is due", True)}</div>
</div></section>

<section class="overview"><div class="wrap">
<div>{bi("تذكير يرنّ في وقته", "Reminders that ring on time", "h3")}{bi("ينبّهك التطبيق في الموعد بالضبط، حتى لو كان الهاتف مقفلاً. تضغط «أخذتها» أو «تخطَّ» لكل دواء.", "The app alerts you at the exact time, even when the phone is locked. Tap Take or Skip for each medicine.", "p")}</div>
<div>{bi("خط كبير وواضح", "Large, clear text", "h3")}{bi("كبّر الخط حتى ٢٠٠٪، مع تباين عالٍ ووضع داكن. صُمّم لمن يصعب عليهم قراءة الخط الصغير.", "Enlarge the text up to 200%, with high contrast and a dark theme. Made for people who find small text hard to read.", "p")}</div>
<div>{bi("بياناتك لك وحدك", "Your data stays yours", "h3")}{bi("بلا حساب وبلا إعلانات وبلا خادم. أدويتك ومواعيدك تبقى على هاتفك.", "No account, no ads, no server. Your medicines and times stay on your phone.", "p")}</div>
</div></section>
"""
    out += feature("morning", False, "يومك كله في شاشة واحدة", "Your whole day on one screen",
        "الأدوية مجمّعة حسب الوقت: صباحاً وظهراً ومساءً وليلاً. ترى ما أخذته وما بقي، وتتنقل بين الأيام لترى ما سبق وما هو قادم.",
        "Medicines are grouped by time of day: morning, noon, evening and night. You see what you took and what is left, and you can move between days to look back or ahead.",
        phone("today", "شاشة اليوم", "The Today screen"))
    out += feature("noon", True, "تذكير لا يفوتك", "A reminder you cannot miss",
        "في الموعد يظهر التنبيه حتى على شاشة القفل: خذها كلها بضغطة واحدة، أو أجّل ١٥ دقيقة، أو افتح الشاشة الكاملة لتأخذ كل دواء أو تتخطاه على حدة.",
        "At the exact time the alert appears, even on the lock screen: take everything with one tap, snooze for 15 minutes, or open the full screen to take or skip each medicine on its own.",
        phone("notification-shade", "تنبيه الدواء على شاشة القفل", "The medicine alert on the lock screen"))
    out += feature("evening", False, "إضافة دواء بخطوات بسيطة", "Add a medicine in a few simple steps",
        "اسم الدواء ونوعه وجرعته، ثم المواعيد: مرة أو عدة مرات في اليوم، أو كل عدد من الساعات، أو عند الحاجة فقط. وإن كانت العلبة قيد الاستعمال، قل للتطبيق كم بقي فيها.",
        "The name, type and dose, then the times: once or several times a day, every few hours, or only when needed. If the box is already in use, tell the app how many are left.",
        phone("add-med-1", "الخطوة الأولى من إضافة دواء", "Step one of adding a medicine"))
    out += feature("noon", True, "مقروء لكل العيون", "Readable for every eye",
        "كبّر الخط حتى ٢٠٠٪ فيتّسع كل شيء ويلتفّ النص بدل أن يُقطع. أزرار كبيرة وسهلة اللمس، وألوان واضحة، ووضع داكن لليل.",
        "Enlarge the text up to 200% and everything makes room: text wraps instead of being cut off. Large, easy-to-tap buttons, clear colours, and a dark theme for the evening.",
        f'<div class="pair">{phone("large-today", "شاشة اليوم بخط كبير جداً", "The Today screen with very large text")}{phone("today-dark", "شاشة اليوم في الوضع الداكن", "The Today screen in the dark theme")}</div>')
    out += feature("morning", False, "ينبّهك قبل أن ينتهي الدواء", "Warns you before a medicine runs out",
        "يتابع التطبيق ما بقي في العلبة وينبّهك قبل نفاده بأيام، لتشتري في الوقت المناسب ولا تنقطع عن دوائك.",
        "The app keeps track of what is left in the box and warns you days before it runs out, so you can buy more in time.",
        phone("refill-alert", "تنبيه قرب نفاد الدواء", "The low-stock alert"))
    out += feature("evening", True, "سجلّ وتقرير لطبيبك", "A record and a report for your doctor",
        "ترى ما أخذته وما فاتك. وعند زيارة الطبيب، صدّر تقريراً بصيغة PDF يعرض أدويتك والتزامك بها.",
        "See what you took and what you missed. Before a doctor's visit, export a PDF report of your medicines and how regularly you took them.",
        f'<div class="pair">{phone("history", "سجل الجرعات", "The dose history")}{phone("report-export", "تصدير تقرير للطبيب", "Exporting a report for the doctor")}</div>')
    out += feature("noon", False, "اعتنِ بمن تحب", "Look after the people you love",
        "أضف والديك أو أبناءك. لكل شخص قائمة أدويته وتذكيراته، وكل ذلك محفوظ على هاتفك وحده.",
        "Add your parents or children. Each person has their own medicines and reminders, all kept on your phone only.",
        phone("family", "قائمة أفراد العائلة", "The list of family members"))
    out += f"""<section class="more"><div class="wrap">
{bi("وأكثر من ذلك", "And more", "h2")}
{bi("<ul><li>أدوية عند الحاجة مع حدّ يومي وفاصل أدنى بين الجرعات</li><li>نسخ احتياطي أسبوعي تلقائي إلى مجلد تختاره، أو إلى Google Drive اختيارياً</li><li>عربي وإنجليزي، مع نظام ١٢ أو ٢٤ ساعة</li><li>تنبيه خاص للجرعة المتأخرة</li><li>إيقاف دواء مؤقتاً ثم استئنافه</li><li>أرقام عربية أو إنجليزية حسب اختيارك</li></ul>", "<ul><li>As-needed medicines with a daily limit and a minimum gap between doses</li><li>Automatic weekly backup to a folder you choose, or optionally to Google Drive</li><li>Arabic and English, with 12 or 24-hour time</li><li>A special alert for a late dose</li><li>Pause a medicine, then resume it</li><li>Arabic or Western digits, as you prefer</li></ul>", "div")}
</div></section>

<section class="night"><div class="wrap">
{bi("بلا حساب. بلا إعلانات. بلا خادم.", "No account. No ads. No server.", "h2")}
{bi("أدويتك ومواعيدك وسجلّك محفوظة على هاتفك فقط. لا نرى بياناتك ولا نبيعها ولا نشاركها مع أحد. لا يحتاج التطبيق إلى الإنترنت إلا لنسخة Google Drive الاختيارية وللبحث عن إصدار جديد، ويمكنك إيقاف البحث من الإعدادات.", "Your medicines, times and history are saved on your phone only. We never see your data, sell it or share it. The app uses the internet only for the optional Google Drive backup and to look for a new version, and you can turn the update check off in Settings.", "p")}
{bi("<p><a href='privacy.html'>اقرأ سياسة الخصوصية</a></p>", "<p><a href='privacy.html'>Read the privacy policy</a></p>", "div")}
</div></section>

<section class="install"><div class="wrap">
{bi("ثبّته في دقيقة", "Install it in a minute", "h2")}
{bi("<ol><li>اضغط «تنزيل» وافتح الملف عند اكتماله.</li><li>إذا طلب أندرويد ذلك، اسمح بالتثبيت من هذا المتصفح. يلزم ذلك مرة واحدة لأن التطبيق ليس على Google Play.</li><li>اضغط «تثبيت». تظهر الإصدارات الجديدة في هذه الصفحة، وتثبّتها بالطريقة نفسها فوق القديم.</li></ol>", "<ol><li>Tap Download and open the file when it finishes.</li><li>If Android asks, allow installing from this browser. This is needed once, because Pill Box is not on Google Play.</li><li>Tap Install. New versions appear on this page, and you install them the same way, over the old one.</li></ol>", "div")}
<div class="row"><a class="btn" href="{DIRECT}" download>{bi("تنزيل لأندرويد", "Download for Android")}</a>
</div>
{bi("<p class='note'>التطبيق يساعدك على التذكّر ولا يغني عن نصيحة طبيبك أو الصيدلاني.</p>", "<p class='note'>This app helps you remember. It does not replace advice from your doctor or pharmacist.</p>", "div")}
</div></section>
</main>
"""
    return out + foot()

def legal(titlekey, keys):
    out = head(AR[titlekey] + " - علبة الدواء", EN[titlekey] + " - Pill Box", AR[titlekey], EN[titlekey])
    def sec(D, lang):
        cards = "".join(f"<h2>{E(D[k+'_title'])}</h2><p>{E(D[k+'_body'])}</p>" for k in keys)
        return f'<section class="{lang}" lang="{lang}"><h1>{E(D[titlekey])}</h1><p class="muted">{E(D["legal_updated"])}</p><div class="card">{cards}</div></section>'
    out += f'<main class="legal"><div class="wrap">{sec(AR, "ar")}{sec(EN, "en")}</div></main>'
    return out + foot()

out = pathlib.Path("site")
(out / "index.html").write_text(home(), encoding="utf-8")
(out / "privacy.html").write_text(legal("pp_title", ["pp_1", "pp_2", "pp_3", "pp_5", "pp_4"]), encoding="utf-8")
(out / "terms.html").write_text(legal("tm_title", ["tm_1", "tm_2", "tm_3", "tm_4"]), encoding="utf-8")
(out / ".nojekyll").write_text("")
print("site built")
