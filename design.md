# Plano de Interface Mobile — KmCerto

Este documento registra o **plano de interface** do aplicativo **KmCerto** em orientação **mobile portrait (9:16)**, com uso prioritário com **uma mão**, alinhado ao PDF enviado pelo usuário. O produto não é um app de marketplace ou mapa tradicional. O núcleo da experiência é um **overlay flutuante automático no Android**, acionado a partir de um **Serviço de Acessibilidade**, com apoio de três superfícies principais em React Native: **Tela Inicial**, **Modo Manual** e **Configurações**.

A proposta visual precisa manter leitura extremamente rápida em contexto de rua. Por isso, a hierarquia visual deve privilegiar **números grandes**, **estado decisório imediato** e **ações com toque amplo**. O padrão de comportamento se inspira na velocidade operacional percebida em apps como 99, Uber e iFood, mas a implementação deve seguir o escopo do PDF, sem inventar novas telas fora da especificação.

## Screen List

| Tela ou superfície | Objetivo principal | Tipo |
|---|---|---|
| Tela Inicial | Orientar ativação de permissões, mostrar status atual e abrir as demais áreas do app | React Native |
| Modo Manual | Permitir digitação manual de valor, km e minutos quando a leitura automática falhar | React Native |
| Configurações | Definir e salvar o valor mínimo aceitável por quilômetro | React Native |
| Overlay Flutuante Automático | Exibir decisão rápida com base na leitura da tela de iFood, 99Food e Uber | Android nativo |
| Serviço de Acessibilidade | Monitorar apps suportados, extrair dados e acionar o overlay | Android nativo |

## Primary Content and Functionality

A **Tela Inicial** deve funcionar como um painel de entrada simples e objetivo. O conteúdo principal precisa apresentar um **checklist de permissões** com indicação visual clara para dois pontos obrigatórios: permissão de overlay e serviço de acessibilidade. Cada item deve exibir se está ativo ou pendente, com atualização visual imediata. Na metade inferior da tela devem existir botões grandes para abrir a permissão de overlay, abrir a tela de acessibilidade do Android, acessar o modo manual e acessar as configurações.

O **Modo Manual** deve ser uma tela de cálculo rápido. O usuário informa **Valor (R$)**, **Km** e **Minutos** em campos de entrada de fácil toque. Ao processar os dados, a interface deve mostrar o resultado com a mesma lógica do overlay automático: **R$/km**, **R$/hora**, **R$/minuto** e **status de ACEITAR ou RECUSAR**. O PDF também pede a visualização de **acumulado do dia**, composto por total ganho e média por hora, então essa área deve aparecer abaixo dos resultados como um bloco resumido.

A **Tela de Configurações** deve conter um fluxo mínimo e estável. Ela precisa ter um campo para **Valor Mínimo por Km**, iniciado com **R$ 1,50** como padrão, além de um botão para persistência local da configuração. Como se trata de uma preferência simples e não sensível, o armazenamento local pode seguir uma estratégia compatível com a plataforma, com fallback adequado fora do Android/iOS nativo.

O **Overlay Flutuante Automático** é o coração visual do produto. Ele deve aparecer sobre outros aplicativos suportados quando houver dados suficientes para cálculo. O conteúdo exibido precisa incluir **Valor Total da Corrida**, **Status ACEITAR ou RECUSAR**, **Valor por KM**, **Ganho por Hora** e **Ganho por Minuto** quando houver tempo detectado. Caso o tempo não seja encontrado na tela do app monitorado, os campos de hora e minuto devem ser ocultados ou apresentados como indisponíveis, conforme a regra do PDF.

O **Serviço de Acessibilidade** deve operar em segundo plano para observar os aplicativos-alvo, capturar o texto da janela ativa, aplicar expressões regulares e encaminhar os dados processados ao overlay. Esse fluxo não é uma tela propriamente dita, mas define a espinha dorsal funcional do app e precisa ser considerado desde o desenho da navegação e da arquitetura.

