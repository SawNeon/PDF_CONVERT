# Guia do código

Este documento explica como o aplicativo é feito: a ideia central, o caminho de uma operação do início ao fim, e o papel de cada pacote e de cada classe. Para saber o que cada **ferramenta** faz e como, veja [`FERRAMENTAS.md`](FERRAMENTAS.md).

## 1. A ideia central

O aplicativo mantém uma **lista ordenada de páginas** (`PageItem`). Cada item é *uma página de um PDF de origem* (arquivo + número da página) ou *uma imagem*, mais uma rotação (0, 90, 180 ou 270). A tela mostra essa lista como miniaturas.

Três regras valem para tudo:

1. **Os arquivos originais nunca são alterados.** Girar, excluir, reordenar e desfazer só mudam a lista, na memória.
2. **O PDF final só é montado ao salvar**, a partir dos originais abertos, por uma classe única (`DocumentComposer`).
3. **Tudo é local.** Nenhuma chamada de rede é feita pelo aplicativo. Quando ele chama programas externos (Office, LibreOffice, navegador), isola o tráfego deles (ver seção 6).

Por isso "juntar PDFs", "converter imagens em PDF", "comprimir", "numerar páginas" e quase todas as outras funções são **a mesma operação** com um passo diferente no fim.

## 2. O caminho de uma operação

```
Adicionar arquivos / arrastar
      │  ImportService + FileTypeDetector (valida pelo conteúdo)
      ▼
SourceRegistry  ── guarda cada PDF aberto (PDDocument) e uma trava por arquivo
      │
      ▼
Lista de PageItem (DocumentSession.items) ──► PageGrid mostra as miniaturas
      │            editar a lista: girar, excluir, reordenar, desfazer
      ▼
Ferramenta escolhida no menu ──► diálogo de opções ──► escolher onde salvar
      │
      ▼
DocumentComposer.produce(...)
   1. valida (lista não vazia, não sobrescreve um arquivo de origem, pasta existe)
   2. cria um arquivo temporário na MESMA pasta do destino
   3. trava os PDFs de origem usados
   4. monta um PDF em memória importando as páginas, na ordem da lista
   5. roda o "trabalho": passos de saída (OutputStep) e grava no temporário
   6. troca o temporário pelo destino de forma atômica (ATOMIC_MOVE)
   7. apaga o temporário se algo falhou; destrava
```

Como a gravação é sempre num temporário seguido de troca atômica, **uma falha ou um cancelamento nunca deixa um arquivo pela metade** nem estraga um arquivo que já existia.

Algumas ferramentas não partem da lista, e sim de **um arquivo que você escolhe** (Reparar, Preencher formulário, Word/Excel/PowerPoint/HTML para PDF). Elas têm a própria rotina, mas reaproveitam as mesmas peças de segurança (`OutputFiles`, `PdfLoader`).

## 3. Pacotes

```
br.com.pdflocal
├── app      ponto de entrada
├── model    dados e regras puras (sem JavaFX, sem PDFBox pesado)
├── service  tudo que mexe em PDF, imagem, arquivo e programas externos (sem JavaFX)
├── ui       telas, diálogos e a ligação entre eles (JavaFX)
└── util     mensagens, configuração, log, erros de domínio
```

**`service` e `model` não conhecem o JavaFX.** Isso permite testá-los com JUnit puro, sem abrir janela. Só `ui` e `app` usam JavaFX.

### 3.1 `app`

| Classe | Papel |
|---|---|
| `Launcher` | Contém o `main` e chama `Application.launch`. Existe separada de `PdfLocalApp` porque, rodando pelo classpath, uma classe que estende `Application` não pode ser a classe principal (o JavaFX reclama de "componentes ausentes"). |
| `PdfLocalApp` | Aplicação JavaFX. Instala o log, lê as configurações, aplica o tema salvo, cria a janela (`MainView`), define ícone e tamanho mínimo, e importa arquivos passados na linha de comando. Ao fechar, libera tudo (`MainView.close`) e desliga o log. |

### 3.2 `model` — dados e regras puras

