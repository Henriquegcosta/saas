/* =====================================================================
   Doki - assistente virtual do VIGENTRA ("O Doki está de olho.")
   - Todo texto vindo do servidor é exibido com textContent (nunca innerHTML).
   - O histórico fica no sessionStorage da aba (por usuário) e é apagado ao sair.
   - A requisição leva APENAS a pergunta; empresa e usuário vêm da sessão no servidor.
   ===================================================================== */
(function () {
  'use strict';

  const root = document.getElementById('doki');
  if (!root) return;

  const API = root.dataset.api;
  const BASE = (root.dataset.base || '/').replace(/\/$/, '');
  const NOME = root.dataset.nome || '';
  const CHAVE = 'doki:historico:' + root.dataset.usuario;
  const CHAVE_ABERTO = 'doki:aberto:' + root.dataset.usuario;
  const MAX_HISTORICO = 60;
  const CORES = ['success', 'warning', 'danger', 'secondary'];
  const SUGESTOES = [
    'Quais documentos estão próximos do vencimento?',
    'Quais documentos já venceram?',
    'Quantos documentos estão cadastrados?',
    'Quais certificados precisam de atenção?',
    'Como cadastro um novo documento?'
  ];
  const AVATAR = root.querySelector('.doki-fab img').getAttribute('src');

  const fab = root.querySelector('.doki-fab');
  const painel = root.querySelector('.doki-painel');
  const lista = root.querySelector('#dokiMensagens');
  const form = root.querySelector('#dokiForm');
  const input = root.querySelector('#dokiPergunta');
  const botao = form.querySelector('[type=submit]');

  let historico = carregar();
  let ocupado = false;

  // ---------------------------------------------------------- armazenamento da sessão
  function carregar() {
    try {
      const v = JSON.parse(sessionStorage.getItem(CHAVE) || '[]');
      return Array.isArray(v) ? v : [];
    } catch (e) { return []; }
  }
  function salvar() {
    historico = historico.slice(-MAX_HISTORICO);
    try { sessionStorage.setItem(CHAVE, JSON.stringify(historico)); } catch (e) { /* armazenamento indisponível */ }
  }
  function limparHistorico() {
    historico = [];
    try { sessionStorage.removeItem(CHAVE); } catch (e) { /* ignora */ }
  }
  /** Lembra se o chat estava aberto, para continuar a conversa ao trocar de tela. */
  function lembrarAberto(aberto) {
    try { if (aberto) sessionStorage.setItem(CHAVE_ABERTO, '1'); else sessionStorage.removeItem(CHAVE_ABERTO); } catch (e) { /* ignora */ }
  }
  function estavaAberto() {
    try { return sessionStorage.getItem(CHAVE_ABERTO) === '1'; } catch (e) { return false; }
  }

  // ---------------------------------------------------------- montagem da interface
  function el(tag, classe, texto) {
    const e = document.createElement(tag);
    if (classe) e.className = classe;
    if (texto != null) e.textContent = texto;
    return e;
  }

  /** Aceita apenas caminhos internos ("/app/..."), nunca outro domínio. */
  function urlInterna(url) {
    return typeof url === 'string' && url.startsWith('/') && !url.startsWith('//') ? BASE + url : null;
  }

  function boasVindas() {
    return {
      intencao: 'BOAS_VINDAS',
      texto: 'Olá' + (NOME ? ', ' + NOME : '') + '! Eu sou o Doki! 🤖 Estou de olho nos seus documentos para ajudar você ' +
        'a manter tudo organizado e protegido. Como posso ajudar?',
      sugestoes: SUGESTOES
    };
  }

  function linhaDoki(conteudo) {
    // "bot" (e não "doki"): a classe .doki é do contêiner fixo do assistente e não pode se repetir nas mensagens
    const linha = el('div', 'doki-linha bot');
    const mini = el('img', 'doki-mini');
    mini.src = AVATAR; mini.alt = '';
    linha.append(mini, conteudo);
    return linha;
  }

  function bolhaDoki(r) {
    const b = el('div', 'doki-bolha' + (r.erro ? ' erro' : ''));
    b.append(el('p', null, r.texto || ''));

    if (Array.isArray(r.passos) && r.passos.length) {
      const ordenada = /^(COMO_|ONDE_)/.test(r.intencao || '');
      const l = el(ordenada ? 'ol' : 'ul', ordenada ? null : 'doki-itens');
      r.passos.forEach(p => l.append(el('li', null, p)));
      b.append(l);
    }

    // documentos encontrados: lista simples DENTRO da própria mensagem (sem cartão, painel ou pop-up)
    if (Array.isArray(r.documentos) && r.documentos.length) {
      const docs = el('ul', 'doki-docs');
      docs.setAttribute('aria-label', 'Documentos encontrados');
      r.documentos.forEach(d => {
        const cor = CORES.includes(d.statusCor) ? d.statusCor : 'secondary';
        const li = el('li', 'doki-doc st-' + cor);
        const href = urlInterna(d.url);
        const nome = el(href ? 'a' : 'span', 'doki-doc-nome', d.nome);
        if (href) { nome.href = href; nome.title = 'Abrir o documento'; }
        li.append(nome);
        const meta = [d.categoria, d.vencimento ? 'vencimento ' + d.vencimento : null].filter(Boolean).join(' · ');
        if (meta) li.append(el('span', 'doki-doc-meta', meta));
        if (d.prazo || d.status) li.append(el('span', 'doki-doc-status', d.prazo || d.status));
        docs.append(li);
      });
      b.append(docs);
    }

    if (Array.isArray(r.links) && r.links.length) {
      const links = el('div', 'doki-links');
      r.links.forEach(l => {
        const href = urlInterna(l.url);
        if (!href) return;
        const a = el('a', 'btn btn-outline-primary', l.rotulo);
        a.href = href;
        links.append(a);
      });
      if (links.childElementCount) b.append(links);
    }
    return b;
  }

  function sugestoes(lista) {
    const box = el('div', 'doki-sugestoes');
    lista.forEach(s => {
      const b = el('button', null, s);
      b.type = 'button';
      b.addEventListener('click', () => enviar(s));
      box.append(b);
    });
    return box;
  }

  function renderizar() {
    lista.textContent = '';
    const mensagens = [{ autor: 'doki', resposta: boasVindas() }].concat(historico);
    let ultimaDoki = -1;
    mensagens.forEach((m, i) => { if (m.autor === 'doki') ultimaDoki = i; });

    mensagens.forEach((m, i) => {
      if (m.autor === 'usuario') {
        const linha = el('div', 'doki-linha usuario');
        linha.append(el('div', 'doki-bolha', m.texto));
        lista.append(linha);
        return;
      }
      lista.append(linhaDoki(bolhaDoki(m.resposta)));
      // sugestões apenas na última resposta, para a conversa não ficar poluída
      const s = m.resposta.sugestoes;
      if (i === ultimaDoki && !ocupado && Array.isArray(s) && s.length) lista.append(sugestoes(s));
    });

    if (ocupado) {
      const digitando = el('div', 'doki-bolha doki-digitando');
      digitando.setAttribute('role', 'status');
      digitando.setAttribute('aria-label', 'O Doki está pensando');
      digitando.append(el('span'), el('span'), el('span'));
      lista.append(linhaDoki(digitando));
    }
    lista.scrollTop = lista.scrollHeight;
  }

  // ---------------------------------------------------------- conversa com o backend
  function falha(texto, links) {
    return { intencao: 'ERRO', texto: texto, links: links || [], erro: true };
  }

  async function enviar(texto) {
    const pergunta = (texto || '').trim();
    if (!pergunta || ocupado) return;
    historico.push({ autor: 'usuario', texto: pergunta.slice(0, 500) });
    ocupado = true;
    botao.disabled = true;
    input.value = '';
    salvar();
    renderizar();

    let resposta;
    try {
      const r = await fetch(API, {
        method: 'POST',
        credentials: 'same-origin',
        headers: Object.assign({ 'Content-Type': 'application/json', Accept: 'application/json' }, window.DG ? DG.csrf() : {}),
        body: JSON.stringify({ pergunta: pergunta.slice(0, 500) })
      });
      const json = (r.headers.get('Content-Type') || '').includes('application/json');
      if (r.redirected || r.status === 401 || (r.ok && !json)) {
        resposta = falha('Sua sessão expirou. Entre novamente para continuar falando comigo. 🔒', [{ rotulo: 'Entrar', url: '/login' }]);
      } else if (r.status === 403) {
        resposta = falha('Não consegui confirmar sua sessão. Recarregue a página e tente de novo.');
      } else if (json) {
        resposta = await r.json();
        if (!r.ok) resposta.erro = true;
      } else {
        resposta = falha('Tive um problema para responder agora. Tente novamente em instantes.');
      }
    } catch (e) {
      resposta = falha('Não consegui me conectar ao VIGENTRA. Verifique sua conexão e tente novamente.');
    }

    ocupado = false;
    botao.disabled = false;
    historico.push({ autor: 'doki', resposta: resposta });
    salvar();
    renderizar();
    input.focus();
  }

  // ---------------------------------------------------------- abrir / fechar
  function abrir(focar = true) {
    painel.hidden = false;
    root.classList.add('aberto');
    fab.setAttribute('aria-expanded', 'true');
    fab.setAttribute('aria-label', 'Fechar o Doki');
    lembrarAberto(true);
    renderizar();
    if (focar) input.focus();
  }
  function fechar() {
    painel.hidden = true;
    root.classList.remove('aberto');
    fab.setAttribute('aria-expanded', 'false');
    fab.setAttribute('aria-label', 'Abrir o Doki, assistente virtual');
    lembrarAberto(false);
    fab.focus();
  }

  fab.addEventListener('click', () => (painel.hidden ? abrir() : fechar()));
  root.querySelector('[data-doki-fechar]').addEventListener('click', fechar);
  root.querySelector('[data-doki-limpar]').addEventListener('click', () => { limparHistorico(); renderizar(); input.focus(); });
  painel.addEventListener('keydown', e => { if (e.key === 'Escape') { e.stopPropagation(); fechar(); } });
  form.addEventListener('submit', e => { e.preventDefault(); enviar(input.value); });

  // ao sair do sistema, a conversa é apagada desta aba
  document.querySelectorAll('form[action$="/logout"]').forEach(f => f.addEventListener('submit', () => {
    limparHistorico();
    lembrarAberto(false);
  }));

  // o chat continua aberto (com o histórico) ao navegar entre as telas, sem roubar o foco da página
  if (estavaAberto()) abrir(false);
})();