## Overlay Layout Specification

O overlay deve parecer técnico, moderno e muito legível. O fundo deve ser **escuro semi-transparente** para não competir demais com o app monitorado, enquanto o conteúdo crítico precisa ter grande contraste. A composição sugerida pelo próprio PDF é um cartão compacto, com leitura vertical imediata.

| Elemento | Conteúdo | Tratamento visual |
|---|---|---|
| Cabeçalho | Valor total da corrida, como `R$ 93,58` | Texto branco, grande, em negrito, centralizado ou com forte destaque superior |
| Faixa de status | `ACEITAR` ou `RECUSAR` | Fundo verde para aceitar e vermelho para recusar, com texto branco em negrito |
| Linha de métricas | `R$/km`, `R$/hr`, `R$/min` | Valores destacados com separadores visuais, exibidos condicionalmente |
| Temporização | Auto-remoção em 8 segundos | Comportamento nativo, sem exigir ação do usuário |

## Key User Flows

| Fluxo | Passos |
|---|---|
| Preparação inicial | Usuário abre o app → verifica checklist → ativa permissão de overlay → ativa acessibilidade → retorna ao app e confirma status visual |
| Leitura automática bem-sucedida | Serviço monitora iFood, 99Food ou Uber → extrai valor e km → calcula métricas → compara com mínimo por km → exibe overlay com ACEITAR ou RECUSAR → remove overlay após 8 segundos |
| Leitura automática sem sucesso | Usuário abre o Modo Manual → digita valor, km e minutos → processa dados → vê R$/km, R$/hora, R$/min e status |
| Configuração de critério | Usuário entra em Configurações → altera valor mínimo por km → salva preferência → overlay e modo manual passam a usar o novo limite |

## Color Choices

A paleta deve comunicar urgência operacional e leitura rápida em ambiente externo. O PDF define diretamente o uso de **fundo escuro semi-transparente** e **texto branco**, com destaque semântico por cor para decisão. A paleta do app em React Native deve derivar disso para manter consistência entre telas e overlay.

| Função | Cor | Hex |
|---|---|---|
| Fundo principal do app | Preto grafite | `#101114` |
| Superfície elevada | Cinza carvão | `#1D2026` |
| Overlay nativo | Preto semi-transparente | `#CC000000` |
| Texto principal | Branco | `#FFFFFF` |
| Texto secundário | Cinza claro | `#CFCFD4` |
| Estado aceitar | Verde forte | `#16A34A` |
| Estado recusar | Vermelho forte | `#DC2626` |
| Destaque neutro | Amarelo técnico | `#F5D400` |

## Layout and Interaction Guidelines

A experiência precisa transmitir a sensação de utilitário profissional, e não de app social ou catálogo. As telas React Native devem ter **estrutura enxuta**, **blocos com cantos arredondados**, **tipografia objetiva** e **ações grandes na metade inferior**. O usuário provavelmente interagirá sob pressão, em deslocamento ou parado por pouco tempo, então toda ação crítica deve ser identificável em um único olhar.

A **Tela Inicial** deve priorizar confirmação de capacidade operacional do sistema. O **Modo Manual** precisa favorecer digitação rápida e comparação instantânea de rentabilidade. A **Tela de Configurações** deve ser curta, quase utilitária. Já o **overlay** deve ser ainda mais compacto, com alta densidade de informação, mas sem poluição visual.

## Arquitetura de Navegação

A navegação principal pode permanecer simples, com rotas dedicadas para **Home**, **Modo Manual** e **Configurações**. O overlay não depende da navegação do app, pois é controlado pelo módulo Android nativo. Portanto, a arquitetura da interface deve separar claramente o que é navegação React Native e o que é comportamento nativo em background.

## Observação de aderência

Este documento foi corrigido para refletir o PDF como fonte principal de verdade. Caso algum detalhe visual das referências enviadas por imagem conflite com a especificação textual do PDF, a implementação deve seguir **o PDF sem alterar nada do escopo solicitado**.
