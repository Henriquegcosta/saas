package br.com.vigentra.service;

import br.com.vigentra.domain.enums.AcaoLog;
import br.com.vigentra.domain.enums.PerfilCodigo;
import br.com.vigentra.domain.enums.PermissaoCodigo;
import br.com.vigentra.domain.model.PreferenciasComunicacao;
import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.repository.ConviteRepository;
import br.com.vigentra.repository.DocumentoRepository;
import br.com.vigentra.repository.NotificacaoRepository;
import br.com.vigentra.repository.ResponsavelRepository;
import br.com.vigentra.repository.TokenUsuarioRepository;
import br.com.vigentra.repository.UsuarioRepository;
import br.com.vigentra.security.SecurityUtils;
import br.com.vigentra.security.SessaoService;
import br.com.vigentra.security.UsuarioPrincipal;
import br.com.vigentra.service.comunicacao.ComunicacaoContaService;
import br.com.vigentra.util.RequestUtils;
import br.com.vigentra.util.TextoUtils;
import br.com.vigentra.web.dto.AlterarSenhaForm;
import br.com.vigentra.web.dto.PerfilUsuarioForm;
import br.com.vigentra.web.dto.UsuarioForm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gestão de usuários com regras de governança:
 * - novos usuários entram SOMENTE por convite (ninguém além do usuário conhece a senha dele);
 * - só quem tem USER_MANAGE administra usuários;
 * - um Gestor só gerencia perfis de nível inferior (Usuário);
 * - somente o Administrador exclui usuários;
 * - a empresa nunca fica sem um Administrador ativo;
 * - ninguém altera o próprio perfil ou se desativa.
 */
@Service
public class UsuarioService {

    private final UsuarioRepository repo;
    private final ResponsavelRepository responsavelRepo;
    private final NotificacaoRepository notificacaoRepo;
    private final TokenUsuarioRepository tokenRepo;
    private final ConviteRepository conviteRepo;
    private final DocumentoRepository documentoRepo;
    private final PermissaoService permissaoService;
    private final RecuperacaoSenhaService recuperacaoSenhaService;
    private final ComunicacaoContaService comunicacao;
    private final AuditoriaService auditoria;
    private final SessaoService sessaoService;
    private final PasswordEncoder encoder;

