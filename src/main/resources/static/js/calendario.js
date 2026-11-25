/* =====================================================================
   VIGENTRA - calendário interativo de vencimentos
   Busca os documentos do mês em /app/api/calendario (JSON, filtrado pelo
   tenant no servidor) e monta a grade no navegador.
   ===================================================================== */
(function () {
  'use strict';
  const root = document.getElementById('calendario');
  if (!root) return;

  const MESES = ['Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho', 'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'];
  const API = root.dataset.api, DOCS = root.dataset.docs, LOGIN = root.dataset.login;
  const grid = document.getElementById('calGrid');
  const titulo = document.getElementById('calTitulo');
  const painel = document.getElementById('calPainel');
  const resumo = document.getElementById('calResumo');
  const [ha, hm] = root.dataset.hoje.split('-').map(Number);
  let ano = ha, mes = hm, selecionado = root.dataset.hoje, eventos = [];

  const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const pad = n => String(n).padStart(2, '0');
  const iso = (a, m, d) => `${a}-${pad(m)}-${pad(d)}`;
  const fmtBR = s => s.split('-').reverse().join('/');

  async function carregar() {
    grid.classList.add('is-loading');
    titulo.textContent = `${MESES[mes - 1]} de ${ano}`;
    try {
      const r = await fetch(`${API}?ano=${ano}&mes=${mes}`, { headers: { Accept: 'application/json' }, credentials: 'same-origin' });
      if (r.status === 401 || r.redirected) { window.location.href = LOGIN; return; }
      if (!r.ok) throw new Error('HTTP ' + r.status);
      const json = await r.json();
      eventos = json.eventos;
      desenhar(json.hoje);
    } catch (e) {
      grid.innerHTML = '<div class="p-4 text-danger small" style="grid-column:1/-1"><i class="bi bi-exclamation-triangle me-1"></i>Não foi possível carregar o calendário. Tente novamente.</div>';
    } finally {
      grid.classList.remove('is-loading');
    }
  }

  function desenhar(hoje) {
    const porDia = {};
    eventos.forEach(ev => (porDia[ev.data] = porDia[ev.data] || []).push(ev));
    const primeiro = new Date(ano, mes - 1, 1);
    const diasMes = new Date(ano, mes, 0).getDate();
    const inicio = primeiro.getDay(); // 0 = domingo
    const diasAnterior = new Date(ano, mes - 1, 0).getDate();
    let html = ['Dom', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb'].map(d => `<div class="cal-head">${d}</div>`).join('');
    const total = Math.ceil((inicio + diasMes) / 7) * 7;
    for (let i = 0; i < total; i++) {
      const d = i - inicio + 1;
      if (d < 1 || d > diasMes) {
        const n = d < 1 ? diasAnterior + d : d - diasMes;
        html += `<div class="cal-day outro-mes" aria-hidden="true"><span class="num">${n}</span></div>`;
        continue;
      }
      const data = iso(ano, mes, d);
      const evs = porDia[data] || [];
      const cls = ['cal-day', data === hoje ? 'hoje' : '', data === selecionado ? 'selecionado' : ''].join(' ');
      const itens = evs.slice(0, 2).map(ev => `<div class="cal-ev ${ev.status}" title="${esc(ev.nome)}">${esc(ev.nome)}</div>`).join('');
      const mais = evs.length > 2 ? `<div class="cal-more">+${evs.length - 2} mais</div>` : '';
      const dots = evs.length ? `<div class="cal-dots">${evs.slice(0, 4).map(ev => `<span class="bg-${ev.status === 'VENCIDO' ? 'danger' : ev.status === 'REGULAR' ? 'success' : 'warning'}"></span>`).join('')}</div>` : '';
      html += `<button type="button" class="${cls}" data-data="${data}" aria-label="${d} de ${MESES[mes - 1]}: ${evs.length} vencimento(s)">
                 <span class="num">${d}</span>${itens}${mais}${dots}</button>`;
    }
    grid.innerHTML = html;
    grid.querySelectorAll('.cal-day[data-data]').forEach(b => b.addEventListener('click', () => selecionar(b.dataset.data)));

    const cont = { VENCIDO: 0, PROXIMO_VENCIMENTO: 0, REGULAR: 0 };
    eventos.forEach(e => { cont[e.status] = (cont[e.status] || 0) + 1; });
    resumo.innerHTML = `<span class="status-badge status-VENCIDO">${cont.VENCIDO} vencido(s)</span>
      <span class="status-badge status-PROXIMO_VENCIMENTO">${cont.PROXIMO_VENCIMENTO} próximo(s)</span>
      <span class="status-badge status-REGULAR">${cont.REGULAR} regular(es)</span>`;
    detalhar();
  }

  function selecionar(data) {
    selecionado = data;
    grid.querySelectorAll('.cal-day').forEach(b => b.classList.toggle('selecionado', b.dataset.data === data));
    detalhar();
  }

  function detalhar() {
    const doDia = eventos.filter(e => e.data === selecionado);
    const [a, m] = selecionado.split('-').map(Number);
    const noMes = a === ano && m === mes;
    if (!noMes) {
      const lista = eventos;
      painel.innerHTML = cabecalho(`${MESES[mes - 1]} de ${ano}`, `${lista.length} vencimento(s) no mês`) + itens(lista, 'Nenhum documento vence neste mês.');
      return;
    }
    painel.innerHTML = cabecalho(fmtBR(selecionado), `${doDia.length} vencimento(s) nesta data`) +
      itens(doDia, 'Nenhum documento vence nesta data.') +
      (eventos.length && doDia.length !== eventos.length ? `<div class="px-3 pb-3"><button class="btn btn-sm btn-light w-100" id="verMes">Ver todos os ${eventos.length} vencimentos do mês</button></div>` : '');
    const b = document.getElementById('verMes');
    if (b) b.addEventListener('click', () => {
      painel.innerHTML = cabecalho(`${MESES[mes - 1]} de ${ano}`, `${eventos.length} vencimento(s) no mês`) + itens(eventos, '');
    });
  }

  const cabecalho = (t, s) => `<div class="card-header"><div class="card-title">${esc(t)}</div><div class="card-subtitle">${esc(s)}</div></div>`;
  function itens(lista, vazioMsg) {
    if (!lista.length) return `<div class="empty-state py-4"><div class="icon"><i class="bi bi-calendar-check"></i></div><p class="mb-0">${esc(vazioMsg)}</p></div>`;
    return '<div>' + lista.map(ev => `
      <a class="due-item" href="${DOCS}${ev.id}">
        <span class="cat-dot" style="background:${esc(ev.cor)}"></span>
        <div class="flex-grow-1 min-w-0">
          <div class="fw-semibold text-truncate">${esc(ev.nome)}</div>
          <div class="text-muted small text-truncate">${esc(ev.categoria)}${ev.responsavel ? ' · ' + esc(ev.responsavel) : ''} · ${fmtBR(ev.data)}</div>
        </div>
        <span class="status-badge status-${ev.status}">${esc(ev.prazo)}</span>
      </a>`).join('') + '</div>';
  }

  document.getElementById('calAnterior').addEventListener('click', () => { mes--; if (mes < 1) { mes = 12; ano--; } carregar(); });
  document.getElementById('calProximo').addEventListener('click', () => { mes++; if (mes > 12) { mes = 1; ano++; } carregar(); });
  document.getElementById('calHoje').addEventListener('click', () => { ano = ha; mes = hm; selecionado = root.dataset.hoje; carregar(); });
  document.addEventListener('keydown', e => {
    if (['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement.tagName)) return;
    if (e.key === 'ArrowLeft' && e.altKey) document.getElementById('calAnterior').click();
    if (e.key === 'ArrowRight' && e.altKey) document.getElementById('calProximo').click();
  });
  carregar();
})();