| Classe | Papel |
|---|---|
| `PageItem` | Uma página da lista: `id`, `sourceId` + `pageIndex` (se for página de PDF) **ou** `imagePath` (se for imagem), e `rotation`. É um `record` imutável que se recusa a existir em estado inválido (rotação diferente de múltiplos de 90, ou nem página nem imagem). |
| `SourceDocument` | Um PDF aberto: `id`, caminho, quantidade de páginas e se tem assinatura digital. |
| `FileType` | PDF, JPEG, PNG, GIF, BMP, TIFF. |
| `PageSize` | `ORIGINAL` (a página tem o tamanho da imagem) ou `A4` (a imagem é encaixada). Vale só para imagens. |
| `ViewMode` | `BY_PAGE` (cada página uma miniatura) ou `BY_FILE` (um cartão por arquivo). |
| `PageEdits` | Funções puras que devolvem uma **nova** lista: `rotate` e `delete`. Valida os índices. |
| `PageOrder` | Função pura `moveMany`: move um conjunto de itens para uma posição, mantendo a ordem relativa. É o que a grade usa ao arrastar. |
| `PageGroups` / `PageGroup` | Agrupam itens consecutivos para o modo "por arquivo". Um grupo sabe seu início, seus itens e seus índices. |
| `UndoHistory` | Pilha de até 30 "fotografias" da lista. `referencedSources` diz quais PDFs ainda são necessários para um eventual desfazer (para só fechar um PDF quando nada mais o usa). |
| `CompressionLevel` | `LIGHT` (200 dpi, JPEG 80), `MEDIUM` (150 dpi, 65), `STRONG` (100 dpi, 50) e `NONE`. |
| `PageNumberOptions` | Posição (6 cantos), padrão do texto (`{n}`, `{total}`), número inicial, pular a primeira, tamanho. Valida tudo no construtor. |
| `WatermarkOptions` | Texto **ou** imagem, opacidade (5% a 100%) e se vai na diagonal. |
| `CropOptions` | Área a manter, em frações da página exibida (esquerda, topo, direita, base), e o modo (`HIDE_OUTSIDE` ou `REMOVE_OUTSIDE`). |
| `RedactionArea` | Uma área a tarjar, em frações da página. |
| `ImageExportOptions` | Formato (JPEG/PNG) e resolução (72 a 400 dpi). |
| `OfficeKind` | `WORD`, `EXCEL`, `POWERPOINT`, com o nome do executável e o nome COM. |

### 3.3 `service` — o trabalho de verdade

**Abrir e validar arquivos**

| Classe | Papel |
|---|---|
| `FileTypeDetector` | Descobre o tipo pelo **conteúdo** (os primeiros bytes): `%PDF-`, `FF D8 FF`, `89 50 4E 47...`, GIF, BMP, TIFF. A extensão é ignorada. |
| `PdfLoader` | Único lugar que abre um PDF com `Loader.loadPDF`. Aceita senha, usa cache em arquivo temporário (para PDFs grandes) e traduz os erros do PDFBox em erros do domínio (`PasswordProtectedException`, `WrongPasswordException`, `CorruptFileException`). |
| `SourceRegistry` | Guarda todos os PDFs abertos, cada um com uma **trava** (`ReentrantLock`). Isso é essencial porque `PDDocument` e `PDFRenderer` não são seguros para várias threads. Também diz se algum PDF da lista tem assinatura. |
| `ImportService` / `ImportResult` | Recebe uma lista de arquivos, valida, abre e devolve os itens e as falhas. Falhas por senha trazem o motivo (`PASSWORD_REQUIRED` ou `PASSWORD_WRONG`) e o caminho, para o app poder pedir a senha. |
| `ExifOrientation` | Lê a orientação EXIF das fotos (biblioteca metadata-extractor). |

**Montar o PDF final**

| Classe | Papel |
|---|---|
| `DocumentComposer` | O coração. `compose` monta um PDF com passos de saída; `produce` é a versão genérica (valida, trava, cria temporário, monta em memória, chama o "trabalho", troca atomicamente). A exportação de imagens e todas as ferramentas da lista passam por aqui. |
| `OutputStep` | Interface de um "passo de saída": recebe o PDF já montado e o altera antes de gravar. Tem `requiresClassicStructure()` para passos que exigem gravar sem fluxos de referência cruzada (PDF/A). |
| `StripThumbnailsStep` | Roda em **todo** salvamento: remove as miniaturas embutidas das páginas, que podem guardar a prévia de um conteúdo antigo. |
| `OutputFiles` | Rotinas de gravação segura compartilhadas: troca atômica, apagar temporário, apagar pasta, espaço em disco, "é o mesmo arquivo?". |
| `SaveListener` / `SaveResult` | Progresso e cancelamento (`onPage`, `onCompress`, `onTransform`, `isCancelled`) e o resultado (páginas, bytes, estatística de compressão). |
| `ImageToPageService` | Transforma uma imagem em página: JPEG e PNG entram **sem recompressão** (`createFromFileByContent`), os outros formatos sem perda; corrige a orientação EXIF; no modo A4 encaixa com margem de 10 mm e escolhe retrato ou paisagem. |

