/* =====================================================================
   VIGENTRA - gráficos (Chart.js). Os dados chegam via atributo data-chart
   (JSON gerado no servidor), sem scripts inline.
   ===================================================================== */
(function () {
  'use strict';
  if (typeof Chart === 'undefined') return;

  const C = { success: '#16A34A', warning: '#F59E0B', danger: '#DC2626', gray: '#CBD5E1', primary: '#2563EB', grid: '#EEF2F7', text: '#64748B' };
  Chart.defaults.font.family = getComputedStyle(document.body).fontFamily;
  Chart.defaults.font.size = 12;
  Chart.defaults.color = C.text;
  Chart.defaults.plugins.legend.labels.usePointStyle = true;
  Chart.defaults.plugins.legend.labels.boxWidth = 8;
  Chart.defaults.plugins.tooltip.backgroundColor = '#0B1F3A';
  Chart.defaults.plugins.tooltip.padding = 10;
  Chart.defaults.plugins.tooltip.cornerRadius = 8;

  const dados = el => { try { return JSON.parse(el.dataset.chart); } catch (e) { return null; } };
  const vazio = (el, msg) => {
    const box = el.parentElement;
    box.innerHTML = '<div class="h-100 d-flex flex-column align-items-center justify-content-center text-muted small">' +
      '<i class="bi bi-bar-chart fs-3 mb-2 opacity-50"></i><span></span></div>';
    box.querySelector('span').textContent = msg;   // texto inserido como texto, nunca como HTML
  };

  // Rosca: situação dos documentos
  document.querySelectorAll('canvas[data-chart-type="status"]').forEach(el => {
    const d = dados(el);
    if (!d || d.valores.reduce((a, b) => a + b, 0) === 0) return vazio(el, 'Cadastre documentos para ver a distribuição.');
    const total = d.valores.reduce((a, b) => a + b, 0);
    new Chart(el, {
      type: 'doughnut',
      data: { labels: d.labels, datasets: [{ data: d.valores, backgroundColor: [C.success, C.warning, C.danger, C.gray], borderWidth: 3, borderColor: '#fff', hoverOffset: 6 }] },
      options: {
        maintainAspectRatio: false, cutout: '72%',
        plugins: {
          legend: { position: 'bottom' },
          tooltip: { callbacks: { label: c => ` ${c.label}: ${c.parsed} (${Math.round(c.parsed * 100 / total)}%)` } }
        },
        onClick: (e, els) => {
          if (!els.length || !el.dataset.link) return;
          const status = ['REGULAR', 'PROXIMO_VENCIMENTO', 'VENCIDO', 'SEM_VENCIMENTO'][els[0].index];
          window.location.href = el.dataset.link + '?status=' + status;
        }
      },
      plugins: [{
        id: 'centro',
        afterDraw(chart) {
          const { ctx, chartArea: a } = chart;
          const x = (a.left + a.right) / 2, y = (a.top + a.bottom) / 2;
          ctx.save();
          ctx.textAlign = 'center';
          ctx.fillStyle = '#0F172A';
          ctx.font = '700 26px ' + Chart.defaults.font.family;
          ctx.fillText(total, x, y + 4);
          ctx.fillStyle = C.text;
          ctx.font = '500 11px ' + Chart.defaults.font.family;
          ctx.fillText('documentos', x, y + 22);
          ctx.restore();
        }
      }]
    });
  });

  // Barras empilhadas: vencimentos por mês
  document.querySelectorAll('canvas[data-chart-type="meses"]').forEach(el => {
    const d = dados(el);
    if (!d) return;
    const soma = d.vencidos.concat(d.aVencer).reduce((a, b) => a + b, 0);
    if (soma === 0) return vazio(el, 'Nenhum vencimento no período.');
    new Chart(el, {
      type: 'bar',
      data: {
        labels: d.labels,
        datasets: [
          { label: 'Vencidos', data: d.vencidos, backgroundColor: C.danger, borderRadius: 5, maxBarThickness: 28 },
          { label: 'A vencer', data: d.aVencer, backgroundColor: C.primary, borderRadius: 5, maxBarThickness: 28 }
        ]
      },
      options: {
        maintainAspectRatio: false,
        scales: {
          x: { stacked: true, grid: { display: false } },
          y: { stacked: true, beginAtZero: true, ticks: { precision: 0 }, grid: { color: C.grid }, border: { display: false } }
        },
        plugins: { legend: { position: 'bottom' } }
      }
    });
  });

  // Barras horizontais: por categoria
  document.querySelectorAll('canvas[data-chart-type="categorias"]').forEach(el => {
    const d = dados(el);
    if (!d || d.valores.length === 0) return vazio(el, el.dataset.vazio || 'Nenhum documento para exibir.');
    new Chart(el, {
      type: 'bar',
      data: { labels: d.labels, datasets: [{ label: 'Documentos', data: d.valores, backgroundColor: d.cores, borderRadius: 5, maxBarThickness: 22 }] },
      options: {
        indexAxis: 'y', maintainAspectRatio: false,
        scales: { x: { beginAtZero: true, ticks: { precision: 0 }, grid: { color: C.grid }, border: { display: false } }, y: { grid: { display: false } } },
        plugins: { legend: { display: false } }
      }
    });
  });
})();
