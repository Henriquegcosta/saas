package br.com.vigentra.service.conta;

import br.com.vigentra.domain.enums.AcaoLog;
import br.com.vigentra.domain.enums.PermissaoCodigo;
import br.com.vigentra.domain.model.Convite;
import br.com.vigentra.domain.model.Responsavel;
import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.repository.ConviteRepository;
import br.com.vigentra.repository.EmpresaRepository;
import br.com.vigentra.repository.ResponsavelRepository;
import br.com.vigentra.repository.UsuarioRepository;
import br.com.vigentra.security.LimitadorTaxa;
import br.com.vigentra.security.SecurityUtils;
import br.com.vigentra.security.UsuarioPrincipal;
import br.com.vigentra.service.AuditoriaService;
import br.com.vigentra.service.NegocioException;
import br.com.vigentra.service.PermissaoService;
import br.com.vigentra.service.RecursoNaoEncontradoException;
import br.com.vigentra.service.UsuarioService;
import br.com.vigentra.service.comunicacao.ComunicacaoContaService;
import br.com.vigentra.util.TextoUtils;
import br.com.vigentra.web.dto.AceiteConviteForm;
import br.com.vigentra.web.dto.ConviteForm;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Convites de usuários. O administrador/gestor escolhe e-mail e perfil (respeitando a hierarquia);
 * o convidado define nome e senha ao aceitar. Com isso, ninguém além do próprio usuário conhece
 * a senha, e o e-mail fica comprovado pelo próprio aceite.
 */
@Service
public class ConviteService {

    public static final Duration VALIDADE = Duration.ofDays(7);

    /** Retorno interno: o token bruto existe só em memória (vai no e-mail). */
    public record ConviteCriado(Convite convite, String token) {}

