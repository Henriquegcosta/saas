package br.com.vigentra;

import br.com.vigentra.domain.enums.StatusEnvio;
import br.com.vigentra.domain.enums.TipoAlerta;
import br.com.vigentra.domain.model.Documento;
import br.com.vigentra.domain.model.HistoricoEnvio;
import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.repository.HistoricoEnvioRepository;
import br.com.vigentra.repository.NotificacaoRepository;
import br.com.vigentra.service.DocumentoService;
import br.com.vigentra.service.comunicacao.ResultadoEnvio;
import br.com.vigentra.service.comunicacao.email.EmailService;
import br.com.vigentra.service.alerta.AlertaService;
import br.com.vigentra.service.alerta.ResultadoRotina;
import br.com.vigentra.web.dto.DocumentoForm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;

@DisplayName("Rotina automática de alertas")
class AlertaTest extends IntegracaoBase {

    @Autowired AlertaService alertaService;
    @Autowired DocumentoService documentoService;
    @Autowired HistoricoEnvioRepository historicoRepo;
    @Autowired NotificacaoRepository notificacaoRepo;
    @MockitoSpyBean EmailService emailService;

    /** Apenas os alertas de vencimento (o histórico também guarda e-mails de conta, como a confirmação). */
    private List<HistoricoEnvio> envios(Long empresaId) {
        return historicoRepo.findRecentes(empresaId, PageRequest.of(0, 500)).stream()
                .filter(h -> h.getTipoMensagem() == br.com.vigentra.domain.enums.TipoMensagem.ALERTA_VENCIMENTO).toList();
    }

    @Test
    @DisplayName("Dispara alertas para documentos na régua, registra histórico e notificações (modo desenvolvimento)")
    void disparoDeAlertas() {
        Usuario admin = ts.novaEmpresa("Alertas");
        ts.logar(admin);
        Documento d30 = ts.documento("Vence em 20 dias", 20);
        Documento d0 = ts.documento("Vence hoje", 0);
        Documento venc = ts.documento("Vencido há 3 dias", -3);
        ts.documento("Vence em 200 dias", 200);   // fora da régua
        ts.documento("Sem vencimento", null);     // nunca gera alerta
        ts.deslogar();

        Long empresaId = admin.getEmpresa().getId();
        ResultadoRotina r = alertaService.executarEmpresa(empresaId, documentoService.hoje());

        assertThat(r.documentosAnalisados()).isEqualTo(3);
        assertThat(r.alertasGerados()).isEqualTo(3);
        // 2 destinatários por documento: responsável + administrador
        assertThat(r.modoDev()).isEqualTo(6);
        assertThat(r.enviados()).isZero();          // sem SMTP nada é "enviado de verdade"
        assertThat(r.falhas()).isZero();

        List<HistoricoEnvio> h = envios(empresaId);
        assertThat(h).hasSize(6).allMatch(x -> x.getStatus() == StatusEnvio.MODO_DEV && x.getEnviadoEm() != null);
        assertThat(h).filteredOn(x -> x.getDocumento().getId().equals(d30.getId())).allMatch(x -> x.getPeriodo().equals("D30"));
        assertThat(h).filteredOn(x -> x.getDocumento().getId().equals(d0.getId()))
                .allMatch(x -> x.getPeriodo().equals("D0") && x.getTipoAlerta() == TipoAlerta.VENCE_HOJE);
        assertThat(h).filteredOn(x -> x.getDocumento().getId().equals(venc.getId()))
                .allMatch(x -> x.getPeriodo().equals("POS1") && x.getTipoAlerta() == TipoAlerta.VENCIDO);
        assertThat(h).extracting(HistoricoEnvio::getAssunto).anyMatch(a -> a.contains("VENCE HOJE"));

        // notificação no sistema para o administrador (tem acesso a todos os documentos)
        assertThat(notificacaoRepo.contarNaoLidas(admin.getId())).isEqualTo(3);
    }

