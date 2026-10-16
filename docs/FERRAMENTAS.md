# Guia das ferramentas

Para cada função do aplicativo: **onde ela age**, **como funciona**, o que **nunca** faz e os **limites**. A estrutura do código está em [`ARQUITETURA.md`](ARQUITETURA.md).

## Como ler este guia

Há três "lugares" onde uma ferramenta pode agir:

| Onde age | O que significa |
|---|---|
| **Lista de páginas** | Age sobre as páginas que estão na tela, na ordem e com as rotações atuais. Por isso, **girar, excluir e reordenar antes valem**. Só funciona com a lista preenchida. |
| **Arquivo que você escolhe** | Pede um arquivo (não usa a lista). Funciona com a lista vazia. |
| **Ao abrir** | Age quando você adiciona um arquivo. |

Em todos os casos **o arquivo original nunca é alterado**: o resultado é sempre um arquivo novo, escolhido por você, gravado num temporário e trocado de uma vez só. Se algo falhar ou você cancelar, não sobra arquivo pela metade.

## Resumo

| Ferramenta | Age sobre | Resultado | Programa externo |
|---|---|---|---|
| Adicionar arquivos / arrastar | Ao abrir | Páginas na lista | Não |
| Organizar (girar, excluir, reordenar, desfazer) | Lista | Lista alterada na tela | Não |
| Salvar PDF | Lista | PDF | Não |
| Comprimir PDF | Lista | PDF menor | Não |
| Reparar PDF | Arquivo | PDF reconstruído | Não |
| Imagens para PDF | Ao abrir / lista | Páginas na lista | Não |
| Word / Excel / PowerPoint para PDF | Arquivo | PDF | Office ou LibreOffice |
| HTML para PDF | Arquivo | PDF | Edge ou Chrome |
| PDF para JPG ou PNG | Lista | Imagem ou ZIP | Não |
| PDF para PDF/A | Lista | PDF/A-1b | Não |
| PDF para Word | Lista | `.docx` | LibreOffice |
| PDF para Excel | Lista | `.xlsx` | Não |
| PDF para PowerPoint | Lista | `.pptx` | PowerPoint |
| Girar todas as páginas | Lista | Lista alterada na tela | Não |
| Inserir números de página | Lista | PDF | Não |
| Inserir marca d'água | Lista | PDF | Não |
| Recortar PDF | Lista | PDF | Não |
| Preencher formulário | Arquivo | PDF preenchido | Não |
| Tarjar dados | Lista | PDF | Não |
| Proteger com senha | Lista | PDF protegido | Não |

---

# Base

## Adicionar arquivos

**Onde age:** ao abrir. Botão "Adicionar arquivos" ou arrastar e soltar na janela (também aceita arquivos passados ao abrir o programa).

**Como funciona**
1. Cada arquivo é identificado pelo **conteúdo**: PDF (`%PDF-`), JPEG, PNG, GIF, BMP ou TIFF. A extensão é ignorada. Qualquer outra coisa é recusada com mensagem clara.
2. PDFs são abertos e cada página vira um item da lista. Imagens viram um item cada.
3. Um PDF **protegido por senha** não é recusado: o aplicativo pergunta a senha (veja "Senha ao abrir").
4. PDFs com **assinatura digital** são marcados; ao salvar, o aplicativo avisa que o PDF novo não mantém as assinaturas.
5. As miniaturas são geradas em segundo plano, só para as páginas visíveis.

**Não faz:** não copia nem altera o arquivo; não grava nada em disco.

**Limites:** um arquivo com defeito aparece numa lista de falhas ("Alguns arquivos não foram adicionados"), e os demais entram normalmente.

## Organizar (a grade de páginas)

**Onde age:** lista de páginas, só na memória.

