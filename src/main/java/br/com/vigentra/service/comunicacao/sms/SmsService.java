package br.com.vigentra.service.comunicacao.sms;

import br.com.vigentra.config.AppProperties;
import br.com.vigentra.domain.enums.CanalEnvio;
import br.com.vigentra.service.comunicacao.CanalMensageria;
import br.com.vigentra.service.comunicacao.EstadoIntegracao;
import br.com.vigentra.service.comunicacao.ResultadoEnvio;
import br.com.vigentra.service.comunicacao.StatusIntegracao;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Canal SMS. Nesta versão NÃO há provedor contratado: o canal fica "indisponível",
 * a rotina de alertas não o utiliza e nenhuma mensagem é registrada como enviada.
 * As preferências e consentimentos de SMS já podem ser cadastrados.
 */
@Service
public class SmsService implements CanalMensageria {

    private final Optional<SmsProvider> provedor;

    public SmsService(List<SmsProvider> provedores, AppProperties props) {
        String escolhido = props.sms() == null ? "nenhum" : props.sms().provider();
        this.provedor = provedores.stream().filter(p -> p.nome().equalsIgnoreCase(escolhido)).findFirst();
    }

    @Override public CanalEnvio canal() { return CanalEnvio.SMS; }

    @Override
    public StatusIntegracao status() {
        return provedor
                .map(p -> new StatusIntegracao(canal(), p.nome(), EstadoIntegracao.ATIVA, "Gateway de SMS ativo.", Map.of()))
                .orElseGet(() -> new StatusIntegracao(canal(), "Nenhum", EstadoIntegracao.INDISPONIVEL,
                        "Estrutura preparada para um gateway de SMS. Nenhum provedor foi contratado nesta etapa; "
                                + "as preferências de SMS ficam salvas para uso futuro.",
                        Map.of("Como integrar", "docs/NOTIFICACOES.md, seção SMS")));
    }

    public ResultadoEnvio enviar(String para, String texto) {
        return provedor.map(p -> p.enviar(para, texto))
                .orElseGet(() -> ResultadoEnvio.falha("sms", "Canal SMS indisponível: nenhum provedor integrado."));
    }
}
