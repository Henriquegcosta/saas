package br.com.vigentra.service;

import br.com.vigentra.domain.enums.AcaoLog;
import br.com.vigentra.domain.enums.PerfilCodigo;
import br.com.vigentra.domain.model.Empresa;
import br.com.vigentra.domain.model.Responsavel;
import br.com.vigentra.domain.model.Usuario;
import br.com.vigentra.repository.EmpresaRepository;
import br.com.vigentra.repository.ResponsavelRepository;
import br.com.vigentra.repository.UsuarioRepository;
import br.com.vigentra.security.SecurityUtils;
import br.com.vigentra.security.SessaoService;
import br.com.vigentra.security.UsuarioPrincipal;
import br.com.vigentra.service.conta.ContaService;
import br.com.vigentra.util.TextoUtils;
import br.com.vigentra.web.dto.EmpresaForm;
import br.com.vigentra.web.dto.RegistroForm;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Cadastro de novas empresas (onboarding do tenant) e manutenção dos dados da empresa.
 * O cadastro cria, numa única transação: empresa, usuário administrador, responsável,
 * categorias padrão, matriz de permissões e configurações de alerta.
 */
@Service
public class EmpresaService {

    private final EmpresaRepository empresaRepo;
    private final UsuarioRepository usuarioRepo;
    private final ResponsavelRepository responsavelRepo;
    private final PermissaoService permissaoService;
    private final CategoriaService categoriaService;
    private final ConfiguracaoService configuracaoService;
    private final AuditoriaService auditoria;
    private final PasswordEncoder encoder;
    private final ContaService contaService;
    private final Clock clock;
    private final SessaoService sessaoService;

    public EmpresaService(EmpresaRepository empresaRepo, UsuarioRepository usuarioRepo,
                          ResponsavelRepository responsavelRepo, PermissaoService permissaoService,
                          CategoriaService categoriaService, ConfiguracaoService configuracaoService,
                          AuditoriaService auditoria, PasswordEncoder encoder, ContaService contaService, Clock clock,
                          SessaoService sessaoService) {
        this.empresaRepo = empresaRepo;
        this.usuarioRepo = usuarioRepo;
        this.responsavelRepo = responsavelRepo;
        this.permissaoService = permissaoService;
        this.categoriaService = categoriaService;
        this.configuracaoService = configuracaoService;
        this.auditoria = auditoria;
        this.encoder = encoder;
        this.contaService = contaService;
        this.clock = clock;
        this.sessaoService = sessaoService;
    }

    @Transactional
    public Usuario registrar(RegistroForm f) {
        String email = f.getEmail().trim().toLowerCase();
        if (!f.getSenha().equals(f.getConfirmacaoSenha())) throw new NegocioException("As senhas não conferem.");
        if (usuarioRepo.existsByEmailIgnoreCase(email)) throw new NegocioException("Este e-mail já está cadastrado.");
        String cnpj = normalizarCnpj(f.getCnpj(), null);

        Empresa e = new Empresa();
        e.setNome(f.getEmpresa().trim());
        e.setCnpj(cnpj);
        e.setEmail(email);
        e.setTelefone(TextoUtils.limpar(f.getTelefone()));
        empresaRepo.save(e);

        Usuario u = new Usuario();
        u.setEmpresa(e);
        u.setPerfil(permissaoService.perfil(PerfilCodigo.ADMIN));
        u.setNome(f.getNome().trim());
        u.setEmail(email);
        u.setTelefone(TextoUtils.limpar(f.getTelefone()));
        u.setSenhaHash(encoder.encode(f.getSenha()));
        u.setTermosAceitosEm(LocalDateTime.now(clock));
        usuarioRepo.save(u);

        Responsavel r = new Responsavel();
        r.setEmpresa(e);
        r.setUsuario(u);
        r.setNome(u.getNome());
        r.setEmail(email);
        r.setTelefone(u.getTelefone());
        r.setCargo("Administrador");
        responsavelRepo.save(r);

        permissaoService.inicializarEmpresa(e);
        categoriaService.criarPadrao(e);
        configuracaoService.criarPadrao(e);

        auditoria.registrarPara(e.getId(), u.getId(), u.getNome(), u.getEmail(), AcaoLog.EMPRESA_CADASTRADA,
                "Empresa", e.getId(), "Empresa \"" + e.getNome() + "\" cadastrada; termos de uso e política de privacidade aceitos", true);
        // a conta só acessa o sistema após confirmar o e-mail (o envio acontece depois do commit)
        contaService.solicitarConfirmacao(u);
        return u;
    }

    @Transactional(readOnly = true)
    public Empresa atual() {
        return empresaRepo.findById(SecurityUtils.empresaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Empresa não encontrada."));
    }

    @Transactional
    public void atualizar(EmpresaForm f) {
        Empresa e = atual();
        e.setNome(f.getNome().trim());
        e.setCnpj(normalizarCnpj(f.getCnpj(), e.getId()));
        e.setEmail(TextoUtils.limpar(f.getEmail()));
        e.setTelefone(TextoUtils.limpar(f.getTelefone()));
        e.setEndereco(TextoUtils.limpar(f.getEndereco()));
        empresaRepo.save(e);
        auditoria.registrar(AcaoLog.EMPRESA_ALTERADA, "Empresa", e.getId(), "Dados cadastrais da empresa atualizados");
    }

    /**
     * Desativa a empresa (tenant): seus usuários não conseguem mais entrar (login e "lembrar acesso"
     * passam a ser recusados) e as sessões já abertas são encerradas imediatamente.
     * Uso interno (futuro painel da plataforma / controle de assinatura): não há rota que o exponha,
     * e quem chamar é responsável por autorizar a operação.
     */
    @Transactional
    public void desativar(Long empresaId) {
        Empresa e = empresaRepo.findById(empresaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Empresa não encontrada."));
        if (!e.isAtivo()) return;
        e.setAtivo(false);
        empresaRepo.save(e);
        UsuarioPrincipal ator = SecurityUtils.usuarioOpcional().orElse(null);
        auditoria.registrarPara(e.getId(), ator == null ? null : ator.getId(), ator == null ? "Sistema" : ator.getNome(),
                ator == null ? null : ator.getEmail(), AcaoLog.EMPRESA_ALTERADA, "Empresa", e.getId(),
                "Empresa desativada: sessões dos usuários encerradas", true);
        sessaoService.expirarUsuarios(usuarioRepo.findTodos(e.getId()).stream().map(Usuario::getId).toList());
    }

    /** Valida e formata o CNPJ (opcional). Garante unicidade entre empresas. */
    private String normalizarCnpj(String cnpj, Long empresaId) {
        if (cnpj == null || TextoUtils.somenteDigitos(cnpj).isEmpty()) return null;
        if (!TextoUtils.cnpjValido(cnpj)) throw new NegocioException("CNPJ inválido.");
        String formatado = TextoUtils.formatarCnpj(cnpj);
        boolean duplicado = empresaId == null ? empresaRepo.existsByCnpj(formatado)
                : empresaRepo.existsByCnpjAndIdNot(formatado, empresaId);
        if (duplicado) throw new NegocioException("Já existe uma empresa cadastrada com este CNPJ.");
        return formatado;
    }
}