**Como funciona**
- **Girar:** botões em cada cartão (ao passar o mouse) e o menu Ferramentas.
- **Excluir:** botão no cartão ou tecla Delete.
- **Reordenar:** arrastar um cartão (ou vários selecionados). Uma linha mostra onde vai cair, e a grade rola sozinha perto das bordas.
- **Selecionar:** clique, Ctrl+clique (alterna), Shift+clique (intervalo), Ctrl+A (todas), Esc (limpa).
- **Desfazer:** Ctrl+Z, até 30 passos.
- **Modo de exibição:** "por página" (uma miniatura cada) ou "por arquivo" (um cartão por arquivo).
- **Pré-visualização:** duplo clique abre a página em tamanho grande (110 dpi), com zoom (+, −, 0), ajuste e setas para navegar.

**Não faz:** nada disso toca nos arquivos. Só o que você salva depois é gravado.

## Salvar PDF

**Onde age:** lista de páginas.

**Como funciona**
1. Se algum PDF da lista é assinado, pede confirmação ("o PDF gerado não mantém as assinaturas").
2. Sugere o nome do primeiro arquivo da lista mais `_editado` (um arquivo) ou `_unido` (vários), e você escolhe o destino.
3. Monta o PDF importando as páginas na ordem da lista, aplica as rotações, e grava num temporário que é trocado de uma vez só pelo destino.
4. Mostra o progresso e permite cancelar.

**Não faz:** nunca sobrescreve um dos arquivos de origem (recusa com mensagem). O PDF novo não herda título nem autor dos originais e perde as miniaturas embutidas (que podem guardar a prévia de um conteúdo antigo).

**Imagens:** JPEG e PNG entram **sem recompressão**. Com "Imagens em: A4" a imagem é encaixada mantendo a proporção, com margem de 10 mm, e a página fica em retrato ou paisagem conforme a imagem. Com "Original" a página tem o tamanho da imagem. A orientação EXIF das fotos é corrigida.

**Classes:** `DocumentComposer`, `ImageToPageService`, `StripThumbnailsStep`.

## Senha ao abrir

**Onde age:** ao abrir.

**Como funciona:** quando um PDF pede senha, o aplicativo mostra uma caixa com o nome do arquivo. Senha errada repete a pergunta; cancelar pula o arquivo. As senhas de vários arquivos são pedidas em fila.

**Atenção:** o PDF gerado a partir de um arquivo aberto com senha **não tem senha**. A própria caixa avisa isso. Use "Proteger com senha" para proteger o resultado.

**Segurança:** a senha não vai para log nem configuração, e a cópia na memória é zerada depois do uso.

---

# Otimizar

## Comprimir PDF

**Onde age:** lista de páginas. É um "Salvar" que também reduz as imagens.

**Como funciona**
1. Você escolhe um nível:

   | Nível | Resolução máxima | Qualidade JPEG |
   |---|---|---|
   | Leve | 200 dpi | 80 |
   | Média (recomendada) | 150 dpi | 65 |
   | Forte | 100 dpi | 50 |

2. Antes de abrir o diálogo, o aplicativo calcula quanto do tamanho é imagem. Se for **menos de 30%**, avisa que a redução será pequena (compressão só age em imagens).
3. Para cada imagem, **mede a resolução real** onde ela é desenhada na página (lê o conteúdo acompanhando `q`, `Q` e `cm`). A mesma foto dá 239 ppi numa página cheia e 479 ppi em meia página.
4. Só reduz a imagem que estiver mais de **15% acima** do alvo. Abaixo disso a perda de nitidez não compensa.
5. A imagem nova (JPEG) só substitui a original se ficar **pelo menos 5% menor**, e nunca é ampliada. Imagens em cinza continuam em cinza.
6. Mostra "de X para Y (redução de N%)" ao terminar.

**Fica como está:** imagens com transparência ou máscara, de 1 bit, CMYK, menores que 16 px, imagens que não puderem ser lidas ou medidas, e imagens dentro de blocos de formulário.

**Não faz:** não mexe em texto nem em fontes. A cópia dos recursos é privada, então o PDF aberto na tela não é alterado nem fica "mais comprimido" num segundo salvamento.

**Limites:** a perda de qualidade é permanente no arquivo gerado. PDF só de texto quase não reduz.