    private final ConviteRepository repo;
    private final UsuarioRepository usuarioRepo;
    private final EmpresaRepository empresaRepo;
    private final ResponsavelRepository responsavelRepo;
    private final PermissaoService permissaoService;
    private final UsuarioService usuarioService;
    private final TokenService tokens;
    private final ComunicacaoContaService comunicacao;
    private final AuditoriaService auditoria;
    private final LimitadorTaxa limitador;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public ConviteService(ConviteRepository repo, UsuarioRepository usuarioRepo, EmpresaRepository empresaRepo,
                          ResponsavelRepository responsavelRepo, PermissaoService permissaoService, UsuarioService usuarioService,
                          TokenService tokens, ComunicacaoContaService comunicacao, AuditoriaService auditoria,
                          LimitadorTaxa limitador, PasswordEncoder encoder, Clock clock) {
        this.repo = repo;
        this.usuarioRepo = usuarioRepo;
        this.empresaRepo = empresaRepo;
        this.responsavelRepo = responsavelRepo;
        this.permissaoService = permissaoService;
        this.usuarioService = usuarioService;
        this.tokens = tokens;
        this.comunicacao = comunicacao;
        this.auditoria = auditoria;
        this.limitador = limitador;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Transactional
    public ConviteCriado convidar(ConviteForm f) {
        SecurityUtils.exigir(PermissaoCodigo.USER_MANAGE);
        UsuarioPrincipal ator = SecurityUtils.usuario();
        if (!usuarioService.perfisAtribuiveis().contains(f.getPerfil())) {
            throw new AccessDeniedException("Você não pode convidar usuários com o perfil " + f.getPerfil().getNome() + ".");
        }
        String email = f.getEmail().trim().toLowerCase();
        if (usuarioRepo.existsByEmailIgnoreCase(email)) throw new NegocioException("Este e-mail já possui uma conta no VIGENTRA.");
        if (!limitador.permitir("convite:" + ator.getId(), 30, Duration.ofHours(1))) {
            throw new NegocioException("Limite de convites por hora atingido. Tente novamente mais tarde.");
        }
        LocalDateTime agora = LocalDateTime.now(clock);
        // um convite aberto por e-mail: o anterior é cancelado
        repo.findAbertosPorEmail(ator.getEmpresaId(), email).forEach(c -> c.setCanceladoEm(agora));

        String token = tokens.aleatorio();
        Convite c = new Convite();
        c.setEmpresa(empresaRepo.getReferenceById(ator.getEmpresaId()));
        c.setPerfil(permissaoService.perfil(f.getPerfil()));
        c.setConvidadoPor(usuarioRepo.getReferenceById(ator.getId()));
        c.setEmail(email);
        c.setNome(TextoUtils.limpar(f.getNome()));
        c.setCriarResponsavel(f.isResponsavel());
        c.setTokenHash(TokenService.sha256(token));
        c.setExpiraEm(agora.plus(VALIDADE));
        repo.save(c);
        c = repo.findById(c.getId()).orElseThrow();
        comunicacao.convite(c, token);
        auditoria.registrar(AcaoLog.CONVITE_ENVIADO, "Convite", c.getId(),
                "Convite enviado para " + email + " com perfil " + c.getPerfil().getNome());
        return new ConviteCriado(c, token);
    }

    /** Gera um novo link (o anterior deixa de valer) e renova a validade. */
    @Transactional
    public ConviteCriado reenviar(Long id) {
        SecurityUtils.exigir(PermissaoCodigo.USER_MANAGE);
        Convite c = buscar(id);
        if (c.getAceitoEm() != null || c.getCanceladoEm() != null) throw new NegocioException("Este convite não está mais aberto.");
        if (!usuarioService.perfisAtribuiveis().contains(c.getPerfil().getCodigo())) {
            throw new AccessDeniedException("Você não pode reenviar convites deste perfil.");
        }
        if (!limitador.permitir("convite-reenvio:" + id, 3, Duration.ofHours(1))) {
            throw new NegocioException("Este convite foi reenviado recentemente. Aguarde antes de tentar de novo.");
        }
        String token = tokens.aleatorio();
        c.setTokenHash(TokenService.sha256(token));
        c.setExpiraEm(LocalDateTime.now(clock).plus(VALIDADE));
        repo.save(c);
        comunicacao.convite(c, token);
        auditoria.registrar(AcaoLog.CONVITE_ENVIADO, "Convite", c.getId(), "Convite reenviado para " + c.getEmail());
        return new ConviteCriado(c, token);
    }

    @Transactional
    public void cancelar(Long id) {
        SecurityUtils.exigir(PermissaoCodigo.USER_MANAGE);
        Convite c = buscar(id);
        if (c.getAceitoEm() != null) throw new NegocioException("Convite já aceito.");
        c.setCanceladoEm(LocalDateTime.now(clock));
        auditoria.registrar(AcaoLog.CONVITE_CANCELADO, "Convite", c.getId(), "Convite para " + c.getEmail() + " cancelado");
    }

    @Transactional(readOnly = true)
    public List<Convite> abertos() {
        return repo.findAbertos(SecurityUtils.empresaId());
    }

    /** Validação pública do link (sem login). Retorna vazio se inválido/expirado/usado/cancelado. */
    @Transactional(readOnly = true)
    public Optional<Convite> validar(String token) {
        if (token == null || token.length() < 20 || token.length() > 100) return Optional.empty();
        return repo.findByTokenHash(TokenService.sha256(token)).filter(c -> c.isUtilizavel(LocalDateTime.now(clock)));
    }

    /** Aceite: cria o usuário na empresa e perfil do convite. Uso único. */
    @Transactional
    public Usuario aceitar(AceiteConviteForm f) {
        Convite c = validar(f.getToken()).orElseThrow(() ->
                new NegocioException("Convite inválido, expirado ou já utilizado. Peça um novo convite ao administrador."));
        if (!f.getSenha().equals(f.getConfirmacaoSenha())) throw new NegocioException("As senhas não conferem.");
        if (usuarioRepo.existsByEmailIgnoreCase(c.getEmail())) {
            throw new NegocioException("Este e-mail já possui uma conta no VIGENTRA.");
        }
        LocalDateTime agora = LocalDateTime.now(clock);
        Usuario u = new Usuario();
        u.setEmpresa(c.getEmpresa());
        u.setPerfil(c.getPerfil());
        u.setNome(f.getNome().trim());
        u.setEmail(c.getEmail());
        u.setTelefone(TextoUtils.limpar(f.getTelefone()));
        u.setSenhaHash(encoder.encode(f.getSenha()));
        u.setTermosAceitosEm(agora);
        u.setEmailVerificadoEm(agora);   // o próprio link do convite comprova o e-mail
        usuarioRepo.save(u);

        if (c.isCriarResponsavel()) {
            Responsavel r = responsavelRepo.findByEmail(c.getEmpresa().getId(), c.getEmail()).orElseGet(Responsavel::new);
            if (r.getId() == null) {
                r.setEmpresa(c.getEmpresa());
                r.setNome(u.getNome());
                r.setEmail(u.getEmail());
                r.setTelefone(u.getTelefone());
                r.setCargo(u.getPerfil().getNome());
            }
            r.setUsuario(u);
            responsavelRepo.save(r);
        }
        c.setAceitoEm(agora);
        c.setUsuario(u);
        repo.save(c);
        comunicacao.boasVindas(u);
        auditoria.registrarPara(c.getEmpresa().getId(), u.getId(), u.getNome(), u.getEmail(), AcaoLog.CONVITE_ACEITO,
                "Usuario", u.getId(), "Convite aceito: " + u.getNome() + " <" + u.getEmail() + "> entrou com perfil "
                        + u.getPerfil().getNome() + "; termos de uso aceitos", true);
        return u;
    }

    private Convite buscar(Long id) {
        return repo.findByIdAndEmpresaId(id, SecurityUtils.empresaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Convite não encontrado."));
    }
}