**Passos de saída e ferramentas de edição**

| Classe | Papel |
|---|---|
| `PdfCompressor` / `CompressStep` | Reduz imagens (ver ferramentas). |
| `ImageResolutionFinder` | Mede a resolução real (ppi) de cada imagem lendo o conteúdo da página e acompanhando `q`, `Q`, `cm`. |
| `ImageScaler` | Reduz imagens em etapas (metade a cada vez) para manter a nitidez. |
| `ImageShare` | Calcula que fração do tamanho dos arquivos é imagem (para avisar "pouco ganho"). |
| `PageNumbersStep` | Escreve o número de cada página. |
| `WatermarkStep` | Aplica marca d'água de texto ou imagem. |
| `CropStep` | Recorta (ocultando ou removendo de verdade). |
| `RedactStep` | Tarja áreas apagando o texto de verdade. |
| `PdfAStep` | Converte para PDF/A-1b. |
| `ProtectStep` | Criptografa com senha (AES-256). |
| `PageGeometry` | Converte um ponto "como o usuário vê a página" em coordenadas do PDF, levando em conta rotação (0/90/180/270) e área de corte. É o que faz número e marca d'água aparecerem no lugar certo em páginas giradas. |
| `EditableResources` | Cria uma **cópia privada** dos recursos de uma página (fontes, transparência, imagens) antes de editá-la. Páginas importadas compartilham esses recursos com o PDF aberto na tela; sem a cópia, escrever uma fonte nova alteraria o original em memória. |
| `StandardText` | Fontes padrão do PDF e verificação de quais textos elas conseguem escrever. |
| `PageRasterizer` | Renderiza uma página e a substitui por uma imagem. Base do recorte seguro, do tarjar e do PDF/A. Limita os pixels (25 milhões) para não estourar a memória. |

**Converter**

| Classe | Papel |
|---|---|
| `PageExporter` | PDF para JPG/PNG (ZIP quando há várias páginas). |
| `PdfRepairer` | Reconstrói um PDF danificado. |
| `FormFiller` | Lê os campos de um formulário e grava uma cópia preenchida. |
| `OfficeConverter` | Word/Excel/PowerPoint para PDF (Office instalado ou LibreOffice). |
| `HtmlConverter` / `HtmlPolicy` | HTML para PDF por um navegador isolado, sem scripts. |
| `PdfToOffice` | PDF para Word (LibreOffice), para PowerPoint (imagens) e para Excel (tabelas). |
| `TableExtractor` / `XlsxWriter` / `CellValues` | Detecta tabelas no texto e grava a planilha `.xlsx`; decide o que vira número. |
| `OfficeFileDetector` | Valida Word/Excel/PowerPoint pelo conteúdo e recusa macros. |
| `OfficeRunner` / `LibreOfficeRunner` / `LibreOfficeProfile` | Executam o script do PowerShell (Office) e o LibreOffice, com tempo limite, cancelamento e isolamento de rede. |
| `ExternalProcess` | Inicia um processo externo, espera com tempo limite, atende ao cancelamento e mata a árvore de processos. |
| `SystemProbe` / `WindowsSystemProbe` | Descobrem o que existe na máquina (Office instalado? aberto? LibreOffice? navegador?). A interface existe para os testes poderem simular. |

**Miniaturas**

| Classe | Papel |
|---|---|
| `ThumbnailService` | Gera miniaturas a 50 dpi fora da thread da tela, com cache LRU de 200 imagens. Há **uma thread de renderização por PDF** (porque o renderizador não é seguro entre threads) e duas threads para imagens. Pedidos repetidos da mesma miniatura se juntam. Ao remover um PDF, libera a thread e as miniaturas dele. |
| `ImageThumbnail` | Miniatura de uma imagem, já com a orientação aplicada. |

### 3.4 `ui` — telas