**Classes:** `PdfCompressor`, `CompressStep`, `ImageResolutionFinder`, `ImageScaler`, `ImageShare`, `EditableResources`.

## Reparar PDF

**Onde age:** arquivo que você escolhe (não usa a lista).

**Como funciona**
1. Aceita arquivos que tenham `%PDF-` nos primeiros 1024 bytes (tolera lixo antes do cabeçalho).
2. Abre o PDF de forma tolerante: se a tabela de referências estiver quebrada ou o final cortado, o PDFBox reconstrói a estrutura lendo os objetos.
3. Copia cada página legível para um documento novo e grava uma cópia saudável.
4. Renderiza cada página em baixa resolução só para contar as que podem não aparecer bem.
5. Mostra um relatório: páginas recuperadas de quantas, páginas perdidas, páginas com possível problema e se havia assinatura digital (que não é mantida).

**Não faz:** não recupera conteúdo realmente destruído, e as páginas ilegíveis são contadas como perdidas, nunca escondidas. Não altera o original.

**Senha:** se o arquivo for protegido, pergunta a senha.

**Classes:** `PdfRepairer`, `PdfLoader`.

---

# Converter em PDF

## Imagens para PDF

**Onde age:** é o "Adicionar arquivos" (imagens viram páginas) e depois "Salvar PDF". Aceita JPEG, PNG, GIF, BMP e TIFF. Veja "Salvar PDF" para tamanho de página e orientação.

## Word, Excel e PowerPoint para PDF

**Onde age:** arquivo que você escolhe (um por vez).

**Como funciona**
1. **Valida pelo conteúdo.** `.docx`, `.xlsx`, `.pptx` são abertos como ZIP só para conferir as partes internas (nada é extraído). `.doc`, `.xls`, `.ppt` precisam da assinatura do formato antigo; `.rtf` precisa de `{\rtf`. Arquivos com **macros** (`vbaProject.bin`) são recusados. Limite de 200 MB.
2. **Escolhe o programa:**
   - Se o Office correspondente está instalado e **não está aberto**, usa o Office.
   - Se não, usa o **LibreOffice**, se existir.
   - Se o Office está aberto e não há LibreOffice, pede para fechar o programa.
3. **Com o Office:** um script de PowerShell (embutido no aplicativo) abre o arquivo **somente leitura**, com macros desligadas, sem atualizar vínculos externos e com uma senha falsa (assim um arquivo protegido falha com mensagem em vez de travar esperando alguém digitar). Exporta para PDF e fecha o programa, mas só se ele não tiver outros documentos abertos.
4. **Com o LibreOffice:** converte com um perfil próprio, com macros desligadas, e com o tráfego de rede desviado (veja abaixo).
5. Confere que o resultado é um PDF e o grava com troca atômica.

**Tempo limite e cancelamento:** 5 minutos ou o botão Cancelar. Em ambos os casos o aplicativo encerra o processo e, se sobrou um Office **sem janela** iniciado por ele, também o encerra. Programas com janela do usuário nunca são encerrados.

**Por que "programa aberto não é automatizado":** o Excel e o PowerPoint têm uma única instância. Conectar-se a ela poderia mexer em, ou fechar, o trabalho que você está fazendo.

**Limites:** os próprios programas do Office podem ter comunicação de rede própria (conteúdo vinculado, serviços conectados); isso está fora do controle do aplicativo.

**Classes:** `OfficeConverter`, `OfficeFileDetector`, `OfficeRunner`, `LibreOfficeRunner`, `LibreOfficeProfile`, `SystemProbe`, `ExternalProcess`.

## HTML para PDF

**Onde age:** arquivo `.html` ou `.htm` que você escolhe.

**Como funciona**
1. Aceita só arquivo de texto (sem bytes nulos) de até 20 MB.
2. Faz uma **cópia temporária** do HTML e insere nela uma política de conteúdo que **bloqueia scripts** (`script-src 'none'`) e um `<base>` apontando para a pasta original (assim imagens e estilos relativos continuam funcionando). A inserção é feita nos bytes, sem decodificar, para não corromper acentos.
3. Imprime a cópia em PDF com o Edge (ou o Chrome) sem janela, com um perfil temporário próprio (não interfere no seu navegador).
4. O navegador roda com **DNS bloqueado** e **proxy morto**, inclusive para endereços locais. Imagens ou estilos remotos não são carregados.