    @Test
    @DisplayName("Não envia notificação duplicada para o mesmo documento e período")
    void prevencaoDeDuplicidade() {
        Usuario admin = ts.novaEmpresa("Duplicidade");
        ts.logar(admin);
        ts.documento("Certificado A1", 5);
        ts.deslogar();
        Long empresaId = admin.getEmpresa().getId();
        LocalDate hoje = documentoService.hoje();

        ResultadoRotina primeira = alertaService.executarEmpresa(empresaId, hoje);
        ResultadoRotina segunda = alertaService.executarEmpresa(empresaId, hoje);
        ResultadoRotina dia_seguinte = alertaService.executarEmpresa(empresaId, hoje.plusDays(1)); // ainda no período D7

        assertThat(primeira.modoDev()).isEqualTo(2);
        assertThat(segunda.modoDev()).isZero();
        assertThat(segunda.duplicadosIgnorados()).isEqualTo(2);
        assertThat(dia_seguinte.modoDev()).isZero();
        assertThat(envios(empresaId)).hasSize(2);
        assertThat(notificacaoRepo.contarNaoLidas(admin.getId())).isEqualTo(1);

        // ao entrar em um novo período (D0), um novo alerta é permitido
        ResultadoRotina noDia = alertaService.executarEmpresa(empresaId, hoje.plusDays(5));
        assertThat(noDia.modoDev()).isEqualTo(2);
        assertThat(envios(empresaId)).hasSize(4);
    }

    @Test
    @DisplayName("Renovação do documento (nova data) reinicia a régua de alertas")
    void renovacaoReiniciaRegua() {
        Usuario admin = ts.novaEmpresa("Renovação");
        ts.logar(admin);
        Documento d = ts.documento("Alvará", 10);
        Long empresaId = admin.getEmpresa().getId();
        alertaService.executarEmpresa(empresaId, documentoService.hoje());
        assertThat(envios(empresaId)).hasSize(2);

        DocumentoForm f = DocumentoForm.de(documentoService.buscar(d.getId()));
        f.setDataVencimento(documentoService.hoje().plusDays(12)); // ainda no período D15, mas com nova data
        documentoService.salvar(f, null);
        alertaService.executarEmpresa(empresaId, documentoService.hoje());
        assertThat(envios(empresaId)).hasSize(4);
    }

    @Test
    @DisplayName("Falha no envio é registrada com erro, nova tentativa agendada e reprocessada depois")
    void falhaENovaTentativa() {
        Usuario admin = ts.novaEmpresa("Falha SMTP");
        ts.logar(admin);
        ts.documento("CND Municipal", 2);
        ts.deslogar();
        Long empresaId = admin.getEmpresa().getId();

        doReturn(ResultadoEnvio.falha("smtp", "MailConnectException: Connection refused"))
                .when(emailService).enviar(any());
        ResultadoRotina r = alertaService.executarEmpresa(empresaId, documentoService.hoje());
        assertThat(r.falhas()).isEqualTo(2);

        List<HistoricoEnvio> falhas = envios(empresaId);
        assertThat(falhas).allMatch(h -> h.getStatus() == StatusEnvio.FALHA
                && h.getTentativas() == 1 && h.getProximaTentativa() != null && h.getUltimoErro().contains("Connection refused"));

        // a próxima execução não duplica: os registros com falha ficam para a retentativa
        assertThat(alertaService.executarEmpresa(empresaId, documentoService.hoje()).duplicadosIgnorados()).isEqualTo(2);

        // servidor volta a funcionar: retentativa manual (forçando o horário)
        doCallRealMethod().when(emailService).enviar(any());
        falhas.forEach(h -> { h.setProximaTentativa(null); historicoRepo.save(h); });
        assertThat(alertaService.reprocessarFalhas()).isGreaterThanOrEqualTo(2);
        assertThat(envios(empresaId)).hasSize(2).allMatch(h -> h.getStatus() == StatusEnvio.MODO_DEV && h.getTentativas() == 2);
    }

    @Test
    @DisplayName("Preferência do usuário: quem desativou e-mails não recebe alertas")
    void respeitaPreferencias() {
        Usuario admin = ts.novaEmpresa("Preferências");
        ts.logar(admin);
        ts.documento("Contrato", 7);
        ts.deslogar();
        Usuario u = ts.recarregar(admin);
        u.setReceberAlertasEmail(false);
        u.setNotificacoesApp(false);
        ts.salvar(u);

        ResultadoRotina r = alertaService.executarEmpresa(admin.getEmpresa().getId(), documentoService.hoje());
        assertThat(r.modoDev()).isEqualTo(1);              // apenas o responsável do documento
        assertThat(r.notificacoesApp()).isZero();
    }
}