| Classe | Papel |
|---|---|
| `MainView` | A janela principal: monta barra de ferramentas, grade e barra de status; trata arrastar-e-soltar, atalhos (Delete, Ctrl+Z, Ctrl+A, Esc) e **todos os fluxos das ferramentas** (escolher arquivo, abrir diálogo, escolher destino, rodar, mostrar o resultado). Controla o estado "ocupado" (botões desabilitados, barra de progresso, botão cancelar). |
| `DocumentSession` | A ponte entre a tela e o `service`. Guarda a lista observável de itens, o histórico de desfazer, o registro de PDFs e as miniaturas. Roda o trabalho pesado em **uma thread de fundo** (`pdflocal-io`). Expõe `startSave`, `startExport`, `startConversion`, `repair`, `inspectForm`, etc. |
| `Toolbar` | Barra superior: logotipo (abre "Sobre"), Adicionar, Limpar, modos de exibição, tamanho para imagens, tema, **menu Ferramentas** e Salvar. |
| `Tool` / `ToolGroup` | Cada item do menu é um `Tool` (grupo, texto, ação, "precisa de páginas?"). Acrescentar uma ferramenta é acrescentar uma linha numa lista. |
| `PageGrid` | A grade de miniaturas: seleção, arrastar para reordenar com indicador de inserção, rolagem automática ao arrastar, construção em lotes e miniaturas só das páginas visíveis. |
| `PageCard` | Um cartão: miniatura, selo, nome da origem e botões (girar, excluir) que aparecem ao passar o mouse. |
| `PreviewPane` | Pré-visualização grande (110 dpi) com zoom, ajuste, navegação por teclado e pré-carga da página vizinha. |
| `GridSelection` | Regras de seleção (clique, Ctrl, Shift). |
| `GridGeometry` | Calcula onde inserir ao soltar (qual célula é a mais próxima e de que lado). |
| `ZoomModel` | Degraus de zoom e modo "ajustar". |
| `Dialogs` | Diálogos comuns: escolher arquivos, escolher destino, confirmar limpar, avisar perda de assinatura, erros, "Sobre". |
| `CompressDialog`, `PageNumbersDialog`, `WatermarkDialog`, `CropDialog`, `RedactDialog`, `ProtectDialog`, `PasswordDialog`, `FormDialog`, `ExportImagesDialog` | Um diálogo por ferramenta com opções. |
| `RegionPickerPane`, `AreasPickerPane` | Controles para marcar uma área (recorte) ou várias áreas (tarjar) sobre a página. |
| `NormalizedRegion`, `PixelRotation`, `ImageRotation` | Contas puras (área arrastada para fração da página; rotação de pixels). Separadas para serem testáveis. |
| `Appearance`, `Theme`, `AppIcons` | Tema claro/escuro, folha de estilo e ícones. |

### 3.5 `util`

| Classe | Papel |
|---|---|
| `Messages` | Lê `messages.properties` (todos os textos em português) e guarda as chaves de erro. |
| `AppSettings` | Guarda o tema em `%LOCALAPPDATA%\PdfLocal\settings.properties`. |
| `LogSetup` | Log em `%LOCALAPPDATA%\PdfLocal\logs` (3 arquivos de 1 MB). |
| `AppInfo` | Versão do aplicativo. |
| `FileSizes`, `OutputNames` | Formatação de tamanhos ("1,2 MB") e sugestão do nome de saída. |
| `PdfLocalException` e filhas | Erros do domínio com **mensagem pronta para o usuário**: `PasswordProtectedException`, `WrongPasswordException`, `CorruptFileException`, `UnsupportedFileException`, `SaveCancelledException`. |

## 4. Threads e concorrência

| Thread | O que faz |
|---|---|
| Thread do JavaFX | Só desenha e responde a cliques. Nunca renderiza PDF. |
| `pdflocal-io` (uma só, em `DocumentSession`) | Importar, salvar, converter, exportar, reparar. Uma de cada vez, o que também evita duas operações pesadas ao mesmo tempo. |
| `thumbnail-pdf-N` (uma por PDF) | Renderiza miniaturas e pré-visualizações daquele PDF. |
| `thumbnail-image-N` (duas) | Miniaturas de imagens. |

O resultado volta para a tela com `Platform.runLater`. Antes de tocar num PDF aberto, o código pega a **trava** daquele PDF no `SourceRegistry`. O salvamento trava todos os PDFs que usa (em ordem de identificador, para evitar impasse entre duas operações).

O cancelamento é cooperativo: o botão Cancelar liga um sinal que `SaveListener.isCancelled()` devolve, e cada laço do trabalho consulta esse sinal. Para programas externos, o `ExternalProcess` consulta o sinal a cada 200 ms e mata o processo.

## 5. Como o programa trata dados sensíveis (LGPD)

- Nenhum nome de arquivo, caminho ou conteúdo vai para o log: só a operação, a quantidade de páginas e o **tipo** do erro técnico.
- O formato do arquivo é decidido pelo conteúdo, nunca pela extensão.
- As senhas ficam em `char[]` e são zeradas depois do uso (limite do Java: o campo de senha da tela entrega uma `String`, que não dá para apagar à força).
- Os arquivos temporários ficam na pasta de destino (ou em pastas curtas próprias, no caso do LibreOffice e do navegador) e são apagados no fim, inclusive em caso de erro.
- O PDF gerado **não herda** título, autor nem metadados dos originais, e perde as miniaturas embutidas.
- Os testes nunca usam documentos reais: PDFs e imagens são gerados por código dentro dos testes, e o `.gitignore` bloqueia PDFs e imagens fora de `src/test/resources`, além de despejos de memória.

