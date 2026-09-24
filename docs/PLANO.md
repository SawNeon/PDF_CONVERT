# PdfLocal — plano de desenvolvimento por fases

Cole **um prompt por vez** no Claude Code. Só avance quando os critérios de aceite da fase estiverem ok e o `mvn test` passar.

---

## Fase 0 — Base do projeto

**Prompt:**
> Leia o CLAUDE.md. Configure a base do projeto Maven: pom.xml com Java 21, JavaFX (controls e swing), PDFBox 3, AtlantaFX, metadata-extractor e JUnit 5, com as versões em `<properties>`; javafx-maven-plugin para rodar com `mvn javafx:run`; classe `Launcher` com o main chamando `PdfLocalApp`; janela vazia com o tema AtlantaFX (PrimerLight); estrutura de pacotes do CLAUDE.md; `.gitignore` bloqueando PDFs e imagens fora de src/test/resources. Não use module-info.java. Pare ao terminar.

**Aceite:**
- [ ] `mvn javafx:run` abre uma janela com o tema aplicado
- [ ] `mvn test` roda (mesmo sem testes ainda)

---

## Fase 1 — Núcleo sem interface (services + testes)

**Prompt:**
> Implemente a camada de serviço, sem interface: `FileTypeDetector` (magic bytes), `PageItem`/`SourceDocument`/`PageSize`, `SourceRegistry` (abre e fecha os PDFs de origem), `ImageToPageService` (imagem → página, com modos ORIGINAL e A4 e correção EXIF) e `DocumentComposer` (recebe a lista de PageItem e grava o PDF final com gravação atômica, respeitando a rotação). Crie testes JUnit que geram PDFs e imagens por código, cobrindo: juntar 2 PDFs na ordem certa, reordenar, excluir página, girar, misturar imagem com PDF, A4 retrato e paisagem, arquivo com extensão falsa rejeitado, PDF com senha e PDF corrompido tratados sem exceção não capturada. Pare ao terminar.

**Aceite:**
- [ ] Todos os testes passam
- [ ] Nenhum arquivo real usado nos testes
- [ ] Originais nunca modificados (há um teste que confere isso pelo hash)

---

## Fase 2 — Grade de miniaturas + arrastar e soltar

**Prompt:**
> Crie a tela principal: toolbar (Adicionar arquivos, Limpar, Salvar PDF), `PageGrid` exibindo um `PageCard` por página com miniatura, número e nome do arquivo de origem. Implemente o `ThumbnailService` (50 DPI, fora da UI thread, uma thread por documento, cache LRU, placeholder enquanto carrega). Permita reordenar os cards arrastando, com o "fantasma" da miniatura seguindo o mouse e um indicador de onde a página vai cair. Aceite arquivos soltos do Windows Explorer direto na janela. O botão Salvar usa o DocumentComposer. Pare ao terminar.

**Aceite:**
- [ ] Soltar 3 PDFs + 2 imagens do Explorer gera a grade
- [ ] Reordenar arrastando funciona e o PDF salvo respeita a ordem
- [ ] A interface não trava ao abrir um PDF de 100+ páginas

---

## Fase 3 — Ações por página e modo de visualização

**Prompt:**
> Adicione ao PageCard botões que aparecem ao passar o mouse: girar à esquerda, girar à direita e excluir. A miniatura deve refletir a rotação. Adicione seleção múltipla (Ctrl/Shift + clique) com ações em lote e as teclas Delete e Ctrl+Z (desfazer a última ação). Adicione um alternador "Por arquivo / Por página": no modo por arquivo, cada card representa um arquivo inteiro. Adicione a opção de tamanho da página para imagens (Original / A4). Pare ao terminar.

**Aceite:**
- [ ] Girar, excluir e desfazer funcionam e aparecem no PDF salvo
- [ ] O modo por arquivo reordena arquivos inteiros

---

## Fase 4 — Pré-visualização e acabamento

**Prompt:**
> Adicione o `PreviewPane`: duplo clique num card abre a página em tamanho grande (~110 DPI), com zoom e navegação pela lista. Adicione um alternador de tema claro/escuro (PrimerLight/PrimerDark). Mostre o aviso sobre assinaturas digitais ao salvar se algum PDF de origem estiver assinado. Mostre progresso e permita cancelar ao salvar documentos grandes. Revise as mensagens de erro para ficarem claras em português. Pare ao terminar.

**Aceite:**
- [ ] A pré-visualização abre rápido e navega entre páginas
- [ ] Os temas funcionam
- [ ] O aviso de assinatura aparece quando aplicável

---

## Fase 5 — Empacotamento e segurança da build

**Prompt:**
> Configure o empacotamento com jpackage para Windows: gere um app-image (pasta portátil, sem precisar de admin) e, opcionalmente, um MSI. Inclua um runtime reduzido só com os módulos necessários (java.base, java.desktop, java.logging e os módulos JavaFX usados) e `--java-options -Xmx1g`. Documente no README a abordagem escolhida para incluir o JavaFX no runtime (jmods do JavaFX via jlink, ou um JDK com JavaFX embutido, como o Liberica Full) e os pré-requisitos (WiX Toolset para o MSI). Adicione o OWASP dependency-check-maven e o cyclonedx-maven-plugin (SBOM). Escreva o README com: como rodar, como gerar o instalador, como verificar o hash do instalador e uma seção "Segurança e privacidade" (processamento local, sem rede, originais preservados, logs sem dados sensíveis). Pare ao terminar.

**Aceite:**
- [ ] O executável roda num PC sem Java instalado
- [ ] Com o PC desconectado da rede, tudo funciona
- [ ] O relatório do dependency-check não aponta CVE alta/crítica
- [ ] O SBOM é gerado em `target/`

---

## Depois (opcional)
- Comprimir e redimensionar fotos grandes antes de converter
- Suporte a HEIC
- Integração com o renomeador de documentos fiscais
- Assinar o executável com o certificado da CA interna