    public UsuarioService(UsuarioRepository repo, ResponsavelRepository responsavelRepo, NotificacaoRepository notificacaoRepo,
                          TokenUsuarioRepository tokenRepo, ConviteRepository conviteRepo, DocumentoRepository documentoRepo,
                          PermissaoService permissaoService, RecuperacaoSenhaService recuperacaoSenhaService,
                          ComunicacaoContaService comunicacao, AuditoriaService auditoria, SessaoService sessaoService,
                          PasswordEncoder encoder) {
        this.repo = repo;
        this.responsavelRepo = responsavelRepo;
        this.notificacaoRepo = notificacaoRepo;
        this.tokenRepo = tokenRepo;
        this.conviteRepo = conviteRepo;
        this.documentoRepo = documentoRepo;
        this.permissaoService = permissaoService;
        this.recuperacaoSenhaService = recuperacaoSenhaService;
        this.comunicacao = comunicacao;
        this.auditoria = auditoria;
        this.sessaoService = sessaoService;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public Page<Usuario> buscar(String q, PerfilCodigo perfil, Boolean ativo, int pagina, int tamanho) {
        return repo.buscar(SecurityUtils.empresaId(), TextoUtils.like(q), perfil, ativo,
                PageRequest.of(Math.max(pagina, 0), tamanho, Sort.by("nome")));
    }

    @Transactional(readOnly = true)
    public List<Usuario> todos() {
        return repo.findTodos(SecurityUtils.empresaId());
    }

    @Transactional(readOnly = true)
    public Usuario buscar(Long id) {
        return repo.findByIdAndEmpresaId(id, SecurityUtils.empresaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }

    @Transactional(readOnly = true)
    public Usuario atual() {
        return buscar(SecurityUtils.usuario().getId());
    }

    /** Perfis que o usuário logado pode atribuir. */
    public List<PerfilCodigo> perfisAtribuiveis() {
        UsuarioPrincipal ator = SecurityUtils.usuario();
        return Arrays.stream(PerfilCodigo.values())
                .filter(p -> ator.isAdmin() || p.getNivel() < ator.getPerfil().getNivel()).toList();
    }

    public boolean podeGerenciar(Usuario alvo) {
        UsuarioPrincipal ator = SecurityUtils.usuario();
        if (!ator.tem(PermissaoCodigo.USER_MANAGE)) return false;
        if (alvo.getId().equals(ator.getId())) return false;
        return ator.isAdmin() || alvo.getPerfil().getNivel() < ator.getPerfil().getNivel();
    }

    /** Edição de um usuário existente (perfil, situação e dados cadastrais). */
    @Transactional
    public Usuario salvar(UsuarioForm f) {
        SecurityUtils.exigir(PermissaoCodigo.USER_MANAGE);
        if (f.getId() == null) {
            throw new NegocioException("Novos usuários entram por convite: use \"Convidar usuário\".");
        }
        UsuarioPrincipal ator = SecurityUtils.usuario();
        if (!perfisAtribuiveis().contains(f.getPerfil())) {
            throw new AccessDeniedException("Você não pode atribuir o perfil " + f.getPerfil().getNome() + ".");
        }
        Usuario u = buscar(f.getId());
        if (u.getId().equals(ator.getId())) throw new NegocioException("Para alterar seus próprios dados use Configurações › Dados do usuário.");
        if (!podeGerenciar(u)) throw new AccessDeniedException("Você não pode alterar este usuário.");
        PerfilCodigo perfilAnterior = u.getPerfil().getCodigo();
        boolean ativoAnterior = u.isAtivo();
        if (perfilAnterior == PerfilCodigo.ADMIN && (f.getPerfil() != PerfilCodigo.ADMIN || !f.isAtivo())) {
            garantirOutroAdmin(u);
        }
        u.setNome(f.getNome().trim());
        u.setTelefone(TextoUtils.limpar(f.getTelefone()));
        u.setPerfil(permissaoService.perfil(f.getPerfil()));
        u.setAtivo(f.isAtivo());
        repo.save(u);

        StringBuilder sb = new StringBuilder("Usuário alterado: " + u.getNome() + " <" + u.getEmail() + ">");
        if (perfilAnterior != f.getPerfil()) sb.append("; perfil ").append(perfilAnterior.getNome()).append(" → ").append(f.getPerfil().getNome());
        if (ativoAnterior != f.isAtivo()) sb.append(f.isAtivo() ? "; reativado" : "; desativado");
        auditoria.registrar(AcaoLog.USUARIO_ALTERADO, "Usuario", u.getId(), sb.toString());
        if (perfilAnterior != f.getPerfil() || !f.isAtivo()) sessaoService.expirarUsuario(u.getId());
        return u;
    }

    /** O gestor não define senhas: envia ao usuário um link seguro de redefinição. */
    @Transactional
    public void enviarRedefinicaoSenha(Long id) {
        SecurityUtils.exigir(PermissaoCodigo.USER_MANAGE);
        Usuario u = buscar(id);
        if (!podeGerenciar(u)) throw new AccessDeniedException("Você não pode alterar este usuário.");
        if (!u.isAtivo()) throw new NegocioException("Reative o usuário antes de enviar a redefinição de senha.");
        UsuarioPrincipal ator = SecurityUtils.usuario();
        recuperacaoSenhaService.enviarParaUsuario(u, ator.getNome() + " <" + ator.getEmail() + ">");
    }

    @Transactional
    public void alternarAtivo(Long id) {
        SecurityUtils.exigir(PermissaoCodigo.USER_MANAGE);
        Usuario u = buscar(id);
        if (!podeGerenciar(u)) throw new AccessDeniedException("Você não pode alterar este usuário.");
        if (u.isAtivo() && u.getPerfil().getCodigo() == PerfilCodigo.ADMIN) garantirOutroAdmin(u);
        u.setAtivo(!u.isAtivo());
        repo.save(u);
        auditoria.registrar(u.isAtivo() ? AcaoLog.USUARIO_ATIVADO : AcaoLog.USUARIO_DESATIVADO, "Usuario", id,
                "Usuário " + u.getNome() + " <" + u.getEmail() + ">" + (u.isAtivo() ? " reativado" : " desativado"));
        if (!u.isAtivo()) sessaoService.expirarUsuario(id);
    }

    @Transactional
    public void excluir(Long id) {
        UsuarioPrincipal ator = SecurityUtils.usuario();
        if (!ator.isAdmin()) throw new AccessDeniedException("Somente administradores podem excluir usuários.");
        Usuario u = buscar(id);
        if (u.getId().equals(ator.getId())) throw new NegocioException("Você não pode excluir a sua própria conta por aqui.");
        if (u.getPerfil().getCodigo() == PerfilCodigo.ADMIN && u.isAtivo()) garantirOutroAdmin(u);

        String desc = "Usuário excluído: " + u.getNome() + " <" + u.getEmail() + "> (" + u.getPerfil().getNome() + ")";
        notificacaoRepo.deleteByUsuarioId(id);
        tokenRepo.deleteByUsuarioId(id);
        conviteRepo.desvincularConvidador(id);
        conviteRepo.desvincularUsuario(id);
        responsavelRepo.findByUsuarioId(id).forEach(r -> r.setUsuario(null));
        documentoRepo.desvincularCriador(id);
        auditoria.desvincularUsuario(u);
        repo.delete(u);
        sessaoService.expirarUsuario(id);
        auditoria.registrar(AcaoLog.USUARIO_EXCLUIDO, "Usuario", id, desc);
    }

    // ------------------------------------------------------------------ próprio usuário

    @Transactional
    public void atualizarProprioPerfil(PerfilUsuarioForm f) {
        Usuario u = atual();
        u.setNome(f.getNome().trim());
        u.setTelefone(TextoUtils.limpar(f.getTelefone()));
        repo.save(u);
        responsavelRepo.findByUsuarioId(u.getId()).forEach(r -> r.setNome(u.getNome()));
        auditoria.registrar(AcaoLog.USUARIO_ALTERADO, "Usuario", u.getId(), "Dados do próprio perfil atualizados");
    }

    @Transactional
    public void alterarPropriaSenha(AlterarSenhaForm f) {
        Usuario u = atual();
        if (f.getSenhaAtual() == null || !encoder.matches(f.getSenhaAtual(), u.getSenhaHash())) {
            throw new NegocioException("A senha atual está incorreta.");
        }
        if (!f.getNovaSenha().equals(f.getConfirmacao())) throw new NegocioException("A confirmação não confere com a nova senha.");
        if (encoder.matches(f.getNovaSenha(), u.getSenhaHash())) throw new NegocioException("A nova senha deve ser diferente da atual.");
        u.setSenhaHash(encoder.encode(f.getNovaSenha()));
        repo.save(u);
        // a sessão atual continua; os "lembrar acesso" de todos os dispositivos deixam de valer
        sessaoService.revogarLembrarAcesso(u.getEmail());
        comunicacao.senhaAlterada(u, RequestUtils.ipCliente());
        auditoria.registrar(AcaoLog.SENHA_ALTERADA, "Usuario", u.getId(), "Senha alterada pelo próprio usuário");
    }

    /** LGPD (art. 18): direito de acesso - exporta os dados pessoais do titular. */
    @Transactional(readOnly = true)
    public Map<String, Object> exportarMeusDados() {
        Usuario u = atual();
        PreferenciasComunicacao p = u.getPreferencias();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nome", u.getNome());
        m.put("email", u.getEmail());
        m.put("emailConfirmadoEm", texto(u.getEmailVerificadoEm()));
        m.put("telefone", u.getTelefone());
        m.put("celular", u.getCelular());
        m.put("perfil", u.getPerfil().getNome());
        m.put("empresa", u.getEmpresa().getNome());
        m.put("ativo", u.isAtivo());
        Map<String, Object> comunicacaoMap = new LinkedHashMap<>();
        comunicacaoMap.put("email", p.isReceberEmail());
        comunicacaoMap.put("whatsapp", p.isReceberWhatsapp());
        comunicacaoMap.put("whatsappConsentimentoEm", texto(p.getWhatsappConsentimentoEm()));
        comunicacaoMap.put("whatsappConsentimentoOrigem", p.getWhatsappConsentimentoOrigem());
        comunicacaoMap.put("sms", p.isReceberSms());
        comunicacaoMap.put("smsConsentimentoEm", texto(p.getSmsConsentimentoEm()));
        comunicacaoMap.put("alertasAntecedencia", p.isAlertaAntecedencia());
        comunicacaoMap.put("alertasVenceHoje", p.isAlertaVenceHoje());
        comunicacaoMap.put("alertasVencido", p.isAlertaVencido());
        comunicacaoMap.put("notificacoesNoSistema", u.isNotificacoesApp());
        m.put("preferenciasDeComunicacao", comunicacaoMap);
        m.put("ultimoLogin", texto(u.getUltimoLogin()));
        m.put("termosAceitosEm", texto(u.getTermosAceitosEm()));
        m.put("cadastradoEm", texto(u.getCreatedAt()));
        m.put("responsavelPor", responsavelRepo.findByUsuarioId(u.getId()).stream()
                .map(r -> Map.of("nome", r.getNome(), "email", r.getEmail())).toList());
        auditoria.registrar(AcaoLog.DADOS_EXPORTADOS, "Usuario", u.getId(), "Usuário exportou seus dados pessoais (LGPD)");
        return m;
    }

    private static String texto(Object o) {
        return o == null ? null : o.toString();
    }

    private void garantirOutroAdmin(Usuario alvo) {
        long admins = repo.countAtivosPorPerfil(alvo.getEmpresa().getId(), PerfilCodigo.ADMIN);
        if (admins <= 1) throw new NegocioException("A empresa precisa de pelo menos um Administrador ativo.");
    }
}