**Não faz:** não executa JavaScript e não acessa a internet. Um teste automático confirma que o navegador não faz nenhuma conexão.

**Limites:** o Edge tenta falar com a rede dele por conta própria (sincronização, atualização de componentes); isso também é bloqueado pelo proxy. Páginas que dependem de JavaScript ou de recursos remotos saem incompletas.

**Classes:** `HtmlConverter`, `HtmlPolicy`.

---

# Converter de PDF

## PDF para JPG ou PNG

**Onde age:** lista de páginas.

**Como funciona**
1. Você escolhe o formato (JPG, menor e mais indicado para documentos; ou PNG, sem perda) e a resolução (100, 150, 200 ou 300 dpi).
2. O aplicativo monta as páginas (com rotações e exclusões) e renderiza cada uma.
3. **Uma página** gera um arquivo de imagem. **Várias páginas** geram um ZIP com `pagina-001.jpg`, `pagina-002.jpg`... (nomes gerados pelo aplicativo, nunca o nome do seu documento).
4. Em páginas muito grandes a resolução é reduzida automaticamente para não faltar memória.

**Classes:** `PageExporter`, `PageRasterizer`.

## PDF para PDF/A

**Onde age:** lista de páginas.

**Como funciona:** gera **PDF/A-1b**, o formato de arquivamento de longo prazo.
1. Pede confirmação, porque **cada página vira imagem** (200 dpi). O texto deixa de ser selecionável e o arquivo costuma crescer.
2. Acrescenta o perfil de cor sRGB (output intent) e os metadados XMP que declaram PDF/A-1b.
3. Grava com a estrutura clássica do PDF (sem fluxos de referência cruzada), que o PDF/A-1 exige.

**Por que assim:** converter um PDF qualquer para PDF/A exige fontes embutidas, perfis de cor e metadados consistentes; o PDFBox não faz essa conversão. Com a página em imagem não há fonte para violar a norma.

**Verificação:** os testes validam o resultado com o validador oficial do Apache (`preflight`) e incluem um PDF comum que precisa **falhar**, para provar que o teste é real.

**Classes:** `PdfAStep`, `PageRasterizer`.

## PDF para Word

**Onde age:** lista de páginas.

**Como funciona:** monta um PDF temporário com as páginas da lista e o converte para `.docx` pelo **LibreOffice** (filtro de importação de PDF do Writer). Valida que o resultado é um Word válido e o grava.

**Limites:** o resultado é editável, mas o **layout fica aproximado** (o texto costuma vir em molduras). PDF escaneado vira imagem.

**Decisão pendente:** o próprio Word abre PDF, com fidelidade melhor, mas mostra um aviso de conversão que, em segundo plano, ninguém consegue responder e a conversão trava. Silenciar esse aviso exige alterar uma opção do Word no registro do usuário. O aplicativo **não faz** isso sem autorização.

**Classes:** `PdfToOffice`, `LibreOfficeRunner`.

## PDF para Excel

**Onde age:** lista de páginas. Não usa programa externo.

**Como funciona**
1. Monta um PDF temporário com as páginas da lista e lê a **posição de cada letra**.
2. Agrupa as letras em linhas e as linhas em **blocos** (um espaço largo separa dois blocos).
3. Detecta tabelas pelo layout (algoritmo inspirado no projeto PdfCraft):
   - **colunas** são bordas esquerdas (ou, para números alinhados à direita, bordas direitas) compartilhadas por pelo menos dois blocos curtos, com tolerância de 4 pt;
   - um bloco só é "de célula" se for baixo (até 5 vezes o tamanho da letra), então **parágrafos nunca viram tabela**;
   - **linhas** são blocos com mais de 50% de sobreposição vertical;
   - uma **tabela** é uma sequência de linhas com 2 ou mais colunas, aceita com 3 ou mais colunas (ou 2 colunas com 3 linhas), com limite de 100 colunas;
   - uma faixa de cabeçalho ou de total logo antes ou depois, que atravessa colunas, entra na tabela.