## 6. Programas externos e rede

O aplicativo só chama programas que já existem na máquina, e **nunca** os que já estão abertos com o trabalho do usuário.

- **Office (Word, Excel, PowerPoint):** automação do Windows por um script de PowerShell embutido no JAR (`scripts/office-convert.ps1`), enviado com `-EncodedCommand` (não depende da política de execução de scripts) e com os caminhos em variáveis de ambiente (nunca montados no texto do comando). Abre somente leitura, com macros desligadas, sem atualizar vínculos. Se o programa **já está aberto**, não é automatizado.
- **LibreOffice:** perfil numa pasta curta e fixa (`%LOCALAPPDATA%\PdfLocal\lo`) porque caminhos longos o derrubam; executado com proxy apontando para uma porta local fechada, porque ele tenta verificar atualização na internet por conta própria.
- **Navegador (Edge ou Chrome):** perfil temporário próprio, DNS bloqueado, proxy morto (inclusive para endereços locais) e uma política de conteúdo que impede scripts.
- Todos têm tempo limite e são cancelados matando a árvore de processos. Instâncias do Office **sem janela** deixadas por um tempo limite são encerradas (as que têm janela, nunca).

## 7. Recursos

| Arquivo | Conteúdo |
|---|---|
| `messages.properties` | Todos os textos da interface, em português, em UTF-8. Um teste confere que o arquivo continua em UTF-8. |
| `pdflocal.css` | Paleta, tema escuro, marca d'água de fundo e estilos dos diálogos. |
| `images/` | Logotipos, símbolo, ícones e marcas d'água da identidade visual. |
| `scripts/office-convert.ps1` | Automação do Office (Word, Excel e PowerPoint para PDF, e imagens para PowerPoint). |
| `scripts/libreoffice-profile.xcu` | Configuração semeada no perfil do LibreOffice (macros desligadas, sem arquivos recentes, vínculos nunca atualizados). |

## 8. Testes

O projeto tem 44 classes de teste e mais de 400 testes (`mvn test`). Eles cobrem as regras puras do modelo, cada serviço com PDFs gerados por código (inclusive renderizando páginas para conferir que o número, a marca d'água e a tarja aparecem onde devem), a sessão e a ligação da tela.

- `mvn test` roda a suíte padrão (rápida, sem abrir programas).
- `mvn test -Dtest.excluded.groups=none` roda também os testes **que abrem programas de verdade** (Word, Excel, PowerPoint, LibreOffice, Edge). São lentos e devem ser rodados com esses programas fechados. Estão marcados com `@Tag("office")`.
- O validador oficial do Apache (`preflight`) é usado **só nos testes** para provar que o PDF/A gerado é válido; não entra no instalador.

## 9. Como acrescentar uma ferramenta

1. **Se atua sobre a lista de páginas:** crie uma classe que implemente `OutputStep` em `service`, com testes que gerem um PDF e confiram o resultado.
2. Crie as opções em `model` (um `record` que valida no construtor).
3. Crie o diálogo em `ui` (copie o mais parecido) e os textos em `messages.properties`.
4. Em `MainView.tools()`, acrescente `new Tool(grupo, "chave.do.texto", this::minhaAcao, true)`.
5. Na ação, abra o diálogo e chame `startSaving(SaveJob.of("output.suffix.xxx", new MeuStep(opcoes)))`. O fluxo seguro de salvar, o progresso, o cancelamento e os erros já vêm prontos.
6. **Se atua sobre um arquivo escolhido** (e não sobre a lista): use `convertFile`/`listConversion` como as conversões, ou siga o exemplo de `repair()`.
7. Documente em `FERRAMENTAS.md` e rode `mvn test`.

## 10. Glossário

| Termo | Significado |
|---|---|
| Página de origem | Uma página de um PDF que você adicionou. |
| Passo de saída | Uma alteração aplicada ao PDF montado, logo antes de gravar. |
| Rasterizar | Transformar uma página em imagem (perde o texto selecionável). |
| Achatar (flatten) | Fazer os valores de um formulário virarem parte da página. |
| Troca atômica | Substituir um arquivo de uma vez só, sem estado intermediário. |
| Instância única | Programa que, ao ser aberto de novo, reaproveita a janela já aberta (Excel, PowerPoint). |
