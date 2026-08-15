# O que mudou face ao jogo original

Isto é um fork privado do [patsonluk/airline](https://github.com/patsonluk/airline),
a partir do commit `77647f1`, preparado para correr em casa para cinco amigos.

O original foi escrito para um servidor público com milhares de jogadores. Quase
tudo o que está aqui em baixo vem daí: preços, esperas e requisitos que fazem
sentido com mil pessoas e um ano de jogo, mas que a cinco pessoas numa noite ou
não se notam ou tornam-se uma parede.

Cada coisa ajustável vive num sítio só — o ficheiro **`game-settings.env`** — e
cada uma delas tem lá escrito o que faz, quanto era no original e quanto está
agora.

---

## 1. Coisas que estavam partidas e foram arranjadas

Não são opções: era código que não funcionava, ou que só não funciona num
servidor pequeno.

- **O jogo parou de avançar durante uma noite inteira.** O motivo real: o jogo
  guarda dinheiro em colunas de texto e escrevia `360000.0` lá dentro; os
  drivers modernos recusam-se a ler isso. A simulação rebentava a meio de cada
  ciclo. E não havia registo nenhum — a simulação não tinha onde escrever os
  erros, por isso durante um dia inteiro não havia uma única linha a dizer o que
  se passava. Ficaram os dois arranjados: o registo primeiro, o erro depois.
- **A ligação à página morria de dois em dois segundos.** Uma consulta passava um
  tipo do Scala que o driver não aceita, o que matava o ator do websocket. O
  resultado eram gráficos a piscar, o relógio aos saltos, o dinheiro a aparecer
  a amarelo sem ter mudado, e o botão de receber o prémio dos Olímpicos a não
  fazer nada. Corrigido em 76 sítios.
- **Elasticsearch.** O original precisa de um motor de busca à parte só para as
  caixas de pesquisa e para o registo de contas. Sem ele, não dava sequer para
  criar conta. Foi removido: as buscas passaram a usar o índice que o próprio
  jogo já tem.
- **Driver da base de dados.** Passou a falar com o MariaDB com o driver do
  MariaDB.
- **Uma linha estragada na tabela de alianças deitava abaixo a página inteira**
  das alianças. Um painel que falha já não leva os outros atrás.
- **A ligação em tempo real não se reconectava** depois de uma quebra — a página
  ficava surda até se recarregar.
- **Fotografias de cidades**: passaram a vir da Wikipédia, e uma busca falhada é
  guardada como falhada em vez de ser repetida para sempre.
- Várias outras: registo de contas, formar aliança, o painel de anúncios, o
  logótipo da companhia (redimensiona em vez de recusar), a chamada ao Vimeo na
  página inicial, o mapa a tapar os menus.

---

## 2. Ritmo — o jogo anda mais depressa

| | Original | Aqui |
|---|---|---|
| Uma semana de jogo | 30 minutos | **3 minutos** |
| Um ano de jogo | ~26 horas | **~2,6 horas** |

E ainda:

- **Botão "Forçar semana"** para correr a semana seguinte já.
- **Modo férias** (desligado): se ninguém jogar durante meia hora, o mundo passa
  a andar a um quarto da velocidade, para que quem falta dois dias não volte a
  um mundo que andou meses sem ele. Volta ao normal assim que alguém entra.
- **Vigia automático**: se o número da semana não mexer durante dois ciclos, a
  simulação é reiniciada sozinha e fica um aviso dentro do jogo a dizer que isso
  aconteceu.
- **A simulação vigia-se a si própria**: uma semana que rebente já não leva as
  seguintes atrás, e se nada acontecer durante duas semanas ela própria força
  uma.

---

## 3. Regras afinadas

Tudo isto está em `game-settings.env` e pode voltar atrás a qualquer momento.

| Regra | Original | Aqui | Porquê |
|---|---|---|---|
| Comprar rotas | negociação com delegados, dados a rolar, semanas de espera | **compra direta, sem custo extra** | a negociação é uma espera, não uma decisão, quando são cinco pessoas |
| Delegados | tratam das negociações | **cada um tira 10% ao preço da rota, até 50%** | ficaram sem trabalho quando as rotas passaram a comprar-se |
| Espera do delegado | 12 semanas | **3** | |
| Voltar a mexer numa rota | 6 semanas | **2** | meia hora de espera real para tocar numa rota acabada de abrir |
| Níveis dos delegados | 4 semanas / 1 ano / 3 anos / 10 anos | **4x mais rápido** | ninguém chega a dez anos de jogo |
| Construir e manter bases | 100% | **50%** | o preço multiplica por 1,7 por nível: ao nível 10 uma base custa 40x o que custa ao nível 3 |
| Pessoal de escritório por base | 100% | **200%** | isto é o que verdadeiramente limita quantas rotas se podem ter |
| Especializações de base | precisam de nível 8 a 14 | **4 a 7** | ninguém num servidor pequeno chega ao 8 |
| Bens de aeroporto (hotéis, etc.) | 200 a 2000 milhões | **25% disso** | mecânica construída e inalcançável |
| Salas VIP | 50 milhões por nível | **40% disso** | idem |
| Desconto por avião impopular | até 70% | **20%** | os limiares são para centenas de aviões em circulação; a cinco pessoas todos os aviões estavam quase sempre com o desconto máximo, e comprar um dava lucro |
| Aliança | mínimo 3 companhias, com aprovação | **2, entra logo** | 3 em 5 é mais de metade de toda a gente; e uma aliança sem líder ligado nunca aceitava ninguém |
| Mudar o nome da companhia | 30 dias de espera | **sem espera** | |
| Logótipo e pintura | precisa de 40 de reputação | **desde o início** | |
| Funcionalidades de apoio (Patreon) | bloqueadas | **abertas** | não há a quem doar num servidor privado |
| Palavra-passe mínima | 6 | **4** | |
| Velocidade dos aviões | o ganho de altitude tem limites fixos | **proporcional ao avião** | abaixo de 700 km todos os aviões acima de 700 km/h demoravam exatamente o mesmo, e pagar por velocidade não comprava nada |

---

## 4. Mecânicas novas

Coisas que o original não tem de todo.

### Estações do ano
O jogo tem um calendário e ignorava-o: a procura em janeiro era a de agosto. Com
isto, as férias seguem o verão — que é um mês diferente em cada hemisfério — e os
negócios caem quando os escritórios esvaziam, no Natal e em agosto. Medido, a 30:
Londres→Faro rende 0,75 em janeiro e 1,26 no fim de julho; Londres→Sydney faz o
contrário; Londres→Lisboa (negócios) cai para 0,82 no Natal.

### Acontecimentos no mundo
De vez em quando acontece qualquer coisa a que toda a gente tem de reagir: crise
de combustível, boom de turismo, recessão, ou problemas num aeroporto. Um de cada
vez, algumas semanas, anunciado a todos. Sem isto, a semana 300 é igual à semana
3 e as únicas notícias são o que vocês fazem uns aos outros.

### Negócios entre companhias
Um painel onde propões dinheiro e aviões, pedes dinheiro, e o outro aceita ou
recusa. Serve para vender um avião, pagar uma dívida combinada no chat, comprar
alguém para sair de uma rota, ou dar um presente. Antes disto, duas pessoas
podiam combinar o que quisessem e não havia forma nenhuma de mover nada.

### Alugar aviões e pagar a prestações
Comprar era a única maneira de ter um avião.

- **Alugar**: 10% de entrada, 18% do preço por ano, para sempre, e o avião nunca
  é teu. Devolves na semana em que deixas de precisar.
- **Prestações**: 25% de entrada, 104 pagamentos semanais a uma taxa 3 pontos
  abaixo do banco, e quando pagas a última o avião passa a ser teu.

Alugado, o avião chega **na hora** — é o avião de outra pessoa, já construído, e
tê-lo hoje é metade da razão de alugar. A prestações é um avião novo comprado a
crédito, por isso **demora a ser construído como uma compra normal** — senão
prestações seria simplesmente melhor do que comprar. Enquanto não
estiver pago não pode ser vendido, trocado, dado num negócio, nem conta como
património.

### Procura que reage ao preço
Todos os passageiros paravam exatamente no mesmo preço, o que fazia uma parede:
a 1,2x o preço sugerido voavam 4%, a 1,3x não voava ninguém. Agora cada grupo tem
o seu próprio limite e a procura desce aos poucos — cobrar caro passou a ser uma
escolha (menos gente, mais por bilhete) em vez de um erro.

### Dois tipos de avião na mesma rota
O original obriga a que uma rota seja voada por um único modelo. Aqui podem
misturar-se, com as verificações de alcance e de pista feitas modelo a modelo.

### Quadro de estreias
Uma lista curta de marcos — primeiro a ter 100 milhões, primeiro a chegar a X —
cada um conquistado uma vez e guardado com o nome de quem lá chegou primeiro. Um
servidor público tem um ranking mundial; cinco amigos não tinham nada em que ser
os primeiros.

### Incidentes
Coisas que correm mal a **uma** companhia de cada vez: uma avaria (metade das
vezes — pagas a reparação e o avião perde condição), uma inspeção (atrasos
durante algumas semanas) ou uma greve (cancelamentos). Toda a gente é avisada,
porque metade da graça é os outros verem.

Não é azar a calhar a quem calha: a probabilidade sai do **estado da frota**.
Com aviões novos é um terço, com aviões acabados é o dobro — a manutenção passa
a ser uma decisão em vez de uma conta. Medido em 200 semanas de 40 voos, com a
frota em bom estado: normalmente 1,3 atrasos pequenos e 0,2 cancelamentos por
semana; com uma inspeção 4,9 pequenos e 2,0 grandes; com uma greve 0,9
cancelamentos. Alguns por cento da receita da semana.

### Subsídios regionais
No jogo cada rota tem de se pagar a si própria, por isso os aeroportos pequenos
ficam vazios para sempre. Agora uma rota **entre dois aeroportos pequenos**
(tamanho ≤ 3), **curta** (≤ 1500 km) e que **dá prejuízo** recebe de volta
**metade do prejuízo da semana**, até 500.000.

Nunca torna a rota lucrativa por si só, e uma rota que dá lucro não recebe nada
— a ideia é tornar possível voar para o interior, não transformar isso num
negócio melhor do que voar para as capitais.

### Impostos por país
Uma rota passa a pagar imposto ao país **de onde parte** — que é sempre uma
base tua, porque uma rota não pode começar noutro sítio. A taxa segue o
rendimento do país: 15% num país médio, oito pontos para cada lado. Medido nos
dados reais do jogo: **Portugal 8,1%**, Alemanha 21,8%, Suíça e Noruega 23%,
Índia e Marrocos 7%.

Rota a rota, e só sobre lucro: uma rota que dá prejuízo não paga nada e não
serve para abater no lucro de outra. A taxa de cada país aparece no painel do
aeroporto, por baixo da abertura do mercado — dá para ver antes de construir a
base. Onde pões as bases passou a ser também uma questão de país.

### IVA dos aviões, recuperado contra o imposto
Uma empresa na Europa paga IVA num avião e depois recebe-o de volta, contra o
que deve sobre os lucros — é por isso que uma empresa que acabou de investir
passa uns tempos sem pagar imposto.

Comprar um avião cria um crédito de **23% do preço**. Alugar ou pagar a
prestações cria-o sobre a entrada e sobre **cada pagamento semanal**, tal como o
IVA de uma renda de leasing. Todas as semanas o imposto das rotas é pago
primeiro com esse crédito, e só o que sobrar é cobrado a sério. Fica um aviso no
registo quando o crédito é criado e outro quando acaba.

### Onde se vê tudo isto
- **Painel Pagamentos** (topo): o que sai por semana, **quanto falta pagar ao
  todo**, o IVA por recuperar, e cada avião a pagamento — quanto já pagaste, em
  quantas semanas, quanto falta e se está a voar.
- **Demonstração de resultados**: por baixo da *Despesa dos bens* aparecem agora
  três linhas — *dos quais pagamentos de aviões*, *dos quais imposto do país*,
  *dos quais carga*. Cada uma desaparece quando é zero, portanto quem tenha isto
  desligado vê o mapa de resultados exatamente como sempre foi.

  As colunas do mapa de resultados estão fixas na base de dados e acrescentar
  uma obrigaria a mexer na tabela de um mundo já a ser jogado — se essa
  alteração falhasse, cada semana passava a rebentar ao gravar as contas. Por
  isso os três números são guardados numa tabela à parte, semana a semana, e
  somados ao período que estiveres a ver (semana, mês ou ano).

### Carga
O porão de um avião voava vazio todas as semanas. Agora há **contratos**:
alguém oferece tantas toneladas por semana entre dois aeroportos, durante
tantas semanas, por um preço — e as ofertas só aparecem em rotas que já voas.

Aceitar é prometer espaço numa rota que passas a ter de manter a voar, e ser
pago todas as semanas aconteça o que acontecer aos passageiros. O porão é a
sério: 0,02 toneladas por lugar por voo, portanto um avião de 180 lugares a
voar 14 vezes leva 50 toneladas por semana, e um contrato pede entre um quarto
e dois terços disso. Uma semana sem espaço perde o pagamento dessa semana, três
cancelam o contrato, e desistir de propósito custa quatro semanas.

Painel novo no topo: **Carga**.

### Noite mais lenta
O servidor corre a noite toda para gente que está a dormir — e a 3 minutos por
semana isso são 160 semanas entre deitar e acordar. Das 00h às 10h uma semana
passa a demorar 5 minutos.

### Tecto de 30%
Nada do que foi acrescentado pode mexer na procura ou nos custos mais de 30%
para cada lado, **por muitas coisas que calhem na mesma semana**. Estações,
acontecimentos do mundo e o resto são multiplicados juntos e depois presos
dentro dessa banda, num sítio só. Um boom num país onde também é verão não pode
duplicar uma rota.

### Compensações
Um script para injetar dinheiro numa companhia à mão, quando algo corre mal por
culpa do servidor (o jogo guarda saldos em cache durante 10 minutos, por isso o
script também reinicia o site).

---

## 5. Interface

- **Mapa**: Google Maps trocado por OpenStreetMap (sem chave, sem conta, sem
  contas a pagar), centrado em Portugal, com tema claro/escuro a seguir o do
  jogo e nomes de sítios em inglês.
- **Aviões no mapa**: eram um ponto amarelo. Agora são aviões desenhados,
  virados para o rumo certo, com tamanho conforme o tipo (20 a 52 px), a voar
  sobre a linha curva que o mapa desenha e à velocidade do horário.
- **Rotas curvas**: as linhas passaram a ser arcos de círculo máximo, como as
  rotas reais, em vez de linhas retas.
- **Português**: mais de mil frases da interface traduzidas. É por jogador — um
  pode ler em português enquanto os outros ficam em inglês — e nomes de
  aeroportos, cidades e aviões ficam como estão.
- **Relógio**: o calendário no ecrã anda a metade da velocidade dos ciclos
  (`AIRLINE_GAME_TIME_SPEED=0.5`) e começa em 2026. Isto é só o relógio e os
  aviões desenhados contra ele — a simulação continua a fazer uma semana de voo
  por ciclo, e a pagar por semana.
- **Caixas de filtro** nas listas longas (companhias, países, alianças, modelos).
- **Cartão de voo** com as cores do tema, e o painel de negociação desaparece
  quando não há nada para negociar.
- **Avisos de atualização**: antes de o servidor reiniciar, aparece um aviso e as
  páginas recarregam-se sozinhas depois.
- Vídeos e clips de fundo desligados (eram chamadas para fora e não carregavam).

---

## 6. O servidor

Nada disto existe no original, que assume que alguém sabe o que está a fazer numa
consola.

- **`scripts/setup.sh`** — instala tudo de raiz numa máquina Ubuntu/WSL2 nova.
- **Serviços** a sério: o site e a simulação arrancam com a máquina, e reiniciam
  sozinhos se caírem.
- **Atualização automática** de 5 em 5 minutos, com aviso aos jogadores.
- **Cópias de segurança** diárias da base de dados, com as últimas 14 guardadas,
  e um botão de backup dentro do jogo.
- **Vigia** (`watchdog.sh`) que verifica se o número da semana ainda mexe.
- **Diagnóstico** (`update-status.sh`) que diz porque é que o jogo não se está a
  atualizar, sem mudar nada.
- **Relógio do jogo** (`game-time.sh`) para andar com a data para a frente ou
  para trás.
- **Acesso em casa**: atalhos para Windows que abrem o jogo ao resto da casa
  (`lan-access.bat`), e lançadores de Tailscale.
- **Segurança**: o acesso pelo Tailscale é só dentro da tua rede privada (`serve`,
  nunca `funnel`), a regra da firewall é só para o perfil privado, o reCAPTCHA
  está desligado (as chaves do original recusam qualquer outro domínio) e o botão
  de backup só corre um caminho fixo, sem nada que venha de um pedido.

---

## 7. Onde se mexe em tudo isto

```bash
nano ~/airline/game-settings.env      # tudo o que é ajustável, com explicações
./scripts/update.sh --no-pull         # aplica, avisa os jogadores, recarrega
./scripts/backup-db.sh                # antes de qualquer coisa arriscada
```

Documentos relacionados:

- **`SELF-HOSTING.md`** — instalação e manutenção
- **`WINDOWS-SETUP.md`** — WSL2, Tailscale, LAN
- **`MODERNISATION.md`** — o que foi feito à velocidade da simulação e porquê
