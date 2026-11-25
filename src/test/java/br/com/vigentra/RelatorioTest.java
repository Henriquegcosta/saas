package br.com.vigentra;

import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.repository.DocumentoFiltro;
import br.com.vigentra.service.relatorio.RelatorioDados;
import br.com.vigentra.service.relatorio.RelatorioExportService;
import br.com.vigentra.service.relatorio.RelatorioService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Geração de relatórios")
class RelatorioTest extends IntegracaoBase {

    @Autowired RelatorioService relatorioService;
    @Autowired RelatorioExportService exportService;

    @Test
    @DisplayName("Consolida totais, por categoria, por responsável e por mês; exporta PDF, Excel e CSV")
    void relatorios() throws Exception {
        Usuario admin = ts.novaEmpresa("Relatórios");
        var p = ts.logar(admin);
        ts.documento("=HYPERLINK(\"http://malicioso\")", 10); // tentativa de "CSV injection"
        ts.documento("Licença", 100);
        ts.documento("Vencido", -3);
        ts.documento("Permanente", null);

        RelatorioDados r = relatorioService.gerar(new DocumentoFiltro());
        assertThat(r.total()).isEqualTo(4);
        assertThat(r.vencidos()).isEqualTo(1);
        assertThat(r.proximos()).isEqualTo(1);
        assertThat(r.regulares()).isEqualTo(1);
        assertThat(r.semVencimento()).isEqualTo(1);
        assertThat(r.porCategoria()).isNotEmpty();
        assertThat(r.porResponsavel()).hasSize(4);
        assertThat(r.porMes()).hasSize(12);

        byte[] pdf = exportService.pdf(r);
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");

        byte[] xlsx = exportService.xlsx(r);
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(wb.getSheet("Documentos").getPhysicalNumberOfRows()).isEqualTo(5);
            assertThat(wb.getSheet("Por categoria")).isNotNull();
            assertThat(wb.getSheet("Por responsável")).isNotNull();
        }

        String csv = new String(exportService.csv(r), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿Documento;Categoria");
        assertThat(csv).contains("'=HYPERLINK"); // fórmula neutralizada
        assertThat(csv.lines()).hasSize(5);

        for (String formato : new String[]{"pdf", "xlsx", "csv"}) {
            mvc.perform(get("/app/relatorios/exportar/" + formato).with(user(p)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
        }
        mvc.perform(get("/app/relatorios/exportar/doc").with(user(p))).andExpect(status().isBadRequest());
    }
}
