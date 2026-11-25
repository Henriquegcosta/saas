/* =====================================================================
   VIGENTRA - comportamentos globais da interface
   (sem scripts inline: compatível com a Content-Security-Policy)
   ===================================================================== */
(function () {
  'use strict';

  const $ = (s, el = document) => el.querySelector(s);
  const $$ = (s, el = document) => Array.from(el.querySelectorAll(s));

  /** Token CSRF para chamadas fetch() (lido das meta tags do layout). */
  window.DG = window.DG || {};
  DG.csrf = () => {
    const t = $('meta[name="_csrf"]'), h = $('meta[name="_csrf_header"]');
    return t && h ? { [h.content]: t.content } : {};
  };

  // ---------- Menu lateral (mobile) ----------
  $$('[data-toggle-sidebar]').forEach(b => b.addEventListener('click', () => document.body.classList.toggle('sidebar-aberta')));
  const backdrop = $('.dg-backdrop');
  if (backdrop) backdrop.addEventListener('click', () => document.body.classList.remove('sidebar-aberta'));
  document.addEventListener('keydown', e => { if (e.key === 'Escape') document.body.classList.remove('sidebar-aberta'); });

  // ---------- Navbar do site (sombra ao rolar) ----------
  const siteNav = $('.site-nav');
  if (siteNav) {
    const onScroll = () => siteNav.classList.toggle('scrolled', window.scrollY > 8);
    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll();
  }

  // ---------- Toasts (mensagens de sucesso/erro) ----------
  $$('.toast[data-autoshow]').forEach(el => {
    const t = new bootstrap.Toast(el, { delay: el.classList.contains('toast-erro') ? 10000 : 6000 });
    t.show();
  });

  // ---------- Atalho "/" para a busca ----------
  const busca = $('#buscaGlobal');
  if (busca) {
    document.addEventListener('keydown', e => {
      if (e.key === '/' && !['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement.tagName)) {
        e.preventDefault();
        busca.focus();
      }
    });
  }

  // ---------- Confirmação antes de ações importantes ----------
  const modalEl = $('#confirmModal');
  if (modalEl) {
    const modal = new bootstrap.Modal(modalEl);
    let formPendente = null;
    document.addEventListener('submit', e => {
      const form = e.target;
      const msg = form.dataset.confirm;
      if (!msg || form.dataset.confirmed === '1') return;
      e.preventDefault();
      formPendente = form;
      $('#confirmTitulo', modalEl).textContent = form.dataset.confirmTitle || 'Confirmar ação';
      $('#confirmMensagem', modalEl).textContent = msg;
      const btn = $('#confirmOk', modalEl);
      btn.textContent = form.dataset.confirmButton || 'Confirmar';
      btn.className = 'btn ' + (form.dataset.confirmVariant === 'primary' ? 'btn-primary' : 'btn-danger');
      $('.confirm-icon', modalEl).className = 'confirm-icon ' + (form.dataset.confirmVariant === 'primary' ? 'ic-blue' : '');
      modal.show();
    }, true);
    $('#confirmOk', modalEl).addEventListener('click', () => {
      if (!formPendente) return;
      formPendente.dataset.confirmed = '1';
      modal.hide();
      if (formPendente.requestSubmit) formPendente.requestSubmit(); else formPendente.submit();
    });
  }

  // ---------- Validação de formulários (Bootstrap) + estado de carregamento ----------
  document.addEventListener('submit', e => {
    const form = e.target;
    if (form.classList.contains('needs-validation') && !form.checkValidity()) {
      e.preventDefault();
      e.stopPropagation();
      form.classList.add('was-validated');
      const inv = form.querySelector(':invalid');
      if (inv) inv.focus();
      return;
    }
    if (form.dataset.confirm && form.dataset.confirmed !== '1') return;
    if (e.defaultPrevented || form.dataset.noLoading !== undefined) return;
    const btn = form.querySelector('[type=submit]:not([data-no-loading])');
    if (btn) {
      setTimeout(() => {
        btn.disabled = true;
        btn.dataset.originalHtml = btn.innerHTML;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>' +
          (btn.dataset.loadingText || 'Processando...');
      }, 0);
    }
  });
  // reabilita botões ao voltar pelo histórico do navegador
  window.addEventListener('pageshow', () => $$('[type=submit][data-original-html]').forEach(b => {
    b.disabled = false; b.innerHTML = b.dataset.originalHtml;
  }));

  // ---------- Mostrar/ocultar senha ----------
  $$('.toggle-password').forEach(btn => btn.addEventListener('click', () => {
    const input = btn.parentElement.querySelector('input');
    const mostrar = input.type === 'password';
    input.type = mostrar ? 'text' : 'password';
    btn.querySelector('.bi').className = 'bi ' + (mostrar ? 'bi-eye-slash' : 'bi-eye');
    btn.setAttribute('aria-label', mostrar ? 'Ocultar senha' : 'Mostrar senha');
  }));

  // ---------- Força da senha ----------
  $$('input[data-strength]').forEach(input => {
    const meter = input.closest('.mb-3, .col-md-6, div').querySelector('.password-meter span');
    const label = input.closest('.mb-3, .col-md-6, div').querySelector('.password-label');
    input.addEventListener('input', () => {
      const v = input.value;
      let s = 0;
      if (v.length >= 8) s++;
      if (/[a-z]/.test(v) && /[A-Z]/.test(v)) s++;
      if (/\d/.test(v)) s++;
      if (/[^A-Za-z0-9]/.test(v) || v.length >= 12) s++;
      const cores = ['#DC2626', '#F59E0B', '#EAB308', '#16A34A', '#15803D'];
      const nomes = ['Muito fraca', 'Fraca', 'Média', 'Boa', 'Forte'];
      if (meter) { meter.style.width = (v ? (s + 1) * 20 : 0) + '%'; meter.style.background = cores[s]; }
      if (label) label.textContent = v ? 'Força: ' + nomes[s] : '';
    });
  });
  // confirmação de senha
  $$('input[data-match]').forEach(input => {
    const alvo = document.getElementById(input.dataset.match);
    const check = () => input.setCustomValidity(input.value && alvo && input.value !== alvo.value ? 'As senhas não conferem.' : '');
    input.addEventListener('input', check);
    if (alvo) alvo.addEventListener('input', check);
  });

  // ---------- Máscaras ----------
  const mascaras = {
    cnpj: v => v.replace(/\D/g, '').slice(0, 14)
      .replace(/^(\d{2})(\d)/, '$1.$2').replace(/^(\d{2})\.(\d{3})(\d)/, '$1.$2.$3')
      .replace(/\.(\d{3})(\d)/, '.$1/$2').replace(/(\d{4})(\d)/, '$1-$2'),
    telefone: v => {
      const d = v.replace(/\D/g, '').slice(0, 11);
      if (d.length <= 10) return d.replace(/^(\d{2})(\d)/, '($1) $2').replace(/(\d{4})(\d)/, '$1-$2');
      return d.replace(/^(\d{2})(\d)/, '($1) $2').replace(/(\d{5})(\d)/, '$1-$2');
    }
  };
  $$('[data-mask]').forEach(input => {
    const fn = mascaras[input.dataset.mask];
    if (!fn) return;
    input.addEventListener('input', () => { input.value = fn(input.value); });
    if (input.value) input.value = fn(input.value);
  });

  // ---------- Linhas clicáveis ----------
  document.addEventListener('click', e => {
    const tr = e.target.closest('tr[data-href]');
    if (!tr || e.target.closest('a, button, form, input, label, .dropdown')) return;
    window.location.href = tr.dataset.href;
  });

  // ---------- Filtros com envio automático ----------
  $$('[data-autosubmit]').forEach(el => el.addEventListener('change', () => el.form && el.form.submit()));

  // ---------- Formulário de documento ----------
  const semVenc = $('#semVencimento');
  const venc = $('#dataVencimento');
  if (semVenc && venc) {
    const sync = () => {
      venc.disabled = semVenc.checked;
      venc.required = !semVenc.checked;
      if (semVenc.checked) venc.value = '';
      const hint = $('#vencimentoHint');
      if (hint) hint.hidden = !semVenc.checked;
    };
    semVenc.addEventListener('change', sync);
    sync();
  }
  // autocompletar e-mail do responsável a partir do nome escolhido na lista
  const respNome = $('#responsavelNome');
  const respEmail = $('#responsavelEmail');
  if (respNome && respEmail) {
    respNome.addEventListener('change', () => {
      const opt = $$('#listaResponsaveis option').find(o => o.value === respNome.value);
      if (opt) respEmail.value = opt.dataset.email;
    });
  }

  // ---------- Upload (dropzone) ----------
  $$('.dropzone').forEach(zone => {
    const input = zone.querySelector('input[type=file]');
    const info = zone.parentElement.querySelector('.dropzone-info');
    const max = parseInt(zone.dataset.max || '10485760', 10);
    const exts = (zone.dataset.exts || 'pdf,png,jpg,jpeg,webp').split(',');
    ['dragenter', 'dragover'].forEach(ev => zone.addEventListener(ev, e => { e.preventDefault(); zone.classList.add('dragover'); }));
    ['dragleave', 'drop'].forEach(ev => zone.addEventListener(ev, () => zone.classList.remove('dragover')));
    input.addEventListener('change', () => {
      const f = input.files[0];
      if (!f) { if (info) info.innerHTML = ''; return; }
      const ext = f.name.split('.').pop().toLowerCase();
      let erro = '';
      if (!exts.includes(ext)) erro = 'Formato não permitido. Use PDF, PNG, JPG ou WEBP.';
      else if (f.size > max) erro = 'O arquivo excede 10 MB.';
      if (erro) {
        input.value = '';
        if (info) info.innerHTML = '<div class="text-danger small mt-2"><i class="bi bi-exclamation-circle me-1"></i>' + erro + '</div>';
        return;
      }
      const kb = f.size < 1048576 ? (f.size / 1024).toFixed(1) + ' KB' : (f.size / 1048576).toFixed(1) + ' MB';
      if (info) {
        info.innerHTML = '';
        const pill = document.createElement('div');
        pill.className = 'file-pill mt-2';
        pill.innerHTML = '<i class="bi ' + (ext === 'pdf' ? 'bi-file-earmark-pdf text-danger' : 'bi-file-earmark-image text-primary') + '"></i>' +
          '<div class="flex-grow-1 text-truncate"><div class="fw-semibold small text-truncate"></div><div class="text-muted small">' + kb + ' · será enviado ao salvar</div></div>';
        pill.querySelector('.fw-semibold').textContent = f.name;
        info.appendChild(pill);
      }
    });
  });

  // ---------- Matriz de permissões: marcar/desmarcar grupo ----------
  $$('[data-check-all]').forEach(cb => cb.addEventListener('change', () => {
    $$('input[name="permissoes"]', cb.closest('form')).forEach(i => { i.checked = cb.checked; });
  }));

  // ---------- Cor da categoria: pré-visualização ----------
  $$('input[type=color][data-preview]').forEach(i => {
    const alvo = document.getElementById(i.dataset.preview);
    const sync = () => { if (alvo) alvo.style.background = i.value; };
    i.addEventListener('input', sync);
    sync();
  });

  // ---------- Tooltips ----------
  $$('[data-bs-toggle="tooltip"]').forEach(el => new bootstrap.Tooltip(el));
})();
