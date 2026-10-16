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
- **Comprimir PDF** abre uma janela com três níveis (Leve, Média e Forte), e **Salvar...** grava o arquivo menor. Para comprimir um PDF, adicione-o, clique em Comprimir PDF, escolha o nível e salve. Ao final, a barra de status mostra o tamanho antes e depois.
- O botão de tema alterna entre claro e escuro, e a escolha é lembrada.

## Como funciona a compressão

A compressão age só nas **imagens** do PDF (escaneamentos e fotos), que são quase todo o peso desses arquivos. O texto e as fontes não mudam. Para cada imagem, o app mede a resolução real em dpi (o tamanho em que ela é de fato desenhada na página, inclusive dentro de blocos aninhados), reduz até o alvo do nível e recodifica em JPEG:

| Nível | Resolução máxima | Qualidade JPEG |
|---|---|---|
| Leve | 200 dpi | 80 |
| Média | 150 dpi | 65 |
| Forte | 100 dpi | 50 |

- Só é reduzida a imagem que estiver mais de 15% acima do alvo do nível. Abaixo disso a resolução é mantida, porque a perda de nitidez não compensa o ganho.
- Se algum uso da imagem não puder ser medido, ela não é alterada.
- A imagem só é trocada se o resultado ficar pelo menos 5% menor, e nunca é ampliada.
- Ficam como estão: imagens com transparência ou máscara, de 1 bit, CMYK, dentro de blocos de formulário (XObjects de formulário) e as que não puderem ser lidas.
- Imagens em tons de cinza continuam em cinza.
- A compressão é feita na cópia que está sendo montada. Os arquivos de origem, no disco e na memória, não são alterados.
- A perda de qualidade é permanente no arquivo gerado. Em PDFs só de texto o ganho é pequeno, e o diálogo avisa quando menos de 30% do tamanho é imagem.

## Limpeza de segurança ao salvar

Todo PDF gerado sai sem as **miniaturas embutidas** das páginas. Um PDF editado pode guardar a prévia do conteúdo antigo, e isso vazaria dado que o usuário já alterou. O PDF gerado também não herda título, autor e demais metadados dos arquivos de origem. A limpeza é feita só na cópia que está sendo montada.
- Em testes com escaneamentos sintéticos de A4 a 300 dpi, a redução ficou entre 55% (Leve) e 90% (Forte). O resultado real depende dos seus documentos.

## Ferramentas de edição

O menu **Ferramentas** reúne as funções que atuam sobre a lista de páginas. Todas geram um PDF novo e nunca alteram os originais, e o nome sugerido para salvar já indica a operação.

| Ferramenta | O que faz |
|---|---|
| Girar todas as páginas | Gira a lista inteira em 90 graus (dá para desfazer com Ctrl+Z) |
| Inserir números de página | Posição (6 cantos), formato (`1`, `Página 1`, `Página 1 de 10`, `1 / 10`), número inicial, tamanho da letra e opção de pular a primeira página |
| Inserir marca d'água | Texto (ex.: CONFIDENCIAL) ou imagem JPEG/PNG, no centro, com opacidade e opção diagonal |
| Preencher formulário | Lê os campos de um PDF (texto, caixa de seleção, opção única, lista), mostra um formulário e grava uma cópia preenchida, com opção de tornar os campos não editáveis |
| Recortar PDF | Marque na página a área que será mantida; a mesma área vale para todas as páginas |

- Números e marca d'água ficam no canto **visível** da página, mesmo em páginas giradas ou com área de corte.
- **Preencher formulário** atua sobre **um PDF escolhido** (não sobre a lista de páginas) e grava uma cópia. Ficam de fora campos somente leitura, de senha, botões, assinaturas e listas de múltipla escolha. Formulários dinâmicos (XFA) não são suportados e o aplicativo avisa. Ao achatar, nenhum campo editável permanece no arquivo. Assinaturas digitais não são mantidas.
- O texto da marca e o formato do número usam fontes padrão do PDF; letras com acento funcionam, símbolos fora desse conjunto são recusados.
- **Recortar tem dois modos.** *Remover o conteúdo de fora* (padrão) transforma a página em imagem e apaga de verdade o que ficou fora, mas o texto deixa de ser selecionável. *Apenas ocultar* mantém o texto, porém o conteúdo cortado continua dentro do arquivo e pode ser recuperado; não use em documentos com dados sensíveis.

## Converter PDF em imagens

**Ferramentas, PDF para JPG ou PNG** exporta as páginas da lista como imagens: uma página gera um arquivo de imagem, várias páginas geram um **ZIP**. Girar, excluir e reordenar valem na exportação.

- Formatos: JPG (menor, padrão) e PNG (sem perda). Resolução: 100, 150 (padrão), 200 ou 300 dpi.
- A resolução é reduzida automaticamente em páginas muito grandes, para não esgotar a memória.
- Os arquivos dentro do ZIP se chamam `pagina-001.jpg`, `pagina-002.jpg`... e nunca usam o nome do documento de origem.
- A gravação é a mesma do PDF: arquivo temporário e troca atômica, cancelamento disponível, e nunca sobrescreve um arquivo de origem.

