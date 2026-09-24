# PdfLocal — contexto do projeto

App desktop para Windows que **junta PDFs** e **converte imagens em PDF**, com interface visual (miniaturas, arrastar e soltar, pré-visualização). Uso interno por poucas pessoas numa operadora de saúde: os documentos podem conter **dados sensíveis (LGPD)**. Por isso, **tudo é processado localmente, sem nenhum acesso à internet**.

## Stack

- Java 21 (LTS) + Maven
- JavaFX 21 (`javafx-controls`, `javafx-swing` para `SwingFXUtils`)
- Apache PDFBox 3.0.x (`org.apache.pdfbox:pdfbox`)
- AtlantaFX (`io.github.mkpaz:atlantafx-base`) para o tema claro/escuro
- metadata-extractor (`com.drewnoakes:metadata-extractor`) para ler a orientação EXIF das fotos
- JUnit 5 para os testes
- Empacotamento com `jpackage`

Use as versões estáveis mais recentes dessas linhas e fixe-as em `<properties>` no `pom.xml`.

## Modelo central (importante)

"Juntar PDFs" e "Imagens → PDF" são **a mesma operação**: a tela mantém uma lista ordenada de `PageItem`, e o PDF final é montado a partir dela.

- `PageItem` = página de um PDF de origem (`sourceId` + `pageIndex`) **ou** uma imagem (`Path`), mais `rotation` (0/90/180/270).
- A UI **nunca altera os arquivos originais**. Girar, excluir e reordenar só mudam a lista.
- O `DocumentComposer` gera o PDF final a partir dos originais somente ao salvar.

## Pacotes

```
br.com.pdflocal
├── app        Launcher (main que NÃO estende Application), PdfLocalApp
├── model      PageItem, SourceDocument, PageSize (ORIGINAL | A4)
├── service    FileTypeDetector, SourceRegistry, ThumbnailService,
│              DocumentComposer, ImageToPageService, ExifOrientation
├── ui         MainView, PageGrid, PageCard, PreviewPane, Toolbar, dialogs
└── util       Erros de domínio, mensagens para o usuário
```

## Regras obrigatórias

### Segurança e privacidade
- **Nenhuma chamada de rede.** Nenhuma dependência que faça telemetria, verificação de atualização ou download.
- **Nunca sobrescrever originais.** O arquivo de saída é escolhido pelo usuário (FileChooser). Grave primeiro num arquivo temporário na mesma pasta e depois faça `Files.move(..., ATOMIC_MOVE)`.
- **Validar o tipo pelo conteúdo (magic bytes), não pela extensão:** PDF `%PDF-`, JPEG `FF D8 FF`, PNG `89 50 4E 47 0D 0A 1A 0A`. Rejeitar o resto com uma mensagem clara.
- **Logs sem dados sensíveis:** nunca registrar nome de arquivo, caminho completo ou conteúdo. Registrar só a operação, a quantidade de páginas e erros técnicos. Os logs ficam em `%LOCALAPPDATA%\PdfLocal\logs`.
- **Testes nunca usam documentos reais.** Os PDFs e imagens de teste são gerados por código dentro dos próprios testes. O `.gitignore` bloqueia `*.pdf`, `*.jpg` e `*.png` fora de `src/test/resources`.

### PDFBox
- Carregar com `Loader.loadPDF(file)` (API 3.x). Para arquivos grandes, usar cache em arquivo temporário (`IOUtils.createTempFileOnlyStreamCache()`).
- **`PDDocument` e `PDFRenderer` não são thread-safe.** Todas as renderizações de um mesmo documento devem rodar numa única thread (um executor de thread única por documento, ou uma fila).
- No `DocumentComposer`, usar `importPage` e manter os documentos de origem **abertos até o `save()` terminar**. Depois, fechar tudo (try-with-resources / `SourceRegistry.close()`).
- `InvalidPasswordException` → mensagem "PDF protegido por senha". Arquivo corrompido → mensagem "Não foi possível ler o arquivo". O app nunca pode quebrar.
- Aviso na interface: o PDF gerado **não mantém assinaturas digitais** dos originais.

### Imagens
- `PDImageXObject.createFromFileByContent`. JPEG entra sem recompressão; PNG, sem perda.
- Corrigir a rotação usando a orientação EXIF.
- `PageSize.ORIGINAL`: a página fica do tamanho da imagem. `PageSize.A4`: a imagem é encaixada mantendo a proporção, com margem de 10 mm, e a orientação retrato/paisagem é escolhida automaticamente.

### Desempenho
- Miniaturas a **50 DPI**; pré-visualização grande a ~110 DPI.
- A renderização acontece sempre fora da JavaFX Application Thread (`Task`/`ExecutorService`), e a tela é atualizada com `Platform.runLater`.
- Renderizar só as miniaturas visíveis; manter um cache LRU limitado (ex.: 200 imagens).
- Ao remover um arquivo da lista, fechar o `PDDocument` e liberar suas miniaturas.
- Memória máxima da JVM no pacote final: `-Xmx1g`.

### Código
- Services sem dependência de JavaFX, para serem testáveis com JUnit puro.
- Mensagens da interface em **português**.
- Rodar `mvn test` ao fim de cada fase; nenhuma fase termina com teste quebrado.

## Como trabalhar neste projeto
- Siga as fases descritas em `docs/PLANO.md`, **uma por vez**. Ao terminar uma fase, pare, resuma o que foi feito e como testar, e aguarde.
- Antes de adicionar qualquer dependência nova, explique o motivo e peça confirmação.