4. O texto fora das tabelas entra como linhas de uma célula, na ordem da página.
5. Grava um `.xlsx` (ZIP de XML, sem biblioteca), **uma aba por página**.

**Números e segurança dos dados**
- **Só vira número o que é inequívoco:** `1.234,56`, `12,5`, `0,5` e inteiros de até 6 dígitos sem zero à esquerda. CPF, telefone, matrícula, `00123`, datas e `1.234` (ambíguo) ficam como **texto**.
- **Nada vira fórmula:** um valor `=1+1` ou `+cmd|...` no PDF é gravado como texto.
- Caracteres inválidos em XML são removidos, e células são limitadas a 32.767 caracteres.

**Limites:** é uma **heurística**. Funciona bem em relatórios, extratos e listagens de sistema. Tabelas sem espaço claro entre colunas, com várias linhas por célula ou muito irregulares podem sair imperfeitas, e células mescladas não são recriadas. **PDF escaneado** não tem texto: o aplicativo avisa e não gera planilha (OCR foi dispensado). Confira o resultado.

**Classes:** `PdfToOffice.toExcel`, `TableExtractor`, `XlsxWriter`, `CellValues`.

## PDF para PowerPoint

**Onde age:** lista de páginas. Exige o Microsoft PowerPoint instalado e **fechado** (não há alternativa com LibreOffice).

**Como funciona**
1. Exporta cada página como PNG (150 dpi) para uma pasta temporária (a extração do ZIP aceita só os nomes `pagina-NNN.png` gerados pelo aplicativo, então um ZIP malicioso não escapa da pasta).
2. Um script do PowerShell cria a apresentação: um slide em branco por página, com a imagem centralizada. Os slides têm 540 pontos de altura e a largura segue a proporção da primeira página.
3. Valida que o resultado é um PowerPoint válido e o grava.

**Limites:** os slides são **imagens**, não texto editável.

**Classes:** `PdfToOffice.toPowerPoint`, `OfficeRunner`, `PageExporter`.

---

# Editar

## Girar todas as páginas

**Onde age:** lista de páginas, na tela (itens "à direita" e "à esquerda" do menu). Gira 90° todas as páginas da lista e pode ser desfeito com Ctrl+Z. A rotação só vai para o arquivo quando você salva.

## Inserir números de página

**Onde age:** lista de páginas.

**Como funciona**
1. Você escolhe a **posição** (rodapé ou cabeçalho; esquerda, centro ou direita), o **formato** (`1`, `Página 1`, `Página 1 de 10`, `1 / 10`), o **número inicial**, o **tamanho da letra** e se a **primeira página fica sem número** (a primeira página continua contando).
2. Em cada página o aplicativo calcula o canto **como você vê a página**, mesmo se ela estiver girada ou tiver área de corte, e escreve o número em cinza escuro (Helvetica), a 28 pt da borda.
3. O "total" é o último número (início + páginas − 1).

**Não faz:** não altera o texto original da página (escreve por cima). Só aceita letras que a fonte padrão sabe escrever (acentos funcionam).

**Classes:** `PageNumbersStep`, `PageGeometry`, `EditableResources`.

## Inserir marca d'água

**Onde age:** lista de páginas.

**Como funciona**
1. Você escolhe **texto** (até 40 caracteres; padrão "CONFIDENCIAL") ou **imagem** (JPEG ou PNG, validada pelo conteúdo), a **opacidade** (5% a 100%, padrão 30%) e se vai **na diagonal** (45°).
2. No texto, o tamanho da letra é calculado para ocupar cerca de 70% da diagonal (ou da largura); na imagem, ocupa até 50% da página. Fica **centralizada** na página como você a vê e sempre **por cima** do conteúdo.
3. A transparência é aplicada com um estado gráfico do PDF.

**Classes:** `WatermarkStep`, `PageGeometry`.

