/* ============================================================================
 * SOYSOceanBox 用户页（Hypixel 风格）：Hero + 奖池楼层 + 下滑电梯
 * 依赖 app.js 暴露的 window.MC（像素图标 / renderSlot / api）
 * ========================================================================== */
(function () {
  'use strict';

  /* ---------- 由 8x8 图案放大生成 16x16 方块纹理 ---------- */
  function blockIcon(pat) {
    const m = [];
    pat.forEach(function (r) {
      const row = r.split('').flatMap(function (ch) { return [ch, ch]; }).join('');
      m.push(row, row);
    });
    return m;
  }

  // 补充 app.js 未内置的方块图标（色符见 MC.PAL：D 钻石 / E 绿宝石 / G 金 / S 铁）
  Object.assign(MC.ICONS, {
    DIAMOND_BLOCK: blockIcon([
      'D2DDD2DD', '2DD2DD2D', 'D2DDD2DD', 'DDDDDDDD',
      'DD22DD22', 'D2DD2DD2', 'DD22DD22', 'DDDDDDDD'
    ]),
    EMERALD_BLOCK: blockIcon([
      'E2EEE2EE', '2EE2EE2E', 'E2EEE2EE', 'EEEEEEEE',
      'EE22EE22', 'E2EE2EE2', 'EE22EE22', 'EEEEEEEE'
    ]),
    GOLD_BLOCK: blockIcon([
      'G2GGG2GG', '2GG2GG2G', 'G2GGG2GG', 'GGGGGGGG',
      'GG22GG22', 'G2GG2GG2', 'GG22GG22', 'GGGGGGGG'
    ]),
    IRON_BLOCK: blockIcon([
      'S2SSS2SS', '2SS2SS2S', 'S2SSS2SS', 'SSSSSSSS',
      'SS22SS22', 'S2SS2SS2', 'SS22SS22', 'SSSSSSSS'
    ])
  });

  /* ---------- 小工具 ---------- */
  function esc(s) { return MC.escapeHtml(String(s == null ? '' : s)); }
  function decolor(s) { return (s == null ? '' : String(s)).replace(/&[0-9a-fk-or]/gi, ''); }
  function labelType(t) {
    return { ITEM: '物品', COMMAND: '指令', MONEY: '金币', POINTS: '点券' }[t] || t;
  }
  function enchantsArray(en) {
    if (Array.isArray(en)) {
      return en.map(function (e) {
        return typeof e === 'string' ? e : (e.name || e.id) + ':' + (e.level || e.lvl || 1);
      });
    }
    if (en && typeof en === 'object') {
      return Object.keys(en).map(function (k) { return k + ':' + en[k]; });
    }
    return [];
  }
  function poolIcon(name) {
    const n = name.toLowerCase();
    if (n.indexOf('vip') >= 0) return MC.ICONS.NETHER_STAR;
    if (n.indexOf('fest') >= 0 || n.indexOf('holiday') >= 0) return MC.ICONS.GOLDEN_APPLE;
    if (n.indexOf('emerald') >= 0) return MC.ICONS.EMERALD;
    if (n.indexOf('diamond') >= 0) return MC.ICONS.DIAMOND;
    return MC.ICONS.CHEST;
  }
  // 非 ITEM 奖项用一个可识别的伪材质，让 renderSlot 显示图标
  function pseudoMaterial(r) {
    const t = (r.type || 'ITEM').toUpperCase();
    if (t === 'ITEM') return r.material || 'BOOK';
    if (t === 'MONEY') return 'GOLD_INGOT';
    if (t === 'POINTS') return 'EXPERIENCE_BOTTLE';
    if (t === 'COMMAND') {
      const c = (r.command || '').toUpperCase();
      if (c.indexOf('EMERALD') >= 0) return 'EMERALD';
      if (c.indexOf('DIAMOND') >= 0) return 'DIAMOND';
      if (c.indexOf('GOLD') >= 0) return 'GOLD_INGOT';
      if (c.indexOf('IRON') >= 0) return 'IRON_INGOT';
      return 'BOOK';
    }
    return 'BOOK';
  }
  function slotItem(r) {
    const t = (r.type || 'ITEM').toUpperCase();
    const lore = [].concat(r.lore || []);
    lore.push('&8类型: ' + labelType(t) + ' &7· &8权重: ' + (r.weight || 1));
    return {
      material: pseudoMaterial(r),
      amount: r.amount,
      name: decolor(r.display) || decolor(r.name) || labelType(t),
      lore: lore,
      enchants: enchantsArray(r.enchants)
    };
  }
  function mkBtn(txt, cls, fn) {
    const b = document.createElement('button');
    b.className = cls; b.textContent = txt;
    b.addEventListener('click', fn);
    return b;
  }

  /* ---------- 自有 toast（多条堆叠） ---------- */
  function showToast(msg, type) {
    const wrap = document.getElementById('toastWrap');
    const t = document.createElement('div');
    t.className = 'toast ' + (type || '');
    t.textContent = msg;
    wrap.appendChild(t);
    setTimeout(function () {
      t.style.opacity = '0'; t.style.transition = 'opacity .3s';
      setTimeout(function () { t.remove(); }, 320);
    }, 3000);
  }

  /* ---------- 状态 ---------- */
  var view = null, state = null;

  /* ---------- Hero ---------- */
  document.getElementById('giftHero').innerHTML = MC.iconSvg(MC.ICONS.CHEST);

  function renderPlayerBar() {
    const bar = document.getElementById('playerBar');
    if (!state || !state.name) {
      bar.innerHTML = '<span class="pb-stat">未检测到游戏内身份，请在游戏内打开本页面。</span>';
      return;
    }
    const pend = state.pendingCount != null ? state.pendingCount
      : (state.pending ? state.pending.length : 0);
    bar.innerHTML =
      '<span class="pb-name">' + esc(state.name) + '</span>' +
      '<span class="pb-stat">当前宝箱：<b>' + esc(state.activePool || view.defaultPool || '-') + '</b></span>' +
      '<span class="pb-stat">累计抽奖：<b>' + (state.totalDraws || 0) + '</b></span>' +
      '<span class="pb-stat">待领取：<b>' + pend + '</b></span>';
    const claim = mkBtn('一键领取 (' + pend + ')', 'btn cyan sm', claimAll);
    if (!pend) claim.disabled = true;
    bar.appendChild(claim);
  }

  function costText() {
    if (!view.costEnabled) return '<span class="badge cyan">免费抽奖</span>';
    const c = view.cost || {};
    return '每次消耗 <b>' + (c.amount || 0) + '</b> ' + (c.type === 'money' ? '金币' : '点券');
  }
  function probText(p) {
    const rs = p.rewards || [];
    const total = rs.reduce(function (a, r) { return a + (r.weight || 1); }, 0);
    let html = '<span class="badge gold">权重合计 ' + total + '</span>';
    rs.forEach(function (r) {
      const pct = ((r.weight || 1) / total * 100).toFixed(1);
      html += '<span>' + esc(decolor(r.display) || r.id) + '：' + pct + '%</span>';
    });
    return html;
  }

  /* ---------- 楼层 ---------- */
  function renderFloors() {
    const main = document.getElementById('floors');
    main.innerHTML = '';
    (view.pools || []).forEach(function (p) {
      const isVip = p.name.toLowerCase().indexOf('vip') >= 0;
      const sec = document.createElement('section');
      sec.className = 'floor' + (isVip ? ' vip' : '');
      sec.id = 'floor-' + p.name;

      const head = document.createElement('div');
      head.className = 'floor-head';
      const icon = document.createElement('div');
      icon.className = 'floor-icon';
      icon.innerHTML = MC.iconSvg(poolIcon(p.name));
      const title = document.createElement('div');
      title.innerHTML = '<h2 class="floor-title">' + esc(p.name) + ' 宝箱</h2>'
        + '<div class="floor-meta">' + costText() + '</div>';
      const actions = document.createElement('div');
      actions.className = 'floor-actions';
      actions.appendChild(mkBtn('抽奖 x1', 'btn sm', function () { doDraw(p.name, 1); }));
      actions.appendChild(mkBtn('抽奖 x10', 'btn sm', function () { doDraw(p.name, 10); }));
      head.appendChild(icon); head.appendChild(title); head.appendChild(actions);
      sec.appendChild(head);

      const rw = document.createElement('div');
      rw.className = 'rewards';
      (p.rewards || []).forEach(function (r) { rw.appendChild(MC.renderSlot(slotItem(r))); });
      sec.appendChild(rw);

      const foot = document.createElement('div');
      foot.className = 'floor-foot';
      foot.innerHTML = probText(p);
      sec.appendChild(foot);

      main.appendChild(sec);
    });
    buildElevator();
    observeFloors();
  }

  /* ---------- 电梯 ---------- */
  function elevItem(id, label) {
    const a = document.createElement('a');
    a.href = '#' + id; a.dataset.target = id;
    a.innerHTML = '<span class="elev-label">' + esc(label) + '</span><span class="elev-dot"></span>';
    a.addEventListener('click', function (e) {
      e.preventDefault();
      const el = document.getElementById(id);
      if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
    return a;
  }
  function buildElevator() {
    const nav = document.getElementById('elevator');
    nav.innerHTML = '';
    nav.appendChild(elevItem('hero', '首页'));
    (view.pools || []).forEach(function (p) {
      nav.appendChild(elevItem('floor-' + p.name, p.name));
    });
  }
  function observeFloors() {
    const links = document.querySelectorAll('.elevator a');
    const ids = ['hero'].concat((view.pools || []).map(function (p) { return 'floor-' + p.name; }));
    function setActive(id) {
      links.forEach(function (a) { a.classList.toggle('active', a.dataset.target === id); });
    }
    if ('IntersectionObserver' in window) {
      const obs = new IntersectionObserver(function (entries) {
        entries.forEach(function (en) { if (en.isIntersecting) setActive(en.target.id); });
      }, { rootMargin: '-45% 0px -45% 0px', threshold: 0 });
      ids.forEach(function (id) {
        const el = document.getElementById(id);
        if (el) obs.observe(el);
      });
    } else {
      window.addEventListener('scroll', function () {
        let cur = 'hero';
        ids.forEach(function (id) {
          const el = document.getElementById(id);
          if (el && el.getBoundingClientRect().top <= window.innerHeight * 0.5) cur = id;
        });
        setActive(cur);
      }, { passive: true });
    }
  }

  /* ---------- 抽奖 / 领取 ---------- */
  function doDraw(pool, times) {
    if (!state || !state.name) { showToast('请先在游戏内登录', 'err'); return; }
    showToast('正在开启 ' + pool + ' 宝箱…', 'gold');
    MC.api('/player/draw/' + pool, { method: 'POST', body: { times: times } })
      .then(function (res) {
        const d = res.json || {};
        const results = d.results || [];
        let ok = 0; const fail = [];
        results.forEach(function (r) {
          if (r.ok) ok++; else fail.push(r.message || r.status);
        });
        if (ok > 0) {
          const names = results.filter(function (r) { return r.ok && r.reward; })
            .map(function (r) { return decolor((r.reward && (r.reward.display || r.reward.name)) || '奖励'); })
            .join('、');
          showToast('抽到：' + (names || '奖励') + '（' + ok + ' 次），请到待领取领取', 'ok');
        }
        if (fail.length) showToast(fail.slice(0, 2).join('；'), 'err');
        refresh();
      })
      .catch(function () { showToast('网络错误，请稍后再试', 'err'); });
  }
  function claimAll() {
    MC.api('/player/claim', { method: 'POST', body: { target: 'all' } })
      .then(function (res) {
        const d = res.json || {};
        showToast(d.msg || ('已领取 ' + (d.claimed || 0) + ' 项'),
          (d.failed || d.expired) ? 'gold' : 'ok');
        refresh();
      });
  }
  function refresh() {
    return MC.api('/player/state').then(function (res) {
      state = res.json || {};
      renderPlayerBar();
    });
  }

  /* ---------- Mock（?mock=1 预览） ---------- */
  var MOCK_VIEW = {
    defaultPool: 'default', costEnabled: true,
    cost: { type: 'points', amount: 100 },
    pools: [
      { name: 'default', rewards: [
        { id: 'coins', type: 'MONEY', amount: 1000, weight: 40, display: '&e1000 金币' },
        { id: 'points', type: 'POINTS', amount: 50, weight: 30, display: '&b50 点券' },
        { id: 'diamond', type: 'ITEM', material: 'DIAMOND', amount: 1, weight: 20, display: '&b钻石 x1' },
        { id: 'emerald_cmd', type: 'COMMAND', command: 'give {player} emerald 1', weight: 10, display: '&a绿宝石 x1' }
      ]},
      { name: 'vip', rewards: [
        { id: 'vip_money', type: 'MONEY', amount: 5000, weight: 50, display: '&e5000 金币' },
        { id: 'vip_item', type: 'ITEM', material: 'DIAMOND_BLOCK', amount: 1, weight: 30, display: '&dVIP 钻石块' },
        { id: 'vip_cmd', type: 'COMMAND', command: 'give {player} diamond 3', weight: 20, display: '&b钻石 x3' }
      ]}
    ]
  };
  var MOCK_STATE = {
    name: 'PreviewPlayer', activePool: 'default', totalDraws: 128,
    pendingCount: 2, cooldownRemaining: 0
  };

  /* ---------- 启动 ---------- */
  function boot() {
    if (location.search.indexOf('mock=1') >= 0) {
      view = MOCK_VIEW; state = MOCK_STATE;
      renderFloors(); renderPlayerBar();
      return;
    }
    MC.api('/view/pools').then(function (res) {
      view = res.json || {};
      renderFloors();
      return MC.api('/player/state');
    }).then(function (res) {
      state = res.json || {};
      renderPlayerBar();
    }).catch(function () {
      document.getElementById('floors').innerHTML =
        '<div class="empty">无法加载礼包数据，请确认在游戏服务器环境中访问（或加 ?mock=1 预览）。</div>';
    });
  }
  boot();
})();
