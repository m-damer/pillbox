// Language and text size. Arabic is the default. The choice is remembered on this device only.
(function () {
  var root = document.documentElement;
  function get(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }
  function put(k, v) { try { localStorage.setItem(k, v); } catch (e) {} }
  function setLang(l) {
    root.setAttribute('data-lang', l);
    root.lang = l;
    root.dir = l === 'ar' ? 'rtl' : 'ltr';
    document.title = (document.documentElement.getAttribute('data-title-' + l)) || document.title;
    var b = document.getElementById('lang');
    if (b) { b.textContent = l === 'ar' ? 'English' : 'العربية'; b.lang = l === 'ar' ? 'en' : 'ar'; }
    var sizes = document.querySelectorAll('.sizes button');
    var names = l === 'ar' ? ['حجم الخط: عادي', 'حجم الخط: كبير', 'حجم الخط: كبير جداً'] : ['Text size: normal', 'Text size: large', 'Text size: extra large'];
    sizes.forEach(function (s, i) { s.setAttribute('aria-label', names[i]); });
    put('lang', l);
  }
  function setSize(n) {
    if (n === 0) root.removeAttribute('data-size'); else root.setAttribute('data-size', String(n));
    document.querySelectorAll('.sizes button').forEach(function (s, i) { s.setAttribute('aria-pressed', String(i === n)); });
    put('size', String(n));
  }
  var lang = get('lang') || 'ar';
  var q = /[?&]lang=(ar|en)/.exec(location.search);
  if (q) lang = q[1];
  setLang(lang);
  setSize(parseInt(get('size') || '0', 10) || 0);
  var lb = document.getElementById('lang');
  if (lb) lb.addEventListener('click', function () { setLang(root.getAttribute('data-lang') === 'ar' ? 'en' : 'ar'); });
  document.querySelectorAll('.sizes button').forEach(function (s, i) { s.addEventListener('click', function () { setSize(i); }); });
})();
