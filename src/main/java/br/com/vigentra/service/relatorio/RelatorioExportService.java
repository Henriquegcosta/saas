package br.com.vigentra.service.relatorio;

import br.com.vigentra.domain.enums.StatusDocumento;
import br.com.vigentra.domain.model.Documento;
import br.com.vigentra.service.StatusCalculator;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Geração dos arquivos de relatório: PDF (OpenPDF), Excel .xlsx (Apache POI) e CSV. */
@Service
public class RelatorioExportService {

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter BR_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color NAVY = new Color(11, 31, 58);
    private static final Color CINZA = new Color(100, 116, 139);
    private static final Color LINHA = new Color(226, 232, 240);

    // =========================================================== PDF

    public byte[] pdf(RelatorioDados r) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 36, 36, 40, 40);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new Rodape(r));
            doc.open();

            Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, NAVY);
            Font sub = FontFactory.getFont(FontFactory.HELVETICA, 10, CINZA);
            Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, NAVY);

            doc.add(new Paragraph("Relatório de Documentos e Vencimentos", titulo));
            doc.add(new Paragraph(r.empresaNome() + "  ·  Gerado em " + r.geradoEm().format(BR_HORA) + " por " + r.geradoPor(), sub));
            doc.add(new Paragraph("Filtros: " + r.descricaoFiltros(), sub));
            doc.add(Chunk.NEWLINE);

            // cards de resumo
            PdfPTable resumo = new PdfPTable(5);
            resumo.setWidthPercentage(100);
            cardResumo(resumo, "Total", r.total(), NAVY);
            cardResumo(resumo, "Regulares", r.totais().get(StatusDocumento.REGULAR), new Color(22, 163, 74));
            cardResumo(resumo, "Próximos do vencimento", r.totais().get(StatusDocumento.PROXIMO_VENCIMENTO), new Color(217, 119, 6));
            cardResumo(resumo, "Vencidos", r.totais().get(StatusDocumento.VENCIDO), new Color(220, 38, 38));
            cardResumo(resumo, "Sem vencimento", r.totais().get(StatusDocumento.SEM_VENCIMENTO), CINZA);
            doc.add(resumo);

            doc.add(secao("Vencimentos por mês", h2));
            PdfPTable meses = new PdfPTable(r.porMes().size() + 1);
            meses.setWidthPercentage(100);
            cab(meses, "Mês");
            r.porMes().forEach(m -> cab(meses, m.rotulo()));
            cel(meses, "Vencidos");
            r.porMes().forEach(m -> cel(meses, String.valueOf(m.vencidos())));
            cel(meses, "A vencer");
            r.porMes().forEach(m -> cel(meses, String.valueOf(m.aVencer())));
            doc.add(meses);

            doc.add(secao("Vencimentos por categoria", h2));
            doc.add(tabelaAgrupada("Categoria", r.porCategoria()));

            doc.add(secao("Relatório por responsável", h2));
            doc.add(tabelaAgrupada("Responsável", r.porResponsavel()));

            doc.newPage();
            doc.add(secao("Documentos (" + r.documentos().size() + ")", h2));
            PdfPTable t = new PdfPTable(new float[]{3.2f, 2f, 1.6f, 2.2f, 1.3f, 1.3f, 1.8f});
            t.setWidthPercentage(100);
            t.setHeaderRows(1);
            for (String c : new String[]{"Documento", "Categoria", "Número", "Responsável", "Emissão", "Vencimento", "Situação"}) cab(t, c);
            for (Documento d : r.documentos()) {
                StatusDocumento s = StatusCalculator.calcular(d, r.hoje(), r.diasProximo());
                cel(t, d.getNome());
                cel(t, d.getCategoria().getNome());
                cel(t, nvl(d.getNumero()));
                cel(t, d.getResponsavel() != null ? d.getResponsavel().getNome() : "-");
                cel(t, d.getDataEmissao() != null ? d.getDataEmissao().format(BR) : "-");
                cel(t, d.getDataVencimento() != null ? d.getDataVencimento().format(BR) : "Sem vencimento");
                PdfPCell sc = new PdfPCell(new Phrase(s.getLabel(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, corStatus(s))));
                estilo(sc);
                t.addCell(sc);
            }
            if (r.documentos().isEmpty()) {
                PdfPCell vazio = new PdfPCell(new Phrase("Nenhum documento encontrado para os filtros selecionados.",
                        FontFactory.getFont(FontFactory.HELVETICA, 9, CINZA)));
                vazio.setColspan(7);
                estilo(vazio);
                t.addCell(vazio);
            }
            doc.add(t);
            doc.close();
            return out.toByteArray();
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException("Falha ao gerar PDF", e);
        }
    }

    private static Paragraph secao(String texto, Font f) {
        Paragraph p = new Paragraph(texto, f);
        p.setSpacingBefore(14);
        p.setSpacingAfter(6);
        return p;
    }

    private static void cardResumo(PdfPTable t, String rotulo, Long valor, Color cor) {
        PdfPCell c = new PdfPCell();
        c.setBorderColor(LINHA);
        c.setPadding(10);
        c.addElement(new Paragraph(rotulo, FontFactory.getFont(FontFactory.HELVETICA, 9, CINZA)));
        c.addElement(new Paragraph(String.valueOf(valor == null ? 0 : valor), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, cor)));
        t.addCell(c);
    }

    private static PdfPTable tabelaAgrupada(String titulo, List<RelatorioDados.LinhaAgrupada> linhas) {
        PdfPTable t = new PdfPTable(new float[]{4f, 1.2f, 1.2f, 1.2f, 1.2f, 1.2f});
        t.setWidthPercentage(100);
        for (String c : new String[]{titulo, "Total", "Regulares", "Próximos", "Vencidos", "Sem venc."}) cab(t, c);
        for (var l : linhas) {
            cel(t, l.nome() + (l.detalhe() != null && !l.detalhe().isBlank() && titulo.equals("Responsável") ? " (" + l.detalhe() + ")" : ""));
            cel(t, String.valueOf(l.total()));
            cel(t, String.valueOf(l.regulares()));
            cel(t, String.valueOf(l.proximos()));
            cel(t, String.valueOf(l.vencidos()));
            cel(t, String.valueOf(l.semVencimento()));
        }
        if (linhas.isEmpty()) {
            PdfPCell c = new PdfPCell(new Phrase("Sem dados.", FontFactory.getFont(FontFactory.HELVETICA, 9, CINZA)));
            c.setColspan(6);
            estilo(c);
            t.addCell(c);
        }
        return t;
    }

    private static void cab(PdfPTable t, String texto) {
        PdfPCell c = new PdfPCell(new Phrase(texto, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE)));
        c.setBackgroundColor(NAVY);
        c.setPadding(5);
        c.setBorderColor(NAVY);
        t.addCell(c);
    }

    private static void cel(PdfPTable t, String texto) {
        PdfPCell c = new PdfPCell(new Phrase(texto, FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY)));
        estilo(c);
        t.addCell(c);
    }

    private static void estilo(PdfPCell c) {
        c.setPadding(5);
        c.setBorderColor(LINHA);
        c.setBorder(Rectangle.BOTTOM);
    }

    private static Color corStatus(StatusDocumento s) {
        return switch (s) {
            case REGULAR -> new Color(22, 163, 74);
            case PROXIMO_VENCIMENTO -> new Color(217, 119, 6);
            case VENCIDO -> new Color(220, 38, 38);
            case SEM_VENCIMENTO -> CINZA;
        };
    }

    private static class Rodape extends PdfPageEventHelper {
        private final RelatorioDados r;
        Rodape(RelatorioDados r) { this.r = r; }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfPTable t = new PdfPTable(2);
            t.setTotalWidth(document.right() - document.left());
            Font f = FontFactory.getFont(FontFactory.HELVETICA, 7, CINZA);
            PdfPCell a = new PdfPCell(new Phrase("VIGENTRA · Documento confidencial - contém dados pessoais protegidos pela LGPD", f));
            a.setBorder(Rectangle.TOP);
            a.setBorderColor(LINHA);
            PdfPCell b = new PdfPCell(new Phrase("Página " + writer.getPageNumber(), f));
            b.setBorder(Rectangle.TOP);
            b.setBorderColor(LINHA);
            b.setHorizontalAlignment(Element.ALIGN_RIGHT);
            t.addCell(a);
            t.addCell(b);
            t.writeSelectedRows(0, -1, document.left(), document.bottom() - 8, writer.getDirectContent());
        }
    }

    // =========================================================== EXCEL

    public byte[] xlsx(RelatorioDados r) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = wb.createCellStyle();
            var bold = wb.createFont();
            bold.setBold(true);
            bold.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setBorderBottom(BorderStyle.THIN);

            Sheet resumo = wb.createSheet("Resumo");
            int i = 0;
            linha(resumo, i++, "Relatório de Documentos e Vencimentos");
            linha(resumo, i++, "Empresa", r.empresaNome());
            linha(resumo, i++, "Gerado em", r.geradoEm().format(BR_HORA));
            linha(resumo, i++, "Gerado por", r.geradoPor());
            linha(resumo, i++, "Filtros", r.descricaoFiltros());
            i++;
            linhaH(resumo, i++, header, "Situação", "Quantidade");
            linhaNum(resumo, i++, "Total", r.total());
            for (StatusDocumento s : StatusDocumento.values()) linhaNum(resumo, i++, s.getLabel(), r.totais().get(s));
            resumo.setColumnWidth(0, 30 * 256);
            resumo.setColumnWidth(1, 50 * 256);

            Sheet docs = wb.createSheet("Documentos");
            linhaH(docs, 0, header, "Documento", "Categoria", "Número", "Empresa responsável", "Responsável",
                    "E-mail do responsável", "Emissão", "Vencimento", "Situação", "Prazo", "Descrição", "Observações");
            int n = 1;
            for (Documento d : r.documentos()) {
                StatusDocumento s = StatusCalculator.calcular(d, r.hoje(), r.diasProximo());
                linha(docs, n++, d.getNome(), d.getCategoria().getNome(), nvl(d.getNumero()), nvl(d.getEmpresaResponsavel()),
                        d.getResponsavel() != null ? d.getResponsavel().getNome() : "",
                        d.getResponsavel() != null ? d.getResponsavel().getEmail() : "",
                        d.getDataEmissao() != null ? d.getDataEmissao().format(BR) : "",
                        d.getDataVencimento() != null ? d.getDataVencimento().format(BR) : "Sem vencimento",
                        s.getLabel(), StatusCalculator.descreverPrazo(d, r.hoje()), nvl(d.getDescricao()), nvl(d.getObservacoes()));
            }
            for (int c = 0; c < 12; c++) docs.setColumnWidth(c, (c == 0 || c >= 10 ? 36 : 20) * 256);
            docs.createFreezePane(0, 1);
            if (n > 1) docs.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, n - 1, 0, 11));

            agrupadoXlsx(wb.createSheet("Por categoria"), header, "Categoria", r.porCategoria());
            agrupadoXlsx(wb.createSheet("Por responsável"), header, "Responsável", r.porResponsavel());

            Sheet mes = wb.createSheet("Por mês");
            linhaH(mes, 0, header, "Mês", "Vencidos", "A vencer", "Total");
            int m = 1;
            for (var l : r.porMes()) {
                Row row = mes.createRow(m++);
                row.createCell(0).setCellValue(l.rotulo());
                row.createCell(1).setCellValue(l.vencidos());
                row.createCell(2).setCellValue(l.aVencer());
                row.createCell(3).setCellValue(l.total());
            }
            mes.setColumnWidth(0, 14 * 256);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gerar Excel", e);
        }
    }

    private static void agrupadoXlsx(Sheet s, CellStyle header, String titulo, List<RelatorioDados.LinhaAgrupada> linhas) {
        linhaH(s, 0, header, titulo, "Detalhe", "Total", "Regulares", "Próximos do vencimento", "Vencidos", "Sem vencimento");
        int i = 1;
        for (var l : linhas) {
            Row row = s.createRow(i++);
            row.createCell(0).setCellValue(l.nome());
            row.createCell(1).setCellValue(nvl(l.detalhe()));
            row.createCell(2).setCellValue(l.total());
            row.createCell(3).setCellValue(l.regulares());
            row.createCell(4).setCellValue(l.proximos());
            row.createCell(5).setCellValue(l.vencidos());
            row.createCell(6).setCellValue(l.semVencimento());
        }
        s.setColumnWidth(0, 34 * 256);
        s.setColumnWidth(1, 40 * 256);
    }

    private static void linha(Sheet s, int i, String... valores) {
        Row r = s.createRow(i);
        for (int c = 0; c < valores.length; c++) r.createCell(c).setCellValue(seguroPlanilha(valores[c]));
    }

    private static void linhaNum(Sheet s, int i, String rotulo, Long valor) {
        Row r = s.createRow(i);
        r.createCell(0).setCellValue(rotulo);
        r.createCell(1).setCellValue(valor == null ? 0 : valor);
    }

    private static void linhaH(Sheet s, int i, CellStyle st, String... valores) {
        Row r = s.createRow(i);
        for (int c = 0; c < valores.length; c++) {
            Cell cell = r.createCell(c);
            cell.setCellValue(valores[c]);
            cell.setCellStyle(st);
        }
    }

    // =========================================================== CSV

    /** CSV com ";" e BOM UTF-8 (abre corretamente no Excel em português). */
    public byte[] csv(RelatorioDados r) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("Documento;Categoria;Número;Empresa responsável;Responsável;E-mail do responsável;Emissão;Vencimento;Situação;Prazo\r\n");
        for (Documento d : r.documentos()) {
            StatusDocumento s = StatusCalculator.calcular(d, r.hoje(), r.diasProximo());
            sb.append(String.join(";",
                    csvCampo(d.getNome()), csvCampo(d.getCategoria().getNome()), csvCampo(d.getNumero()),
                    csvCampo(d.getEmpresaResponsavel()),
                    csvCampo(d.getResponsavel() != null ? d.getResponsavel().getNome() : ""),
                    csvCampo(d.getResponsavel() != null ? d.getResponsavel().getEmail() : ""),
                    csvCampo(d.getDataEmissao() != null ? d.getDataEmissao().format(BR) : ""),
                    csvCampo(d.getDataVencimento() != null ? d.getDataVencimento().format(BR) : "Sem vencimento"),
                    csvCampo(s.getLabel()), csvCampo(StatusCalculator.descreverPrazo(d, r.hoje())))).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Escapa aspas e neutraliza "CSV/Formula injection" (=, +, -, @ no início). */
    static String csvCampo(String v) {
        String s = seguroPlanilha(nvl(v));
        if (s.contains(";") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    static String seguroPlanilha(String v) {
        if (v == null || v.isEmpty()) return "";
        char c = v.charAt(0);
        return (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') ? "'" + v : v;
    }

    private static String nvl(String s) { return s == null ? "" : s; }
}