## Recortar PDF

**Onde age:** lista de páginas. A **mesma área** (em frações da página) vale para todas as páginas.

**Como funciona**
1. Você arrasta sobre a página de referência para marcar a área que será **mantida**.
2. Escolhe um modo:
   - **Remover o conteúdo de fora (recomendado):** cada página é renderizada (200 dpi), cortada na área e substituída por uma imagem. **O que ficou de fora deixa de existir no arquivo**, mas o texto deixa de ser selecionável.
   - **Apenas ocultar:** só define a área visível da página. O texto continua selecionável, mas **o conteúdo cortado continua dentro do arquivo e pode ser recuperado**. Não use em documentos com dados sensíveis.

**Classes:** `CropStep`, `PageRasterizer`, `RegionPickerPane`.

## Preencher formulário

**Onde age:** arquivo que você escolhe (um PDF com campos de formulário, não usa a lista).

**Como funciona**
1. O aplicativo lê os campos: **texto** (inclusive várias linhas, com o limite de caracteres do campo), **caixa de seleção**, **opção única** e **lista suspensa**.
2. Mostra um formulário com um controle para cada campo. "Salvar..." só habilita depois de uma mudança (ou de marcar "tornar os campos não editáveis").
3. Só os valores que você **mudou** são gravados. Os valores de opção e de lista são conferidos pelo aplicativo (o PDFBox não confere).
4. Grava uma **cópia**. Com **achatar**, os valores viram parte da página e **não sobra nenhum campo editável** no arquivo.

**Fica de fora:** campos somente leitura, de senha, botões, assinaturas e listas de seleção múltipla. Formulários dinâmicos (**XFA**) são recusados com mensagem clara (o PDFBox não os preenche).

**Limites:** até 500 campos e 2.000 caracteres por valor. Se o arquivo tinha assinatura digital, ela não é mantida (o aplicativo avisa antes). Texto fora do alfabeto da fonte do campo é recusado.

**Classes:** `FormFiller`, `FormDialog`.

---

# Segurança

## Tarjar dados

**Onde age:** lista de páginas. Pensado para dados sensíveis (LGPD): CPF, nomes, endereços.

**Como funciona**
1. Você navega pelas páginas e **arrasta retângulos** sobre o que quer ocultar. Pode marcar várias áreas por página, desfazer a última ou limpar a página. O contador mostra "Áreas marcadas: N, em M página(s)".
2. Ao salvar, **cada página marcada é renderizada (200 dpi), as áreas são pintadas de preto nos próprios pixels e a página é substituída por essa imagem**.
3. As páginas sem marca **não mudam** e continuam com texto selecionável.

**Por que é seguro:** cobrir um PDF com um retângulo preto deixa o texto por baixo, que ainda pode ser copiado. Aqui **não existe mais texto** na página tarjada (um teste confirma que o texto nem aparece nos bytes do arquivo).

**Falha alta:** se uma marca apontar para uma página que não existe, o salvamento **falha** em vez de pular a tarja.

**Limites:** o texto das páginas tarjadas deixa de ser selecionável, e o arquivo cresce. Confira o resultado antes de compartilhar.

**Classes:** `RedactStep`, `RedactionArea`, `PageRasterizer`, `AreasPickerPane`.

## Proteger com senha

**Onde age:** lista de páginas. É um "Salvar" que criptografa.

**Como funciona**
1. Você digita a senha **duas vezes** (de 8 a 64 caracteres). O botão só habilita quando as duas coincidem.
2. O PDF é criptografado com **AES de 256 bits**. A senha de proprietário (que controla as permissões) é **aleatória** (32 caracteres, `SecureRandom`), nunca igual à de abrir. As permissões de modificar, anotar e reorganizar páginas ficam desligadas.
3. A cópia da senha na memória é zerada depois do uso.

**Atenção:** sem a senha o arquivo **não pode ser aberto nem recuperado**, e o aplicativo não a guarda. A senha nunca vai para log nem configuração.

**Classes:** `ProtectStep`, `ProtectDialog`.
