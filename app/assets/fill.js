/*
 * Flamme Snap : remplissage du formulaire Snapchat de restauration de flamme.
 * Repère les champs par leur libellé, attend qu'ils apparaissent et déclenche
 * les événements input/change. Ne touche JAMAIS au reCAPTCHA ni au bouton Envoyer.
 */
(function () {
  'use strict';

  var CFG = window.__snapFillCfg || {};
  if (window.__snapFill && window.__snapFill.timer) clearInterval(window.__snapFill.timer);
  var S = window.__snapFill = {
    timer: null,
    touched: new WeakMap(),     // champ -> valeur que l'app y a mise
    combo: { tries: 0, last: 0, chosen: '' }
  };

  var STEP = 500;          // ms entre deux essais
  var ACTIVE_MS = 30000;   // phase active (ouvre aussi la liste « Type de conversation »)
  var WATCH_MS = 180000;   // ensuite : remplit seulement les champs qui apparaissent
  var STABLE = 3;          // tours consécutifs « tout est rempli » avant de conclure
  var started = Date.now(), stable = 0, reportedPartial = false;

  var STRICT_11 = /(^|[^0-9])1 ?[:\uff1a] ?1([^0-9]|$)/;
  var LOOSE_11 = /priv|individ|one.?on.?one|one.?to.?one|tete.a.tete|\b1 a 1\b|\bun a un\b/;

  var FIELDS = [
    { key: 'email', re: /e-?mail|courriel/, value: CFG.email },
    { key: 'friend', re: /(utilisateur|pseudo|username).*\b(amie?s?|friend)\b|\b(amie?s?|friend)\b.*(utilisateur|pseudo|username)/, value: CFG.friend },
    { key: 'username', re: /nom d'?utilisateur|pseudo|username/, not: /\b(amie?s?|friend)\b/, value: CFG.username },
    { key: 'phone', re: /portable|telephone|mobile|phone/, value: CFG.phone },
    { key: 'type', re: /type de conversation|conversation type|type of conversation/, choice: true, value: '1:1' },
    { key: 'description', re: /^description\b/, value: CFG.description }
  ];

  var CTRL = 'input:not([type=hidden]):not([type=file]):not([type=submit]):not([type=button])' +
    ':not([type=checkbox]):not([type=radio]):not([type=image]):not([type=reset]),' +
    'textarea, select, [contenteditable=""], [contenteditable="true"], [role="combobox"], [role="textbox"]';
  var CHOICE = 'select, [role="combobox"], [aria-haspopup="listbox"], [aria-haspopup="true"], a.nesty-input,' +
    'input:not([type=hidden]):not([type=radio]):not([type=checkbox]):not([type=file]):not([type=submit])';

  /* ---------- outils ---------- */

  function norm(s) {
    return String(s == null ? '' : s)
      .normalize('NFD').replace(/[\u0300-\u036f]/g, '')
      .replace(/[\u2018\u2019\u02bc`\u00b4]/g, "'")
      .replace(/[\u00a0\u202f]/g, ' ')
      .replace(/\s+/g, ' ').trim().toLowerCase();
  }
  function arr(list) { return Array.prototype.slice.call(list || []); }
  function shown(el) {
    if (!el || !el.getClientRects || !el.getClientRects().length) return false;
    var cs = getComputedStyle(el);
    return cs.visibility !== 'hidden' && cs.display !== 'none';
  }
  function cssId(id) { return (window.CSS && CSS.escape) ? CSS.escape(id) : String(id).replace(/["\\]/g, '\\$&'); }
  function is(el, sel) { try { return !!(el && el.matches && el.matches(sel)); } catch (e) { return false; } }
  function isEditable(el) {
    if (!el || !el.getAttribute) return false;
    var ce = el.getAttribute('contenteditable');
    return ce === '' || ce === 'true' || el.isContentEditable === true;
  }
  function forbidden(el) {
    // Jamais le bouton d'envoi, jamais le reCAPTCHA.
    return !el || is(el, 'button[type=submit], input[type=submit], [type=submit] *') ||
      !!(el.closest && el.closest('.g-recaptcha, [class*="recaptcha"], [id*="recaptcha"]'));
  }
  function fire(el, type, Ctor, init) {
    try { el.dispatchEvent(new (Ctor || Event)(type, init || { bubbles: true })); }
    catch (e) { try { el.dispatchEvent(new Event(type, { bubbles: true })); } catch (e2) {} }
  }
  function setNative(el, v) {
    var proto = el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype :
      el instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
    var d = Object.getOwnPropertyDescriptor(proto, 'value');
    if (d && d.set) d.set.call(el, v); else el.value = v;
  }
  function press(el) {
    if (forbidden(el)) return;
    var r = el.getBoundingClientRect();
    var o = { bubbles: true, cancelable: true, view: window, button: 0,
      clientX: r.left + r.width / 2, clientY: r.top + r.height / 2 };
    var P = window.PointerEvent || MouseEvent;
    fire(el, 'pointerover', P, o); fire(el, 'mouseover', MouseEvent, o); fire(el, 'mousemove', MouseEvent, o);
    fire(el, 'pointerdown', P, o); fire(el, 'mousedown', MouseEvent, o);
    fire(el, 'pointerup', P, o); fire(el, 'mouseup', MouseEvent, o);
    fire(el, 'click', MouseEvent, o);
  }
  function key(el, k, code) {
    var o = { key: k, code: k, keyCode: code, which: code, bubbles: true, cancelable: true };
    fire(el, 'keydown', KeyboardEvent, o); fire(el, 'keyup', KeyboardEvent, o);
  }
  function report(obj) {
    try { if (window.SnapFill && window.SnapFill.onResult) window.SnapFill.onResult(JSON.stringify(obj)); } catch (e) {}
  }

  /* ---------- repérage par libellé ---------- */

  function fieldBox(label) {
    // Plus grand ancêtre du libellé qui ne contient qu'un seul libellé.
    var best = label.parentElement, box = best;
    for (var i = 0; box && i < 6; i++, box = box.parentElement) {
      if (box === document.body || box.tagName === 'FORM') break;
      if (box.querySelectorAll('label, legend').length > 1) break;
      best = box;
    }
    return best;
  }
  function single(box) { return box && box.querySelectorAll('label, legend').length <= 1; }

  function findControl(label, sel) {
    var c = null;
    try { c = label.control || null; } catch (e) {}
    if (!c && label.htmlFor) c = document.getElementById(label.htmlFor);
    if (c && !is(c, sel) && c.querySelector) c = c.querySelector(sel) || null;
    if (!c && label.id) c = document.querySelector('[aria-labelledby~="' + cssId(label.id) + '"]');
    if (!c) c = label.querySelector(sel);
    if (!c) {
      for (var s = label.nextElementSibling, i = 0; s && i < 4; s = s.nextElementSibling, i++) {
        if (is(s, 'label, legend')) break;
        if (is(s, sel)) { c = s; break; }
        var q = s.querySelector(sel);
        if (q) { c = q; break; }
      }
    }
    if (!c) { var box = fieldBox(label); if (single(box)) c = box.querySelector(sel); }
    return c;
  }

  function textControl(label) {
    var c = findControl(label, CTRL);
    // Éditeur riche (ex. CKEditor) : la vraie zone de saisie est un bloc éditable.
    if (c && !isEditable(c) && !shown(c)) {
      var box = fieldBox(label);
      var ed = box && box.querySelector('[contenteditable=""], [contenteditable="true"]');
      if (ed) c = ed;
    }
    return c;
  }

  function collect() {
    var found = {}, used = [];
    arr(document.querySelectorAll('label, legend')).forEach(function (l) {
      if (l.closest('[role=listbox], [role=option], .g-recaptcha')) return;
      var t = norm(l.innerText || l.textContent);
      if (!t || t.length > 140) return;
      for (var i = 0; i < FIELDS.length; i++) {
        var f = FIELDS[i];
        if (!f.re.test(t) || (f.not && f.not.test(t))) continue;
        var c = f.choice ? findControl(l, CHOICE) : textControl(l);
        if ((c || f.choice) && used.indexOf(c) < 0) {
          if (c) used.push(c);
          (found[f.key] = found[f.key] || []).push({ label: l, ctrl: c });
        }
        break;
      }
    });
    // Secours : libellé absent mais aria-label / placeholder explicite.
    FIELDS.forEach(function (f) {
      if (found[f.key] || f.choice) return;
      arr(document.querySelectorAll('input, textarea')).forEach(function (el) {
        if (!is(el, CTRL) || used.indexOf(el) >= 0) return;
        var t = norm((el.getAttribute('aria-label') || '') + ' ' + (el.getAttribute('placeholder') || ''));
        var hit = (t && f.re.test(t) && !(f.not && f.not.test(t))) || (f.key === 'email' && el.type === 'email');
        if (hit) { used.push(el); (found[f.key] = found[f.key] || []).push({ label: null, ctrl: el }); }
      });
    });
    return found;
  }

  /* ---------- remplissage des champs texte ---------- */

  function html(v) {
    return v.split(/\n/).map(function (l) {
      return '<p>' + (l.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;') || '&nbsp;') + '</p>';
    }).join('');
  }

  function fillEditable(el, v, passive) {
    var cur = (el.innerText || el.textContent || '').trim();
    if (norm(cur) === norm(v)) { S.touched.set(el, v); return true; }
    if (S.touched.has(el) && (cur !== '' || passive)) return cur !== '';
    var ed = el.ckeditorInstance;
    if (ed && typeof ed.setData === 'function') {
      try { ed.setData(html(v)); } catch (e) {}
    } else {
      try {
        el.focus({ preventScroll: true });
        var r = document.createRange(); r.selectNodeContents(el);
        var sel = window.getSelection(); sel.removeAllRanges(); sel.addRange(r);
        if (!document.execCommand('insertText', false, v)) throw new Error('insertText');
      } catch (e) {
        el.innerHTML = html(v);
        fire(el, 'input', window.InputEvent, { bubbles: true, inputType: 'insertText', data: v });
      }
      try { el.blur(); } catch (e) {}
    }
    S.touched.set(el, v);
    return norm(el.innerText || el.textContent) === norm(v);
  }

  function fillText(el, v, passive) {
    if (!el || forbidden(el)) return false;
    if (isEditable(el)) return fillEditable(el, v, passive);
    var cur = el.value == null ? '' : String(el.value);
    if (cur === v) { S.touched.set(el, v); return true; }
    // Déjà rempli par l'app puis modifié (par toi ou par le site) : on n'y touche plus.
    if (S.touched.has(el) && (cur !== '' || passive)) return cur !== '';
    fire(el, 'focus', FocusEvent, {}); fire(el, 'focusin', FocusEvent, { bubbles: true });
    setNative(el, v);
    fire(el, 'input', window.InputEvent, { bubbles: true, inputType: 'insertText', data: v });
    fire(el, 'change');
    fire(el, 'blur', FocusEvent, {}); fire(el, 'focusout', FocusEvent, { bubbles: true });
    S.touched.set(el, v);
    return String(el.value) === v;
  }

  /* ---------- « Type de conversation » → 1:1 ---------- */

  function pick(list, textOf) {
    var a = arr(list), i, t;
    for (i = 0; i < a.length; i++) { t = norm(textOf(a[i])); if (STRICT_11.test(t)) return a[i]; }
    for (i = 0; i < a.length; i++) { t = norm(textOf(a[i])); if (LOOSE_11.test(t) && !/group/.test(t)) return a[i]; }
    return null;
  }
  function optText(o) { return (o.innerText || o.textContent || '') + ' ' + (o.getAttribute && (o.getAttribute('aria-label') || '')); }
  function radioText(r) {
    var l = r.labels && r.labels[0];
    return (l ? (l.innerText || l.textContent) : '') + ' ' + (r.getAttribute('aria-label') || '') + ' ' + (r.value || '');
  }

  function radioGroup(label) {
    if (label.tagName === 'LEGEND') return label.parentElement;
    var g = label.closest('[role=radiogroup], fieldset');
    if (g) return g;
    if (label.id) {
      g = document.querySelector('[role=radiogroup][aria-labelledby~="' + cssId(label.id) + '"]');
      if (g) return g;
    }
    for (var s = label.nextElementSibling, i = 0; s && i < 3; s = s.nextElementSibling, i++) {
      if (s.querySelector && s.querySelector('input[type=radio]')) return s;
    }
    return null;
  }

  function wrapperOf(c, label) {
    var w = c;
    while (w.parentElement && w.parentElement !== document.body && !w.parentElement.contains(label)) w = w.parentElement;
    return w;
  }
  function visibleText(node) {
    var cl = node.cloneNode(true);
    arr(cl.querySelectorAll('label, legend, [role=listbox], [role=option], [id$="hint"], [data-garden-id*="hint"], [class*="hint"], [class*="Hint"]'))
      .forEach(function (n) { n.remove(); });
    return norm(cl.textContent);
  }
  function comboDone(c, label) {
    if (!c) return false;
    if (c.value && (STRICT_11.test(norm(c.value)) || (S.combo.chosen && norm(c.value) === norm(S.combo.chosen)))) return true;
    if (c.getAttribute('aria-expanded') === 'true') return false;
    var t = visibleText(wrapperOf(c, label));
    return STRICT_11.test(t) || (!!S.combo.chosen && t.indexOf(norm(S.combo.chosen)) >= 0);
  }
  function optionsFor(c) {
    var id = c && (c.getAttribute('aria-controls') || c.getAttribute('aria-owns'));
    var lb = id ? document.getElementById(id) : null;
    var list = lb ? lb.querySelectorAll('[role=option]') :
      document.querySelectorAll('[role=option], .nesty-panel li, #nesty-panel li');
    return arr(list).filter(shown);
  }

  function comboChoose(label, c, passive) {
    if (!c) return false;
    if (comboDone(c, label)) return true;
    if (passive) return false;
    var opts = optionsFor(c);
    if (opts.length) {
      var o = pick(opts, optText);
      if (o) { S.combo.chosen = (o.innerText || o.textContent || '').trim(); press(o); }
      return false; // vérifié au tour suivant ; si aucune option ne correspond, la liste reste ouverte
    }
    var now = Date.now();
    if (now - S.combo.last < 1100 || S.combo.tries >= 8) return false;
    S.combo.last = now; S.combo.tries++;
    if (S.combo.tries % 2 === 1) {
      press(c.closest('[data-garden-id$="trigger"]') || c);
    } else {
      try { c.focus({ preventScroll: true }); } catch (e) {}
      key(c, 'ArrowDown', 40);
    }
    return false;
  }

  function fillChoice(label, c, passive) {
    // 1. Liste déroulante classique
    if (c && c.tagName === 'SELECT') {
      var opt = pick(c.options, function (o) { return (o.text || '') + ' ' + (o.label || ''); });
      if (!opt) return false;
      if (c.selectedIndex === opt.index) return true;
      if (passive) return false;
      setNative(c, opt.value); c.selectedIndex = opt.index;
      fire(c, 'input'); fire(c, 'change');
      return c.selectedIndex === opt.index;
    }
    // 2. Boutons radio
    var g = radioGroup(label);
    var radios = g ? g.querySelectorAll('input[type=radio]') : [];
    if (radios.length) {
      var r = pick(radios, radioText);
      if (!r) return false;
      if (r.checked) return true;
      if (passive) return false;
      if (!forbidden(r)) r.click();
      return r.checked;
    }
    // 3. Liste personnalisée (combobox)
    return comboChoose(label, c, passive);
  }

  /* ---------- éclat visuel des champs remplis (aucune autre modification) ---------- */

  var flashQ = [], flashed = new WeakSet();
  function flashTarget(f, it) {
    var c = it.ctrl;
    if (!f.choice) return c;
    if (c && c.tagName === 'SELECT') return c;
    var g = it.label ? radioGroup(it.label) : null;
    var r = g && g.querySelector('input[type=radio]:checked');
    if (r) return r.closest('label') || r.parentElement;
    return c ? (c.closest('[data-garden-id$="trigger"]') || wrapperOf(c, it.label || c)) : null;
  }
  function flash(el) {
    if (el && !flashed.has(el)) { flashed.add(el); flashQ.push(el); }
  }
  function flushFlash() {
    var q = flashQ;
    flashQ = [];
    q.forEach(function (el, i) {
      setTimeout(function () {
        try {
          if (!el.animate || !el.isConnected) return;
          // Halo discret, couleur de la flamme, qui s'efface.
          el.animate([
            { boxShadow: '0 0 0 0 rgba(242,85,15,0.45)' },
            { boxShadow: '0 0 0 8px rgba(242,85,15,0)' }
          ], { duration: 900, easing: 'cubic-bezier(.32,.72,0,1)' });
        } catch (e) {}
      }, i * 120);
    });
  }

  /* ---------- boucle ---------- */

  function finishOk(st) {
    clearInterval(S.timer); S.timer = null;
    // On fait défiler jusqu'au bas du formulaire (reCAPTCHA + Envoyer) sans rien cliquer.
    var target = document.querySelector('form button[type=submit], form input[type=submit]') ||
      document.querySelector('.g-recaptcha, iframe[src*="recaptcha"][title]');
    try { if (target && shown(target)) target.scrollIntoView({ block: 'center', behavior: 'smooth' }); } catch (e) {}
    report({ done: true, fields: st });
  }

  function tick() {
    var elapsed = Date.now() - started, passive = elapsed > ACTIVE_MS;
    var found = collect(), st = {}, allOk = true;
    FIELDS.forEach(function (f) {
      var items = found[f.key];
      if (!items || !items.length) { st[f.key] = 'absent'; allOk = false; return; }
      if (!f.value) { st[f.key] = 'vide'; return; }
      var ok = true;
      items.forEach(function (it) {
        var r = false;
        try { r = f.choice ? fillChoice(it.label, it.ctrl, passive) : fillText(it.ctrl, String(f.value), passive); }
        catch (e) { r = false; }
        if (r) { try { flash(flashTarget(f, it)); } catch (e) {} }
        else ok = false;
      });
      st[f.key] = ok ? 'ok' : 'en_cours';
      if (!ok) allOk = false;
    });
    flushFlash();

    stable = allOk ? stable + 1 : 0;
    if (stable >= STABLE) { finishOk(st); return; }
    if (passive && !reportedPartial) { reportedPartial = true; report({ done: false, fields: st }); }
    if (elapsed > WATCH_MS) { clearInterval(S.timer); S.timer = null; }
  }

  S.timer = setInterval(tick, STEP);
  tick();
})();
