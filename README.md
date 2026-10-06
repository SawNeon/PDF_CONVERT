# PdfLocal

Aplicativo desktop para Windows que **junta PDFs** e **converte imagens em PDF**, com miniaturas, arrastar e soltar e pré-visualização. Foi pensado para documentos que podem conter dados sensíveis (LGPD): tudo é processado **localmente**, sem acesso à internet.

Formatos aceitos: PDF, JPEG, PNG, GIF, BMP e TIFF. O tipo é identificado pelo conteúdo do arquivo, nunca pela extensão.

## Como rodar (desenvolvimento)

Requisitos: JDK 21 e Maven 3.9+.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot'
mvn javafx:run
```

Abrir já com arquivos:

```powershell
mvn javafx:run "-Djavafx.args=C:\caminho\a.pdf C:\caminho\foto.jpg"
```

Testes:

```powershell
mvn test
```

Os testes geram todos os PDFs e imagens por código. Nenhum documento real é usado.

## Como usar

- Arraste PDFs e imagens do Explorer para a janela, ou use **Adicionar arquivos**.
- Arraste os cards para reordenar. Ctrl/Shift + clique seleciona vários, e o arraste move a seleção inteira.
- Passe o mouse num card para girar à esquerda, girar à direita ou excluir. `Delete` exclui a seleção e `Ctrl+Z` desfaz.
- **Por arquivo / Por página** alterna entre um card por arquivo e um card por página.
- Duplo clique abre a página em tamanho grande, com zoom e navegação.
- **Imagens em** escolhe o tamanho da página das imagens: Original ou A4 (margem de 10 mm, retrato ou paisagem automático).
- **Salvar PDF** grava o resultado. É possível cancelar durante o salvamento.
- O botão de tema alterna entre claro e escuro, e a escolha é lembrada.

## Identidade visual

- **Cores:** tudo está em `src/main/resources/pdflocal.css`. A paleta fica no começo do arquivo (tema claro em `.root` e tema escuro em `.root.dark`), e o resto do CSS usa essas variáveis.
- **Textos de identificação:** nome do app, descrição e "Desenvolvido por" ficam em `src/main/resources/messages.properties` (`app.title`, `app.about.title`, `app.developer`, `app.about.version`). O arquivo deve ser salvo em **UTF-8**; o `.editorconfig` e um teste cuidam disso.
- **Imagens:** ficam em `src/main/resources/images`, todas PNG com fundo transparente: `logo-green` e `logo-white` (tela "Sobre"), `symbol-green` e `symbol-white` (toolbar), `watermark-light` e `watermark-dark` (fundo da grade, já com baixa opacidade) e `app-icon-32/64/256` (ícone da janela). A versão branca é usada no tema escuro.
- **Ícone do executável:** `packaging/app-icon.ico` (16 a 256 px), usado pelo `jpackage` no `.exe` e no instalador.

Para trocar a logo, substitua os PNGs mantendo os nomes e as proporções (o teste `BrandAssetsTest` confere que os arquivos existem, são PNG e têm transparência) e gere o `.ico` de novo.

## Como gerar o pacote para Windows

Requisitos: JDK 21 (com `jlink` e `jpackage`) e Maven no PATH, ou informados ao script.

```powershell
.\packaging\build-dist.ps1 -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot'
```

O script faz, em ordem: build (`mvn -Pdist package`), runtime reduzido com `jlink`, cópia das bibliotecas nativas do JavaFX, app-image com `jpackage`, `.zip` portátil e hashes SHA-256. Tudo fica em `target\dist`:

| Saída | O que é |
|---|---|
| `app-image\PdfLocal\PdfLocal.exe` | Pasta portátil. Não precisa de administrador nem de Java instalado. |
| `PdfLocal-<versão>-windows-portable.zip` | A mesma pasta compactada, para distribuir. |
| `installer\PdfLocal-<versão>.msi` | Instalador (só com `-Msi`). |
| `SHA256SUMS.txt` | Hash de cada artefato. |

Parâmetros: `-Maven <caminho do mvn>`, `-SkipBuild` (reaproveita o build anterior) e `-Msi`.

A memória máxima da JVM no pacote é `-Xmx1g`.

### MSI (opcional)

O MSI exige o **WiX Toolset 3.x** (3.14 recomendado), com `candle.exe` e `light.exe` no PATH. O JDK 21 não funciona com o WiX 4 ou superior. Depois de instalar o WiX:

```powershell
.\packaging\build-dist.ps1 -Msi
```

O instalador é por usuário (`--win-per-user-install`), então não pede administrador, e cria atalho no menu Iniciar. Sem o WiX, o script termina com uma mensagem explicando o que falta. Nesta versão o MSI não foi gerado nem testado, porque o WiX não estava instalado no ambiente de desenvolvimento.

### Como o JavaFX entra no runtime

O JavaFX não faz parte do JDK. Existem três caminhos:

1. **jmods do JavaFX (Gluon) com `jlink`.** Exige baixar o ZIP de jmods de um site externo, fora do Maven.
2. **JDK com JavaFX embutido** (por exemplo, Liberica Full). Simples, mas obriga todos os desenvolvedores a usar essa distribuição.
3. **JARs do JavaFX do Maven com `jlink`.** Foi o escolhido.

O terceiro caminho funciona porque os JARs `javafx-*-win` do Maven Central são modulares (têm `module-info`) e carregam as DLLs nativas dentro do próprio JAR. O script monta o runtime com `jlink` a partir desses JARs e copia as DLLs para `runtime\bin`, que é exatamente o que o jmods entregaria. Assim:

- tudo vem do Maven Central, com a versão fixada no `pom.xml` (`javafx.version`);
- o app não extrai nada na pasta do usuário ao iniciar;
- as classes do JavaFX ficam como módulos do runtime, e o app roda no classpath, sem `module-info.java`.

Módulos no runtime (12, 55 MB): `java.base`, `java.desktop`, `java.logging`, `java.xml`, `jdk.unsupported`, `jdk.unsupported.desktop`, `javafx.base`, `javafx.graphics`, `javafx.controls` e `javafx.swing`, mais `java.datatransfer` e `java.prefs`, que entram como dependência de `java.desktop`.

Além dos três módulos de base, o `jdeps` mostrou que `java.xml` é necessário para a biblioteca `xmpcore` (metadados das fotos) e que o JavaFX usa `jdk.unsupported` e `jdk.unsupported.desktop`. Para recalcular a lista depois de mudar as dependências:

```powershell
jdeps --multi-release 21 --ignore-missing-deps --module-path <jars javafx> --class-path <jars do app> --print-module-deps target\pdflocal-*.jar
```

O runtime herda a versão (e as correções de segurança) do JDK usado para gerar o pacote. Gere o pacote sempre com um JDK 21 atualizado.

## Como verificar o hash do instalador

Cada artefato tem o SHA-256 em `SHA256SUMS.txt`. Quem recebe o arquivo confere no PowerShell:

```powershell
Get-FileHash .\PdfLocal-1.0.0-windows-portable.zip -Algorithm SHA256
```

O valor impresso em `Hash` deve ser idêntico ao do `SHA256SUMS.txt`, que deve ser obtido por um canal separado do arquivo (por exemplo, a pasta de rede e o e-mail da equipe). Alternativa sem PowerShell:

```cmd
certutil -hashfile PdfLocal-1.0.0-windows-portable.zip SHA256
```

Se os valores forem diferentes, não execute o arquivo.

Os executáveis **não são assinados digitalmente**. O Windows pode mostrar o aviso do SmartScreen na primeira execução. Assinar com o certificado da CA interna está previsto como melhoria futura.

## Segurança e privacidade

- **Processamento local, sem rede.** O app não tem código que acesse a internet, nenhuma telemetria e nenhuma verificação de atualização. O runtime não inclui o módulo `java.net.http`. No teste do executável empacotado, o processo ficou sem nenhuma conexão TCP nem UDP aberta.
- **Originais preservados.** A interface nunca altera os arquivos de origem: girar, excluir e reordenar só mudam a lista, e o PDF final é montado ao salvar. O arquivo de saída é gravado primeiro num temporário na mesma pasta e movido de forma atômica. O app recusa salvar sobre um arquivo de origem. Há testes que comparam o hash dos originais antes e depois de salvar.
- **Tipo pelo conteúdo.** PDF, JPEG, PNG, GIF, BMP e TIFF são reconhecidos pelos primeiros bytes. Um arquivo com extensão falsa é rejeitado com mensagem clara.
- **Logs sem dados sensíveis.** Ficam em `%LOCALAPPDATA%\PdfLocal\logs` (3 arquivos de até 1 MB). Registram só a operação, a quantidade de páginas e a classe do erro técnico. Nunca registram nome de arquivo, caminho ou conteúdo. Isso foi conferido no log gerado pelo executável empacotado.
- **Configurações.** Só o tema escolhido é guardado, em `%LOCALAPPDATA%\PdfLocal\settings.properties`.
- **Arquivos temporários.** Enquanto um PDF grande está aberto, o PDFBox mantém um cache em arquivo temporário na pasta `%TEMP%`, apagado ao fechar o arquivo ou o app. Num encerramento forçado do processo durante os testes não restou nenhum arquivo, mas, se algum aparecer (nomes `PDFBox*.tmp` ou `pdflocal-*.tmp`), ele pode ser apagado manualmente.
- **Assinaturas digitais.** O PDF gerado não mantém as assinaturas digitais dos originais. O app avisa antes de salvar quando algum PDF da lista está assinado.
- **PDFs protegidos por senha** não são abertos: o app mostra "PDF protegido por senha". Arquivos corrompidos mostram "Não foi possível ler o arquivo". Em nenhum caso o app deve encerrar.

### Dependências e vulnerabilidades

O build gera o SBOM (CycloneDX) em `target\bom.json` e `target\bom.xml` a cada `mvn package`.

A verificação do OWASP dependency-check fica num perfil separado e falha o build se encontrar CVE com nota 7 ou mais (alta ou crítica):

```powershell
mvn -Psecurity verify -DnvdApiKey=<sua chave>
```

O dependency-check exige uma chave de API gratuita do NVD (solicite em https://nvd.nist.gov/developers/request-an-api-key). Sem a chave, o NVD recusa as consultas com "Invalid apiKey" e o build do perfil `security` falha antes da análise. O relatório sai em `target\dependency-check-report.html`.

Verificação feita nesta versão: como a chave do NVD não estava disponível, o relatório do OWASP **não foi gerado**. Como alternativa, as 11 bibliotecas do SBOM (PDFBox 3.0.8, fontbox, pdfbox-io, commons-logging 1.4.0, AtlantaFX 2.1.0, metadata-extractor 2.21.0, xmpcore 6.1.11 e os módulos do JavaFX 21.0.12) foram consultadas no OSV.dev, e nenhuma tinha aviso de segurança registrado. Isso não substitui o relatório do dependency-check.

## Estrutura do projeto

```
br.com.pdflocal
├── app        Launcher, PdfLocalApp
├── model      PageItem, SourceDocument, PageSize, PageOrder, PageEdits, UndoHistory, PageGroups
├── service    FileTypeDetector, SourceRegistry, ThumbnailService, DocumentComposer,
│              ImageToPageService, ImportService, ExifOrientation
├── ui         MainView, PageGrid, PageCard, PreviewPane, Toolbar, DocumentSession, Dialogs
└── util       Exceções de domínio, Messages, AppSettings, LogSetup
```

As regras do projeto e o plano de fases estão em `CLAUDE.md` e `docs/PLANO.md`.