## Segurança, PDF/A e reparo

| Ferramenta | O que faz |
|---|---|
| Proteger com senha | Gera um PDF que só abre com a senha escolhida (AES de 256 bits, senha de pelo menos 8 caracteres, digitada duas vezes). A senha de proprietário é aleatória. |
| Tarjar dados | Marque áreas sobre dados sensíveis (CPF, nomes, endereços). As páginas marcadas viram imagem com a tarja pintada nos pixels, e o texto coberto é apagado de verdade do arquivo. |
| PDF para PDF/A | Gera um PDF/A-1b para arquivamento de longo prazo. Cada página vira imagem com perfil de cor sRGB e metadados PDF/A. |
| Reparar PDF | Reconstrói a estrutura de um PDF danificado e grava uma cópia saudável, informando quantas páginas foram recuperadas. |

- **Senha de PDF protegido.** Ao adicionar um PDF protegido, o aplicativo pede a senha em vez de recusar o arquivo. O PDF gerado a partir dele **não tem senha**; use Proteger com senha para proteger o resultado.
- A senha nunca vai para o registro de atividades nem para as configurações, e a cópia na memória é apagada depois do uso. Limite do Java: o texto digitado num campo de senha fica em `String`, que não pode ser apagada à força da memória antes da coleta de lixo. Guarde a senha em local seguro: sem ela o arquivo não pode ser recuperado.
- **Tarjar dados de verdade.** Cobrir um PDF com um retângulo preto deixa o texto por baixo, que ainda pode ser copiado. Aqui a página inteira é convertida em imagem antes de pintar a tarja, então não há texto para recuperar. As páginas sem tarja continuam com texto selecionável. Se uma tarja apontar para uma página que não existe, o salvamento falha em vez de pular a tarja.
- **PDF/A e texto.** Como a página vira imagem, o texto deixa de ser selecionável e pesquisável, e o arquivo costuma crescer. O resultado é validado nos testes automáticos com o validador oficial do Apache PDFBox (`preflight`, usado só nos testes, não vai no instalador), que cobre o PDF/A-1b.
- **Reparar não faz milagre.** Recupera arquivos com tabela de referências quebrada, final cortado ou lixo antes do cabeçalho. Conteúdo realmente destruído não volta, e as páginas que não puderem ser lidas são contadas como perdidas no relatório. Assinaturas digitais não são mantidas na cópia.
- Ao gravar PDF/A o aplicativo usa a estrutura clássica do PDF (sem fluxos de referência cruzada), exigida pelo PDF/A-1.

## Conversões com Office, LibreOffice e navegador

| Ferramenta | Como funciona |
|---|---|
| Word, Excel e PowerPoint para PDF | Usa o Office instalado (automação do Windows) ou, se ele não puder ser usado, o LibreOffice |
| HTML para PDF | Usa o Edge (ou o Chrome) em modo sem janela |
| PDF para Word | Usa o LibreOffice; o resultado é editável, mas o layout fica aproximado |
| PDF para Excel | Detecta tabelas no texto do PDF e grava uma planilha (`.xlsx`), uma aba por página; não usa programa externo |
| PDF para PowerPoint | Cada página vira um slide com a imagem da página (não editável); exige o PowerPoint |

Nada disso usa biblioteca nova: o aplicativo só chama programas que já existem na máquina.

**Regras de segurança**
- Os arquivos são validados pelo **conteúdo**, não pela extensão. Arquivos com **macros** são recusados.
- O Office abre o arquivo somente para leitura, com macros desligadas, sem atualizar vínculos externos e sem pedir senha: um arquivo protegido falha com mensagem, em vez de travar.
- **Programa do Office já aberto não é automatizado.** O Excel e o PowerPoint só têm uma instância, e conectar-se a ela poderia mexer ou fechar o trabalho da pessoa. Nesse caso o aplicativo usa o LibreOffice, se houver, ou pede para fechar o programa.
- Se a conversão estourar o tempo (5 minutos) ou for cancelada, o aplicativo encerra apenas as instâncias do Office **sem janela** que ele mesmo iniciou, nunca as que têm janela.
- Os caminhos seguem por variáveis de ambiente (nunca montados no texto do comando) e o script do PowerShell vai por `-EncodedCommand`, que não depende da política de execução de scripts.

**Rede**
- O LibreOffice 25 tenta verificar atualização na internet por conta própria e a configuração do perfil não o impede. Por isso o aplicativo o executa com proxy apontando para uma porta local fechada: nos testes, as tentativas chegaram a um ouvinte local e **nada saiu para a internet**.
- O HTML é impresso com uma política de conteúdo (`script-src 'none'`) inserida numa cópia temporária, **sem JavaScript**, com DNS bloqueado e proxy morto (inclusive para endereços locais). Um teste automático confirma que o navegador não faz nenhuma conexão a servidor algum. O próprio Edge tenta falar com a rede dele (sincronização, atualização de componentes) e isso também é bloqueado pelo proxy.
- Limite: Word, Excel e PowerPoint são programas da empresa e podem ter comunicação própria (conteúdo vinculado, serviços conectados). Isso está fora do controle do aplicativo.

**LibreOffice**
- O perfil dele fica numa pasta **curta e fixa**: `%LOCALAPPDATA%\PdfLocal\lo`. Caminhos longos (acima de 260 caracteres no total) fazem o LibreOffice fechar com erro logo na abertura, e a pasta fixa também evita recriar o perfil, que é lento na primeira vez.
- O perfil nasce com macros desligadas, vínculos de documento nunca atualizados e lista de arquivos recentes desligada. Se a conversão falhar, o perfil é recriado e há uma segunda tentativa.

**Decisão pendente: PDF para Word com o próprio Word.** O Word abre PDF mostrando um aviso de conversão que, em segundo plano, ninguém consegue responder, e a conversão trava. Silenciar o aviso exige alterar uma opção do Word no registro do usuário. O aplicativo **não** faz isso sem autorização, e por isso usa o LibreOffice.

**PDF para Excel (versão simples)**
- Lê o **texto selecionável** do PDF e detecta tabelas pelo layout: colunas são bordas (esquerda ou, para números, direita) compartilhadas por vários blocos curtos; linhas são blocos na mesma altura; uma tabela precisa de 3 ou mais colunas (ou 2 colunas com 3 linhas). Parágrafos nunca viram tabela, e o texto fora das tabelas entra como linhas de uma célula, na ordem da página.
- Uma aba por página. Células mescladas não são recriadas.
- **Números só são convertidos quando não há dúvida** (`1.234,56`, `12,5`, inteiros curtos). CPF, telefone, matrícula, códigos com zero à esquerda e datas ficam como texto. Nada é gravado como fórmula, e caracteres inválidos em XML são removidos.
- **PDF escaneado** não tem texto: o aplicativo avisa e não gera planilha, porque o OCR foi dispensado.
- É uma heurística. Funciona bem em relatórios, extratos e listagens gerados por sistema; tabelas sem espaço claro entre as colunas, com texto em várias linhas por célula ou com layout muito irregular podem sair imperfeitas, então confira o resultado.
- A ideia do algoritmo de detecção de tabelas vem do projeto PdfCraft (licença MIT ou Apache-2.0), reescrita em Java para PDFBox, sem copiar código. O PdfCraft ainda não exporta Excel.

**Fora do escopo:** OCR (dispensado pela equipe).

**Testes que abrem programas de verdade** (Word, Excel, PowerPoint, LibreOffice, Edge) ficam fora do `mvn test` padrão, porque são lentos. Para rodá-los, com esses programas fechados:

```
mvn test -Dtest.excluded.groups=none
```

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

O script encontra sozinho o JDK 21 (em `JAVA_HOME`, nas pastas de instalação comuns e na pasta `.jdks` do IntelliJ) e o Maven (no PATH, no cache do wrapper do Maven ou no Maven embutido do IntelliJ), e mostra no início quais usou.

Parâmetros opcionais: `-JavaHome <pasta do JDK 21>`, `-Maven <caminho do mvn>`, `-SkipBuild` (reaproveita o build anterior) e `-Msi`.

No IntelliJ, a configuração de execução **Gerar dist** (arquivo `.run/Gerar dist.run.xml`) roda esse script com um clique, no menu de execução, no topo da janela. Se ela não aparecer, crie uma em *Run | Edit Configurations | + | Shell Script*, com *Script file* `packaginguild-dist.ps1`, interpretador `powershell.exe`, opções do interpretador `-NoProfile -ExecutionPolicy Bypass -File` e diretório de trabalho na raiz do projeto.

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
├── service    FileTypeDetector, SourceRegistry, ThumbnailService, DocumentComposer, OutputStep (passos de saída:
│              CompressStep, StripThumbnailsStep, PageNumbersStep, WatermarkStep, CropStep),
│              PdfCompressor, ImageResolutionFinder, ImageShare, PageGeometry, PageRasterizer, PageExporter,
│              ProtectStep, PdfAStep, PdfRepairer, RedactStep, FormFiller, PdfLoader, OfficeConverter, HtmlConverter,
│              PdfToOffice, OfficeRunner, LibreOfficeRunner, TableExtractor, XlsxWriter, CellValues,
│              ImageToPageService, ImportService, ExifOrientation
├── ui         MainView, PageGrid, PageCard, PreviewPane, Toolbar (menu Ferramentas: Tool, ToolGroup),
│              DocumentSession, Dialogs
└── util       Exceções de domínio, Messages, AppSettings, LogSetup, OutputNames
```

As regras do projeto e o plano de fases estão em `CLAUDE.md` e `docs/PLANO.md`.

## Documentação

- [`docs/FERRAMENTAS.md`](docs/FERRAMENTAS.md): cada ferramenta, onde ela age, como funciona, o que nunca faz e os limites.
- [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md): o guia do código, com a ideia central, o caminho de uma operação, cada pacote e classe, as threads, a segurança e como acrescentar uma ferramenta.
