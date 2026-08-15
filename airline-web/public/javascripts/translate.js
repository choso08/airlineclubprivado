/*
 * Portuguese for the game's interface.
 *
 * The game was written in English throughout: text sits directly in the page,
 * in forty-odd scripts that build panels by hand, and in strings the server
 * sends down. There is no message catalogue to fill in - Play's i18n support
 * is present but nothing uses it, and wiring it up would mean touching every
 * literal in five and a half thousand lines of template while still missing
 * everything the scripts write at runtime.
 *
 * So this translates the finished page instead. It walks the text as the
 * browser has it, swaps whole phrases it recognises, and watches for the
 * panels the game builds later. That has two properties worth stating plainly:
 *
 *   - only exact, whole-string matches are replaced, so nothing that is not in
 *     the dictionary below can be damaged. An unknown phrase stays in English,
 *     which is a missing translation rather than a broken screen.
 *   - it is per-player. One person can read Portuguese while the others carry
 *     on in English; nothing on the server changes.
 *
 * Airport names, cities, countries, aircraft models and player-written text
 * are deliberately left alone: they are proper nouns, and a half-translated
 * world map reads worse than an English one.
 *
 * To add or correct a phrase, put it in the dictionary. The key must match the
 * English exactly, including capitals and any colon.
 */
(function (global) {
  'use strict';

  var COOKIE = 'gameLanguage';

  var DICTIONARIES = {

    pt: {
      // --------------------------------------------------------- navigation
      'Airline': 'Companhia',
      'Airlines': 'Companhias',
      'Airport': 'Aeroporto',
      'Airports': 'Aeroportos',
      'Airplane': 'Avião',
      'Airplanes': 'Aviões',
      'Alliance': 'Aliança',
      'Alerts': 'Avisos',
      'Announcements': 'Anúncios',
      'Assets': 'Bens',
      'Bank': 'Banco',
      'Bases': 'Bases',
      'Base': 'Base',
      'Campaigns': 'Campanhas',
      'Country': 'País',
      'Departures': 'Partidas',
      'Events': 'Eventos',
      'Fleet': 'Frota',
      'Flights': 'Voos',
      'Flight': 'Voo',
      'General': 'Geral',
      'Hangar': 'Hangar',
      'History': 'Histórico',
      'Log': 'Registo',
      'Logs': 'Registos',
      'Market': 'Mercado',
      'Members': 'Membros',
      'Office': 'Escritório',
      'Oil': 'Combustível',
      'Olympics': 'Olimpíadas',
      'Rankings': 'Classificações',
      'Ranking': 'Classificação',
      'Ranking:': 'Classificação:',
      'Research': 'Investigação',
      'Rivals': 'Rivais',
      'Routes': 'Rotas',
      'Search': 'Procurar',
      'Settings': 'Definições',
      'Survey': 'Inquérito',
      'Tickets': 'Bilhetes',
      'World Map': 'Mapa do mundo',
      'Flight Map': 'Mapa de voos',
      'Passenger Map': 'Mapa de passageiros',
      'Exit Passenger Map': 'Sair do mapa de passageiros',
      'Exit Airport Flight Map': 'Sair do mapa de voos do aeroporto',
      'Influence Heatmap': 'Mapa de influência',
      'Hide Heatmap Overlay': 'Esconder o mapa de influência',
      'Hide Inactive Airlines': 'Esconder companhias inativas',

      // ------------------------------------------------------------ actions
      'OK': 'OK',
      'Ok': 'OK',
      'Apply': 'Aplicar',
      'Cancel': 'Cancelar',
      'Confirm': 'Confirmar',
      'Confirmation': 'Confirmação',
      'Create': 'Criar',
      'Delete': 'Apagar',
      'Edit': 'Editar',
      'Remove': 'Remover',
      'Replace': 'Substituir',
      'Restore': 'Repor',
      'Revert': 'Reverter',
      'Send': 'Enviar',
      'Set': 'Definir',
      'Sign': 'Assinar',
      'Update': 'Atualizar',
      'Upgrade': 'Melhorar',
      'Downgrade': 'Reduzir',
      'Upload': 'Carregar',
      'Purchase': 'Comprar',
      'Sell': 'Vender',
      'Build': 'Construir',
      'Boost': 'Reforçar',
      'Negotiate': 'Negociar',
      'Vote': 'Votar',
      'Preview': 'Pré-visualizar',
      'Previous': 'Anterior',
      'Login': 'Entrar',
      'Logout': 'Sair',
      'Sign Up': 'Criar conta',
      'Plan Flight': 'Planear voo',
      'Purchase airplane': 'Comprar avião',
      'Build Base': 'Construir base',
      'Build Headquarters': 'Construir sede',
      'View Airport': 'Ver aeroporto',
      'Details': 'Detalhes',
      'Reset Tutorials': 'Repor os tutoriais',

      // ------------------------------------------------------------- money
      'Cash': 'Dinheiro',
      'Budget': 'Orçamento',
      'Price': 'Preço',
      'Price:': 'Preço:',
      'Pricing': 'Preços',
      'Profit': 'Lucro',
      'Profit:': 'Lucro:',
      'Revenue': 'Receita',
      'Revenue:': 'Receita:',
      'Income:': 'Rendimento:',
      'Value': 'Valor',
      'Value:': 'Valor:',
      'Total': 'Total',
      'Total:': 'Total:',
      'Total Income': 'Rendimento total',
      'Total Revenue:': 'Receita total:',
      'Total Expense:': 'Despesa total:',
      'Total Profit:': 'Lucro total:',
      'Income Sheet': 'Demonstração de resultados',
      'Cash Flow Sheet': 'Fluxo de caixa',
      'Cash Flow Details': 'Detalhe do fluxo de caixa',
      'Total Cash Flow': 'Fluxo de caixa total',
      'Total Cash Flow:': 'Fluxo de caixa total:',
      'Operation Cash Flow:': 'Fluxo de caixa das operações:',
      'Expense:': 'Despesa:',
      'Margin': 'Margem',
      'Impact': 'Impacto',
      'Flights Income': 'Rendimento dos voos',
      'Other Income': 'Outros rendimentos',
      'Flight Revenue:': 'Receita dos voos:',
      'Flight Expense:': 'Despesa dos voos:',
      'Flight Profit:': 'Lucro dos voos:',
      'Ticket Revenue:': 'Receita de bilhetes:',
      'Ticket Price History': 'Histórico do preço dos bilhetes',
      'Airport Fee:': 'Taxa de aeroporto:',
      'Airport Fees:': 'Taxas de aeroporto:',
      'Fuel Cost:': 'Custo do combustível:',
      'Fuel Profit:': 'Lucro do combustível:',
      'Fuel:': 'Combustível:',
      'Crew Cost:': 'Custo da tripulação:',
      'Crew:': 'Tripulação:',
      'Maintenance:': 'Manutenção:',
      'Airplane Maintenance:': 'Manutenção dos aviões:',
      'Airplane Depreciation:': 'Depreciação dos aviões:',
      'Depreciation:': 'Depreciação:',
      'Airplane Purchase:': 'Compra de aviões:',
      'Airplane Sold:': 'Aviões vendidos:',
      'Capital Gain/Loss:': 'Ganho/perda de capital:',
      'Service Investment:': 'Investimento em serviço:',
      'Service Supplies:': 'Consumíveis de serviço:',
      'Flight Supplies:': 'Consumíveis de voo:',
      'Lounge Cost:': 'Custo do lounge:',
      'Lounge Income:': 'Rendimento do lounge:',
      'Lounge Upkeep:': 'Manutenção do lounge:',
      'Lounge Supplies:': 'Consumíveis do lounge:',
      'Office Upkeep:': 'Manutenção do escritório:',
      'Base Upkeep:': 'Manutenção da base:',
      'Base Upkeep after upgrade:': 'Manutenção da base após melhoria:',
      'Base Construction:': 'Construção da base:',
      'Facility Construction:': 'Construção de instalações:',
      'Overtime Compensation:': 'Compensação de horas extra:',
      'Overtime Comp.': 'Horas extra',
      'Delay Compensation:': 'Compensação por atrasos:',
      'Flight Cancellation:': 'Cancelamento de voos:',
      'Flight Delays:': 'Atrasos de voos:',
      'Compensation:': 'Compensação:',
      'Loan Interest:': 'Juros do empréstimo:',
      'Loan Principal Repayment:': 'Amortização do capital:',
      'Loans Repayment': 'Pagamento de empréstimos',
      'Outstanding Loans': 'Empréstimos por pagar',
      'New Loan': 'Novo empréstimo',
      'Loan Options': 'Opções de empréstimo',
      'Available Loan:': 'Empréstimo disponível:',
      'Borrow Amount:': 'Montante a pedir:',
      'Principal Amount': 'Capital',
      'Interest': 'Juros',
      'Interest Rate': 'Taxa de juro',
      'Interest Rate History': 'Histórico da taxa de juro',
      'Weekly Payment': 'Pagamento semanal',
      'Total Payment': 'Pagamento total',
      'Remaining Payment': 'Pagamento em falta',
      'Remaining Duration': 'Duração restante',
      'Remaining Weeks': 'Semanas restantes',
      'Early Repayment Amount': 'Montante de liquidação antecipada',
      'Early Repayment Fee': 'Taxa de liquidação antecipada',
      'Setup Cost:': 'Custo inicial:',
      'Initial Cost': 'Custo inicial',
      'Weekly Cost:': 'Custo semanal:',
      'Upkeep:': 'Manutenção:',
      'Upkeep after Upgrade:': 'Manutenção após melhoria:',
      'Upgrade Cost:': 'Custo da melhoria:',
      'Build/Upgrade Cost:': 'Custo de construção/melhoria:',
      'Build/Upgrade Duration:': 'Duração da construção/melhoria:',
      'Replace cost:': 'Custo de substituição:',
      'Sell Value:': 'Valor de venda:',
      'Selling value:': 'Valor de venda:',
      'Purchase Price:': 'Preço de compra:',
      'Others Revenue:': 'Outras receitas:',
      'Others Expense:': 'Outras despesas:',
      'Others Profit:': 'Outros lucros:',
      'Asset Revenue:': 'Receita dos bens:',
      'Asset Expense:': 'Despesa dos bens:',
      'Transactions': 'Transações',
      'Transaction Type': 'Tipo de transação',
      'Transactions Revenue:': 'Receita de transações:',
      'Transactions Expense:': 'Despesa de transações:',
      'Transactions Profit:': 'Lucro de transações:',
      'Airport Asset Transactions:': 'Transações de bens do aeroporto:',

      // ------------------------------------------------------------- routes
      'From': 'De',
      'From:': 'De:',
      'From :': 'De:',
      'To': 'Para',
      'To:': 'Para:',
      'From Airport': 'Aeroporto de partida',
      'To Airport': 'Aeroporto de chegada',
      'Destination': 'Destino',
      'Destination Airport': 'Aeroporto de destino',
      'Destinations:': 'Destinos:',
      'Top Destinations': 'Principais destinos',
      'Distance': 'Distância',
      'Distance:': 'Distância:',
      'Duration': 'Duração',
      'Flight Duration:': 'Duração do voo:',
      'Flight Code:': 'Código do voo:',
      'Flight Type': 'Tipo de voo',
      'Flight Type:': 'Tipo de voo:',
      'Flight Class': 'Classe',
      'Flight Frequency': 'Frequência de voos',
      'Flight Frequency:': 'Frequência de voos:',
      'Flight Routes': 'Rotas',
      'Total Flight Routes': 'Total de rotas',
      'Total Flight Routes:': 'Total de rotas:',
      'Total Routes:': 'Total de rotas:',
      'Route Setup:': 'Criação da rota:',
      'Route Weekly Profit': 'Lucro semanal da rota',
      'Setup New Flight Route:': 'Criar nova rota:',
      'Plan Route Details': 'Detalhes da rota planeada',
      'Existing Flights': 'Voos existentes',
      'Existing Settings': 'Definições atuais',
      'New Settings': 'Novas definições',
      'No flights yet': 'Ainda não há voos',
      'Freq.': 'Freq.',
      'Freq./Stops': 'Freq./Escalas',
      'Sch.': 'Hor.',
      'Capacity': 'Capacidade',
      'Capacity:': 'Capacidade:',
      'Capacity (Freq.)': 'Capacidade (freq.)',
      'Capacity (Y/J/F):': 'Capacidade (Y/J/F):',
      'Capacity History': 'Histórico de capacidade',
      'Price (Y/J/F):': 'Preço (Y/J/F):',
      'Cap (Freq)': 'Cap (freq)',
      'Cap(Freq):': 'Cap (freq):',
      'Cap Chg.': 'Var. cap.',
      'Price Chg.': 'Var. preço',
      'Cap': 'Cap',
      'LF': 'TO',
      'Load Factor:': 'Taxa de ocupação:',
      'PAX': 'PAX',
      'Pax': 'PAX',
      'Quality': 'Qualidade',
      'Ql.': 'Qual.',
      'Config': 'Config',
      'Configuration': 'Configuração',
      'Seats Config': 'Config. de lugares',
      'Seat Configuration:': 'Configuração de lugares:',
      'Seat Configurations:': 'Configurações de lugares:',
      'Seat configuration:': 'Configuração de lugares:',
      'Economy': 'Económica',
      'Economy Class:': 'Classe económica:',
      'Business': 'Executiva',
      'Business Class:': 'Classe executiva:',
      'First': 'Primeira',
      'First Class:': 'Primeira classe:',
      'Business PAX :': 'PAX de negócios:',
      'Tourist PAX :': 'PAX de turismo:',
      'Assigned Airplanes:': 'Aviões atribuídos:',
      'Available Airplanes:': 'Aviões disponíveis:',
      'Assigned route(s):': 'Rota(s) atribuída(s):',
      'Available flight time:': 'Tempo de voo disponível:',
      'Weekly Flight Movements:': 'Movimentos semanais:',
      'Increase/decrease the flight frequency by selecting/deselecting the airplane icons.':
        'Aumenta ou reduz a frequência selecionando ou desmarcando os ícones dos aviões.',
      'Then select the Destination Airport from the world map':
        'Depois escolhe o aeroporto de destino no mapa do mundo',
      'Or assign airplane to this airport': 'Ou atribui um avião a este aeroporto',
      'No airplane with this model based in this airport':
        'Não há nenhum avião deste modelo sediado neste aeroporto',

      // ---------------------------------------------------------- airplanes
      'Model': 'Modelo',
      'Model:': 'Modelo:',
      'Family': 'Família',
      'Family:': 'Família:',
      'Airplane Family': 'Família de aviões',
      'Airplane Model': 'Modelo de avião',
      'Airplane Model:': 'Modelo de avião:',
      'Airplane Model Details': 'Detalhes do modelo',
      'Airplane Model (max freq):': 'Modelo de avião (freq. máx.):',
      'Airplane ID': 'ID do avião',
      'Airplane ID:': 'ID do avião:',
      'Airplane:': 'Avião:',
      'Owned Airplane Details': 'Detalhes do avião',
      'Manufacturer:': 'Fabricante:',
      'Max Capacity:': 'Capacidade máxima:',
      'Max Flying Range:': 'Alcance máximo:',
      'Max Range': 'Alcance máximo',
      'Max Lifespan:': 'Vida útil máxima:',
      'Lifespan': 'Vida útil',
      'Lifespan:': 'Vida útil:',
      'Fuel Burn': 'Consumo',
      'Fuel Burn:': 'Consumo:',
      'Speed': 'Velocidade',
      'Speed:': 'Velocidade:',
      'Turnaround Time:': 'Tempo de escala:',
      'Category': 'Categoria',
      'Category:': 'Categoria:',
      'Condition': 'Estado',
      'Condition:': 'Estado:',
      'Condition Below': 'Estado abaixo de',
      'Age:': 'Idade:',
      'Fleet Age:': 'Idade da frota:',
      'Fleet Size': 'Tamanho da frota',
      'Fleet Size:': 'Tamanho da frota:',
      'Inventory': 'Inventário',
      'Delivery Time:': 'Prazo de entrega:',
      'Deliver in:': 'Entrega em:',
      'Used Airplane Market': 'Mercado de aviões usados',
      'Sold Airplanes': 'Aviões vendidos',
      'Previous Operator': 'Operador anterior',
      'Operators': 'Operadores',
      'Operators:': 'Operadores:',
      'Top Operators': 'Principais operadores',
      'Auto Airplane Renewal:': 'Renovação automática de aviões:',
      'Maintenance Cost Factor': 'Fator do custo de manutenção',
      'Quantity': 'Quantidade',
      'Quantity:': 'Quantidade:',
      'By Base': 'Por base',
      'By Model': 'Por modelo',
      'Airplane Livery': 'Pintura do avião',
      'Upload Airline Livery': 'Carregar a pintura da companhia',
      'Remove Livery/Slogan': 'Remover pintura/lema',
      'Illustration courtesy of Norebbo': 'Ilustração cortesia de Norebbo',
      'Preferred Suppliers': 'Fornecedores preferidos',
      'Light Airplane Suppliers': 'Fornecedores de aviões ligeiros',
      'Regional Airplane Suppliers': 'Fornecedores de aviões regionais',
      'Medium Airplane Suppliers': 'Fornecedores de aviões médios',
      'Large Airplane Suppliers': 'Fornecedores de aviões grandes',

      // ----------------------------------------------------------- airports
      'Airport Details': 'Detalhes do aeroporto',
      'Airport Name:': 'Nome do aeroporto:',
      'Airport:': 'Aeroporto:',
      'Airport Rating': 'Avaliação do aeroporto',
      'Airport scale:': 'Escala do aeroporto:',
      'Airport Coverage:': 'Cobertura do aeroporto:',
      'Airport Pop Coverage': 'População coberta',
      'Population': 'População',
      'Population:': 'População:',
      'Population Coverage:': 'População coberta:',
      'Coverage Pop.': 'Pop. coberta',
      'City': 'Cidade',
      'City:': 'Cidade:',
      'City :': 'Cidade:',
      'Country:': 'País:',
      'Home Country': 'País de origem',
      'Home Airport': 'Aeroporto principal',
      'Home airport:': 'Aeroporto principal:',
      'Income Level': 'Nível de rendimento',
      'Income Level:': 'Nível de rendimento:',
      'Average income level:': 'Nível de rendimento médio:',
      'Nearby Airports:': 'Aeroportos próximos:',
      'Small Airports:': 'Aeroportos pequenos:',
      'Medium Airports:': 'Aeroportos médios:',
      'Large Airports:': 'Aeroportos grandes:',
      'Connected Airports:': 'Aeroportos ligados:',
      'Connected Countries:': 'Países ligados:',
      'Served By Airlines:': 'Servido pelas companhias:',
      'Runways:': 'Pistas:',
      'Runway Req.': 'Pista necessária',
      'Runway Requirement:': 'Pista necessária:',
      'Runway requirement:': 'Pista necessária:',
      'Radius': 'Raio',
      'Radius:': 'Raio:',
      'Range:': 'Alcance:',
      'Area': 'Área',
      'Scale': 'Escala',
      'Scale:': 'Escala:',
      'Facilities:': 'Instalações:',
      'Transit options to nearby airports': 'Ligações a aeroportos próximos',
      'Visitors by Air': 'Visitantes por via aérea',
      'Domestic Flight Weekly Pax': 'PAX semanais em voos internos',
      'Intl. Flight Weekly Pax': 'PAX semanais em voos internacionais',
      'Invalidate Airport Image': 'Limpar a imagem do aeroporto',
      'Invalidate City Image': 'Limpar a imagem da cidade',

      // ------------------------------------------------------------- bases
      'Airline Bases': 'Bases da companhia',
      'Airline Bases:': 'Bases da companhia:',
      'Airline Headquarters': 'Sede da companhia',
      'Airline Headquarters:': 'Sede da companhia:',
      'Headquarters': 'Sede',
      'Hq': 'Sede',
      'Base Count': 'Número de bases',
      'Base Scale:': 'Escala da base:',
      'Base Type:': 'Tipo de base:',
      'Office Scale': 'Escala do escritório',
      'Office Scale Details': 'Detalhes da escala do escritório',
      'Office Staff Capacity': 'Capacidade de pessoal',
      'Office Staff Req.:': 'Pessoal necessário:',
      'Office Staff Required': 'Pessoal necessário',
      'Office Staff Requirement Breakdown': 'Detalhe do pessoal necessário',
      'Office Staff: (req/cap)': 'Pessoal: (nec./cap.)',
      'Staff Cap (HQ/base)': 'Cap. de pessoal (sede/base)',
      'Freq Cap 1': 'Lim. freq. 1',
      'Freq Cap 2': 'Lim. freq. 2',
      'Freq Cap 3': 'Lim. freq. 3',
      'First build your Headquarters': 'Constrói primeiro a tua sede',
      'Your Airline Headquarters/Base': 'A sede/base da tua companhia',
      'Airline Lounges': 'Lounges da companhia',
      'Lounge Level': 'Nível do lounge',
      'Lounge Name': 'Nome do lounge',
      'Requires Lounge': 'Requer lounge',
      'Weekly Lounge Visit:': 'Visitas semanais ao lounge:',
      'Weekly Lounge Visitors': 'Visitantes semanais do lounge',
      'Asset': 'Bem',
      'Asset Type:': 'Tipo de bem:',
      'Assets:': 'Bens:',
      'Specialization:': 'Especialização:',
      'Specializations': 'Especializações',
      'Aviation Hub Level': 'Nível de plataforma aeronáutica',

      // ------------------------------------------------------- the airline
      'Airline Details': 'Detalhes da companhia',
      'Airline Name:': 'Nome da companhia:',
      'Airline Code:': 'Código da companhia:',
      'Airline Color:': 'Cor da companhia:',
      'Airline Logo:': 'Logótipo da companhia:',
      'Airline Title': 'Título da companhia',
      'Airline Reputation': 'Reputação da companhia',
      'Airline:': 'Companhia:',
      'Company Slogan': 'Lema da companhia',
      'Logo Templates': 'Modelos de logótipo',
      'Upload Own Logo': 'Carregar logótipo próprio',
      'Tag Color:': 'Cor da etiqueta:',
      'Color 1 :': 'Cor 1:',
      'Color 2 :': 'Cor 2:',
      'Reputation': 'Reputação',
      'Reputation:': 'Reputação:',
      'Reputation Bonus': 'Bónus de reputação',
      'Reputation Boost': 'Reforço de reputação',
      'Reputation/Level:': 'Reputação/nível:',
      'Level': 'Nível',
      'Level:': 'Nível:',
      'Level History': 'Histórico de níveis',
      'Lv': 'Nv',
      'Grade:': 'Nota:',
      'Performance Grade:': 'Nota de desempenho:',
      'Service Quality': 'Qualidade do serviço',
      'Current Service Quality:': 'Qualidade do serviço atual:',
      'Target Service Quality:': 'Qualidade do serviço pretendida:',
      'Service Level:': 'Nível de serviço:',
      'Overall Quality:': 'Qualidade geral:',
      'Projected Service Funding (weekly):': 'Investimento previsto em serviço (semanal):',
      'Loyalty': 'Lealdade',
      'Loyalty Bonus': 'Bónus de lealdade',
      'Loyalty Bonus:': 'Bónus de lealdade:',
      'Loyalty to your airline:': 'Lealdade à tua companhia:',
      'Loyalist': 'Fiéis',
      'Loyalist Trend': 'Tendência dos fiéis',
      'Loyalist History/Trend': 'Histórico dos fiéis',
      'Total Loyalist:': 'Total de fiéis:',
      'Declare Bankruptcy': 'Declarar falência',
      'Declare bankruptcy': 'Declarar falência',
      'Rebuild Airline': 'Reconstruir a companhia',
      'Rebuild airline': 'Reconstruir a companhia',
      'Reset to initial game start': 'Repor no início do jogo',
      'All routes will be cancelled': 'Todas as rotas serão canceladas',
      'All of the airplanes will be removed': 'Todos os aviões serão removidos',
      'All loans and oil contracts cancelled': 'Todos os empréstimos e contratos de combustível serão cancelados',
      'All Airport Assets will be sold': 'Todos os bens em aeroportos serão vendidos',
      'Headquarters, bases and facilities will be removed': 'A sede, as bases e as instalações serão removidas',
      'Service Quality will be reset': 'A qualidade do serviço será reposta',
      'Reputation and Loyalty will be kept': 'A reputação e a lealdade mantêm-se',
      'Dropped from current Alliance': 'Saída da aliança atual',
      'New Balance after reset': 'Saldo após a reposição',
      'Balance reset to $': 'Saldo reposto em $',

      // ------------------------------------------------------- passengers
      'Passengers': 'Passageiros',
      'Weekly Passengers': 'Passageiros semanais',
      'Weekly Passengers:': 'Passageiros semanais:',
      'Weekly Passenger': 'Passageiros semanais',
      'Total Weekly Passengers': 'Total de passageiros semanais',
      'Weekly PAX (Y/J/F):': 'PAX semanais (Y/J/F):',
      'Weekly Passenger Miles': 'Passageiros-milha semanais',
      'Weekly Passengers (self)': 'Passageiros semanais (próprios)',
      'Weekly Passengers (alliance members)': 'Passageiros semanais (aliança)',
      'Weekly Passenger (Africa)': 'Passageiros semanais (África)',
      'Weekly Passenger (Asia)': 'Passageiros semanais (Ásia)',
      'Weekly Passenger (Europe)': 'Passageiros semanais (Europa)',
      'Weekly Passenger (North America)': 'Passageiros semanais (América do Norte)',
      'Weekly Passenger (Oceania)': 'Passageiros semanais (Oceânia)',
      'Weekly Passenger (South America)': 'Passageiros semanais (América do Sul)',
      'Passenger Composition': 'Composição dos passageiros',
      'Passenger Satisfaction': 'Satisfação dos passageiros',
      'Passenger Transit Type': 'Tipo de trânsito',
      'Passenger Type': 'Tipo de passageiro',
      'Travel Preference': 'Preferência de viagem',
      'Satisfaction': 'Satisfação',
      'Overall Satisfaction :': 'Satisfação geral:',
      'Other Passengers:': 'Outros passageiros:',
      'Your Passengers:': 'Os teus passageiros:',
      'Alliance Passengers:': 'Passageiros da aliança:',
      'Top Transit PAX': 'Principais PAX em trânsito',
      'Direct Demand (Y/J/F)': 'Procura direta (Y/J/F)',
      'Direct Demand (Y/J/F):': 'Procura direta (Y/J/F):',
      'Direct Competitions': 'Concorrência direta',
      'Direct Competitions:': 'Concorrência direta:',
      'Competition:': 'Concorrência:',
      'Airline share (as Departure)': 'Quota da companhia (à partida)',
      'Airline share (as Arrival)': 'Quota da companhia (à chegada)',
      'Network Capacity (Self + Alliance)': 'Capacidade da rede (própria + aliança)',
      'How likely a passenger from this airport would choose your airline':
        'A probabilidade de um passageiro deste aeroporto escolher a tua companhia',
      'How much will a passenger willing to pay for your ticket':
        'Quanto um passageiro está disposto a pagar pelo teu bilhete',
      'Passengers all have different preferences and emphasis when choosing their flights.':
        'Cada passageiro tem preferências diferentes ao escolher um voo.',

      // ------------------------------------------------------------- oil
      'Oil Price History': 'Histórico do preço do combustível',
      'Fuel Consumption History': 'Histórico de consumo',
      'Fuel consumption details of previous week': 'Consumo detalhado da semana anterior',
      'Active Oil Contracts': 'Contratos de combustível ativos',
      'New Oil Contract': 'Novo contrato de combustível',
      'Oil Contract Cost/Penalty:': 'Custo/penalização do contrato:',
      'Oil Contracts Termination': 'Rescisão de contratos de combustível',
      'Contract Duration:': 'Duração do contrato:',
      'Contract Initial Cost:': 'Custo inicial do contrato:',
      'Contract Per Barrel Price:': 'Preço por barril do contrato:',
      'Contract Price': 'Preço do contrato',
      'Contract Volume:': 'Volume do contrato:',
      'Contract Termination Cost (current):': 'Custo de rescisão (atual):',
      'Termination Penalty': 'Penalização por rescisão',
      'Price per Barrel': 'Preço por barril',
      'Inventory Policy': 'Política de inventário',
      'Inventory Price': 'Preço do inventário',
      'Current Inventory Price:': 'Preço atual do inventário:',
      'Total in Circulation:': 'Total em circulação:',
      'Volume': 'Volume',
      'barrels': 'barris',
      'Carefree': 'Despreocupada',
      'Swift': 'Rápida',
      'Comprehensive': 'Completa',
      'Policy': 'Política',
      'Current Policy:': 'Política atual:',
      'Airline keeps an inventory of fuel to buffer oil price fluctuations':
        'A companhia mantém uma reserva de combustível para amortecer as variações de preço',
      'Sign long term Oil Contract with fixed price.':
        'Assinar um contrato de longo prazo com preço fixo.',
      'The price offered depends on current market price and the contract duration':
        'O preço depende do preço de mercado atual e da duração do contrato',

      // -------------------------------------------------------- alliances
      'Alliance Details': 'Detalhes da aliança',
      'Alliance History': 'Histórico da aliança',
      'Alliance Name': 'Nome da aliança',
      'Alliance Name:': 'Nome da aliança:',
      'Alliance Bonus': 'Bónus da aliança',
      'Alliance:': 'Aliança:',
      'Alliance Championed Airports': 'Aeroportos liderados pela aliança',
      'Alliance Championed Countries': 'Países liderados pela aliança',
      'Your Airline Alliance': 'A aliança da tua companhia',
      'Your Alliance History': 'O histórico da tua aliança',
      'Your Alliance Mission': 'A missão da tua aliança',
      'Your Alliance Stats': 'As estatísticas da tua aliança',
      'Cannot join alliance': 'Não é possível entrar na aliança',
      'Leader Airline': 'Companhia líder',
      'Airline Member': 'Companhia membro',
      'Championed Airports': 'Aeroportos liderados',
      'Championed Airports:': 'Aeroportos liderados:',
      'Championed Countries': 'Países liderados',
      'Championed Countries:': 'Países liderados:',
      'Champion Airline of': 'Companhia líder de',
      'Champion Points': 'Pontos de liderança',
      'Mission:': 'Missão:',
      'Selected Mission:': 'Missão selecionada:',
      'Current Progress:': 'Progresso atual:',
      'Claim Reward': 'Receber recompensa',
      'Claimed Reward:': 'Recompensa recebida:',
      'Flight Code sharing - passengers are more willing to take connection flights from the same alliance':
        'Partilha de código - os passageiros aceitam mais facilmente ligações dentro da mesma aliança',

      // ------------------------------------------------------- diplomacy
      'Relationship': 'Relação',
      'Relationship Factors': 'Fatores da relação',
      'Relationship Management with': 'Gestão da relação com',
      'Relationship with your airline:': 'Relação com a tua companhia:',
      'Relationship with Destination Country:': 'Relação com o país de destino:',
      'Mutual Relationship:': 'Relação mútua:',
      'Your Relationship:': 'A tua relação:',
      'Delegates': 'Delegados',
      'Delegates Pool': 'Delegados disponíveis',
      'Country Delegates': 'Delegados no país',
      'Country Delegates Required:': 'Delegados necessários:',
      'Delegate Level:': 'Nível do delegado:',
      'Assigned Delegates for Campaign': 'Delegados atribuídos à campanha',
      'Assigned Delegates for negotiation': 'Delegados atribuídos à negociação',
      'Assigned Delegates to develop relationship': 'Delegados atribuídos a desenvolver a relação',
      'Cannot assign temporary delegates awarded as bonus':
        'Não é possível atribuir delegados temporários dados como bónus',
      'Negotiation Difficulty :': 'Dificuldade da negociação:',
      'Negotiation Difficulty Details': 'Detalhes da dificuldade da negociação',
      'Negotiation in progress... Success Rate:': 'Negociação em curso... taxa de sucesso:',
      'Estimated Difficulty:': 'Dificuldade estimada:',
      'Total difficulty:': 'Dificuldade total:',
      'Difficulty': 'Dificuldade',
      'Difficulty:': 'Dificuldade:',
      'Success Rate': 'Taxa de sucesso',
      'Attempts Left:': 'Tentativas restantes:',
      'Too hard! Reduce frequency, change airport or improve relationship first.':
        'Demasiado difícil. Reduz a frequência, muda de aeroporto ou melhora primeiro a relação.',
      'Upon first attempt, the difficulty will be fixed!':
        'A dificuldade fica fixa na primeira tentativa.',
      'National Airline of': 'Companhia nacional de',
      'National Airline:': 'Companhia nacional:',
      'Partnered Airline of': 'Companhia parceira de',
      'Partnered Airline:': 'Companhia parceira:',
      'Title with Destination Country:': 'Título no país de destino:',
      'Your Title:': 'O teu título:',
      'Your airline title with this country:': 'O título da tua companhia neste país:',
      'Market Openness:': 'Abertura do mercado:',
      'Country market openness:': 'Abertura do mercado do país:',
      'Openness': 'Abertura',

      // -------------------------------------------------------- campaigns
      'Active Campaigns': 'Campanhas ativas',
      'Campaign Details': 'Detalhes da campanha',
      'Campaign Management': 'Gestão de campanhas',
      'Campaign/Advertisement:': 'Campanha/publicidade:',
      'Brand Aware': 'Notoriedade da marca',
      'Active Boosts:': 'Reforços ativos:',

      // ----------------------------------------------------------- rankings
      'Rank': 'Posição',
      'Movement': 'Variação',
      'Trend': 'Tendência',
      'Top Airlines': 'Melhores companhias',
      'Top Airline:': 'Melhor companhia:',
      'Top Countries of Olympics Passengers transported': 'Países com mais passageiros olímpicos transportados',
      'Top Countries of Olympics Passengers missed': 'Países com mais passageiros olímpicos perdidos',
      'Percentage': 'Percentagem',
      'Missed Percentage': 'Percentagem perdida',
      'Transported Percentage': 'Percentagem transportada',
      'Total Score': 'Pontuação total',
      'Ownership Percentage': 'Percentagem de propriedade',
      'Self': 'Própria',
      'Others': 'Outras',
      'Other': 'Outro',
      'All': 'Tudo',

      // --------------------------------------------------------- messages
      'Message': 'Mensagem',
      'Send Direct Message': 'Enviar mensagem direta',
      'Send Broadcast Message': 'Enviar mensagem a todos',
      'Send private message (popup)': 'Enviar mensagem privada (janela)',
      'Private message from Admin': 'Mensagem privada do administrador',
      'Broadcast message from Admin': 'Mensagem do administrador',
      'Broadcast message to all online airlines': 'Mensagem para todas as companhias ligadas',
      'Recent Activities': 'Atividade recente',
      'Top Comments': 'Principais comentários',
      'Positive Comment': 'Comentário positivo',
      'Negative Comment': 'Comentário negativo',
      'Add Self Note': 'Adicionar nota pessoal',
      'Search History': 'Histórico de procuras',
      // The filter boxes above the long lists, and the week in review.
      'Filter aircraft': 'Filtrar aviões',
      'Filter routes': 'Filtrar rotas',
      'Losing money': 'A perder dinheiro',
      'nothing matches': 'nada encontrado',
      'Last week': 'Semana passada',
      'Guide': 'Guia',
      // The charts: their labels are drawn as text, so they translate like any
      // other part of the page.
      'Quarter': 'Trimestre',
      'Week': 'Semana',
      'Quarterly Profit': 'Lucro trimestral',
      'Seats Consumption': 'Lugares vendidos',
      'Ticket Price': 'Preço do bilhete',
      'Sold Seats (Economy)': 'Vendidos (turística)',
      'Sold Seats (Business)': 'Vendidos (executiva)',
      'Sold Seats (First)': 'Vendidos (primeira)',
      'Cancelled Seats': 'Lugares cancelados',
      'Empty Seats': 'Lugares vazios',
      'Revenue (Economy)': 'Receita (turística)',
      'Revenue (Business)': 'Receita (executiva)',
      'Revenue (First)': 'Receita (primeira)',
      'Price (Economy)': 'Preço (turística)',
      'Price (Business)': 'Preço (executiva)',
      'Price (First)': 'Preço (primeira)',
      // Negotiating a route.
      'Negotiation': 'Negociação',
      'Negotiation Info': 'Informação da negociação',
      'Negotiation Difficulty:': 'Dificuldade da negociação:',
      'Odds': 'Probabilidade',
      'Assigned Delegates': 'Delegados atribuídos',
      'Available Delegates': 'Delegados disponíveis',
      'Delegate': 'Delegado',
      'Busy': 'Ocupado',
      'Available': 'Disponível',
      'Pool': 'Disponíveis',
      'Task': 'Tarefa',
      // Buying and selling aircraft.
      'Used': 'Usado',
      'Brand New': 'Novo',
      'Age': 'Idade',
      'Sell Value': 'Valor de venda',
      'Delivery': 'Entrega',
      'Immediate': 'Imediata',
      'Home Base': 'Base',
      'Utilization': 'Utilização',
      'Assign': 'Atribuir',
      'Unassign': 'Retirar',
      'Force week': 'Forçar semana',
      'Backup': 'Cópia de segurança',
      'Backup started': 'Cópia iniciada',
      'Airlines by profit': 'Companhias por lucro',
      'Most profitable routes': 'Rotas mais rentáveis',
      'Nothing happened': 'Não aconteceu nada',
      'Nothing flew': 'Não voou nada',
      'No figures for this week yet': 'Ainda não há números desta semana',
      // The alliance panel, where the wording had to say what to do next.
      'Abandoned': 'Abandonada',
      'No leader': 'Sem líder',
      'Not in an alliance': 'Sem aliança',
      'Build a headquarters first': 'Cria primeiro uma sede',
      'This alliance no longer exists - leave it to start again': 'Esta aliança já não existe - sai dela para recomeçar',
      'Applied - but this alliance has no leader to accept you': 'Candidatura enviada - mas esta aliança não tem líder para te aceitar',
      // What the search boxes answer when they have nothing to show. These
      // come down from the server as text, not as a panel, which is why they
      // are here rather than in a template.
      'No match': 'Nada encontrado',
      'Search with at least 2 characters': 'Escreve pelo menos 2 letras',
      'Search with at least 3 characters': 'Escreve pelo menos 3 letras',
      'Event': 'Evento',
      'Clue': 'Pista',
      'Selection': 'Seleção',
      'Severity': 'Gravidade',
      'Status:': 'Estado:',
      'Name': 'Nome',
      'Name:': 'Nome:',
      'Former Names:': 'Nomes anteriores:',
      'User': 'Utilizador',
      'User ID:': 'ID do utilizador:',
      'User Level:': 'Nível do utilizador:',
      'Username:': 'Nome de utilizador:',
      'User status': 'Estado do utilizador',
      'Last Seen': 'Visto pela última vez',
      'Occurrence': 'Ocorrências',
      'Modifiers': 'Modificadores',
      'Modifier:': 'Modificador:',
      'Please select your profile': 'Escolhe o teu perfil',

      // ------------------------------------------------------------- time
      'Start Week': 'Semana inicial',
      'Month': 'Mês',
      'Qtr': 'Trim.',
      'Year': 'Ano',
      'Cycle': 'Ciclo',
      'Current': 'Atual',
      'Current:': 'Atual:',
      'Current Details': 'Detalhes atuais',
      'Previous Week Value:': 'Valor da semana anterior:',
      'Current Streak:': 'Sequência atual:',
      'Longest Streak:': 'Melhor sequência:',
      'weeks': 'semanas',
      'weeks ago': 'semanas atrás',
      'week(s) ago': 'semana(s) atrás',
      'years': 'anos',
      'min': 'min',
      'km': 'km',
      'Minute(s)': 'Minuto(s)',
      'flight(s) per week': 'voo(s) por semana',
      'entries truncated)': 'entradas cortadas)',
      'pop:': 'pop:',
      'Pop': 'Pop',
      '(POP:': '(POP:',

      // --------------------------------------------------------- settings
      'Color Theme': 'Tema de cores',
      'UI Theme': 'Aspeto do jogo',
      'Language': 'Idioma',
      'Dark': 'Escuro',
      'Light': 'Claro',
      'Classic': 'Clássico',
      'Modern': 'Moderno',
      'Modern Lite (For slow systems)': 'Moderno leve (sistemas lentos)',
      'Default Wallpaper': 'Fundo predefinido',
      'Custom Wallpaper': 'Fundo personalizado',
      'Animated': 'Animado',
      'Autoplay': 'Reprodução automática',
      'Airline Club': 'Airline Club',
      '- Image should not be larger than 1MB': '- A imagem não deve exceder 1 MB',
      '- Logo should be 24px wide and 12px tall': '- O logótipo deve ter 24px de largura e 12px de altura',
      '- Logo should be in png format': '- O logótipo deve estar em formato png',
      '- Livery should be in png format, transparent background recommended':
        '- A pintura deve estar em formato png, de preferência com fundo transparente',
      'Code should be exactly 2 letters': 'O código tem de ter exatamente 2 letras',
      'Value must be an integer 0 - 100': 'O valor tem de ser um número inteiro entre 0 e 100',

      // ------------------------------------------------------- login page
      '&gt;&gt; Tell me more': '&gt;&gt; Conta-me mais',
      '&gt;&gt; Sign me up': '&gt;&gt; Quero jogar',
      '&gt;&gt; Check out the game world': '&gt;&gt; Ver o mundo do jogo',
      '&gt;&gt; Search for a Flight in-game': '&gt;&gt; Procurar um voo no jogo',
      'What changed': 'O que mudou',


      // ------------------------------------------- explanations and hints
      // The sentences the game uses to explain itself. These matter most of
      // all: a label can be guessed from where it sits, a paragraph cannot.
      '* Drag and drop airplane icon to another airplane icon to swap schedules and settings':
        '* Arrasta o ícone de um avião para cima de outro para trocar horários e definições',
      '* Drag and drop airplane icon to another box to switch configuration':
        '* Arrasta o ícone de um avião para outra caixa para mudar a configuração',
      '* Drag and drop airplane icon to another box to switch home base':
        '* Arrasta o ícone de um avião para outra caixa para mudar a base',
      '* Airplane Family # +': '* Família de aviões # +',
      '* Airplane Model #': '* Modelo de avião #',
      '- Adjustments': '- Ajustes',
      '- Factors': '- Fatores',
      '- Fleet age': '- Idade da frota',
      '- Fleet Age per Route': '- Idade da frota por rota',
      '- Service level per route': '- Nível de serviço por rota',
      '- Service Star level per route': '- Estrelas de serviço por rota',
      '- Company wide Service Quality': '- Qualidade do serviço em toda a companhia',
      '- Company wide level Service Quality (by Service Funding in Office View)':
        '- Qualidade do serviço da companhia (definida pelo investimento no Escritório)',
      'Airport loyalty affects:': 'A lealdade do aeroporto afeta:',
      'Airport loyalty is built up by:': 'A lealdade do aeroporto ganha-se com:',
      'Increasing number of loyalists of this airport':
        'Aumentar o número de fiéis neste aeroporto',
      'Loyalist are gained by providing satisfying services to passengers originate from this airport.':
        'Ganham-se fiéis dando bom serviço aos passageiros que partem deste aeroporto.',
      'Becoming national or partnered airlines of this country':
        'Tornar-se companhia nacional ou parceira deste país',
      'Flight Quality Expectation': 'Qualidade esperada do voo',
      'Flight Quality is affected by:': 'A qualidade do voo depende de:',
      'Overall quality is determined by:': 'A qualidade geral resulta de:',
      'Some passengers are reluctant to take flights that do not match expected quality':
        'Há passageiros que evitam voos que não chegam à qualidade que esperam',
      'Passenger satisfaction is reflected by Satisfaction Factor (SF) of a flight.':
        'A satisfação dos passageiros aparece no Fator de Satisfação (SF) do voo.',
      'Quality expection for this route from passengers of this airport:':
        'Qualidade que os passageiros deste aeroporto esperam nesta rota:',
      'Airplane maintenance cost is multiplied by this factor. Maintenance factor =':
        'O custo de manutenção é multiplicado por este fator. Fator de manutenção =',
      'In order to completely fix fuel price, Oil Contract should be signed':
        'Para fixar o preço do combustível por completo é preciso assinar um contrato',
      'Oil from inventory still fluctuates with the market price but to a smaller degree depending on the policy type':
        'O combustível em reserva ainda acompanha o mercado, mas menos, conforme a política escolhida',
      'Other than the cash price, all the other boosts are one-time only. The value will drop back to normal after a while':
        'Tirando o dinheiro, os reforços são todos temporários e voltam ao normal passado algum tempo',
      'Temporary boost such as negotiation great success or events':
        'Reforço temporário, por exemplo de um grande sucesso numa negociação ou de um evento',
      'Exceeding operation capacity of current base. Extra overtime compensation of':
        'Ultrapassa a capacidade da base atual. Compensação extra de horas extra de',
      'Difficulty value should be less than delegates available below.':
        'A dificuldade tem de ser menor do que os delegados disponíveis em baixo.',
      'Hover over the Red Cross icon above for details.':
        'Passa o rato pela cruz vermelha acima para veres os detalhes.',
      'Each delegate level provides': 'Cada nível de delegado dá',
      'First delegate with each level provides': 'O primeiro delegado de cada nível dá',
      'delegates to maintain current bases in this country':
        'delegados para manter as bases atuais neste país',
      'loyalty. Each additional delegates will only be 50% as effective as the previous one. Delegate starts at level 0 and progress over time. Hover over each delegate above to see details':
        'de lealdade. Cada delegado a mais rende metade do anterior. Os delegados começam no nível 0 e sobem com o tempo. Passa o rato por cada um para veres os detalhes',
      'relationship points. Delegate starts at level 0 and progress over time. Hover over each delegate above to see details':
        'pontos de relação. Os delegados começam no nível 0 e sobem com o tempo. Passa o rato por cada um para veres os detalhes',
      'Discount is capped at 80%': 'O desconto não passa dos 80%',
      'Common in low income countries': 'Comum em países de baixo rendimento',
      'For more info, please refer to the survey': 'Para saber mais, vê o inquérito',
      'will be charged per week for this change.': 'será cobrado por semana por esta alteração.',
      'This will replace': 'Isto vai substituir',
      'Confirm Purchase of': 'Confirmar a compra de',
      'reputation points (': 'pontos de reputação (',
      'Reputation increased by': 'Reputação aumentada em',
      'edit here': 'editar aqui',
      'as Favorite': 'como favorito',
      'as your new Favorite': 'como o teu novo favorito',

      // ---------------------------------------------------- more labels
      'Airline Mod:': 'Moderação da companhia:',
      'User Mod:': 'Moderação do utilizador:',
      'Basic support:': 'Apoio base:',
      'Capacity support:': 'Apoio em capacidade:',
      'Frequency support:': 'Apoio em frequência:',
      'Charms:': 'Encantos:',
      'Construction Time Discount:': 'Desconto no tempo de construção:',
      'Price Discount:': 'Desconto no preço:',
      'Discount:': 'Desconto:',
      'Discount /': 'Desconto /',
      'Penalty': 'Penalização',
      'Factor': 'Fator',
      'Growth': 'Crescimento',
      'Elite': 'Elite',
      'Require': 'Requer',
      'Role:': 'Função:',
      'Owner:': 'Dono:',
      'Supplies:': 'Consumíveis:',
      'SF': 'FS',
      'IATA:': 'IATA:',
      'IATA/ICAO:': 'IATA/ICAO:',
      'IP:': 'IP:',
      'UUID:': 'UUID:',
      'Principal Airport:': 'Aeroporto principal:',
      'Weekly Revenue:': 'Receita semanal:',
      'Sold Assets': 'Bens vendidos',
      'Sold Bases': 'Bases vendidas',
      'Normal (10 attempts)': 'Normal (10 tentativas)',
      'Hard (5 attempts, double reward)': 'Difícil (5 tentativas, recompensa a dobrar)',
      'You have exhausted all your attempts! Try again next week!':
        'Esgotaste as tentativas todas. Tenta outra vez para a semana.',
      'You will get new clue for each guess you make':
        'Cada palpite dá-te uma pista nova',
      'You have picked the reward as below': 'Escolheste a recompensa abaixo',
      'Please pick your reward as below:': 'Escolhe a tua recompensa:',
      'Please pick your reward as below, you can always come back later to make your choice!':
        'Escolhe a tua recompensa. Podes sempre voltar mais tarde para decidir.',

      // -------------------------------------------------------- olympics
      'Host City': 'Cidade anfitriã',
      'Host City:': 'Cidade anfitriã:',
      'Voted City:': 'Cidade votada:',
      'Olympics Host City Voting (1 : highest precedence)':
        'Votação da cidade anfitriã (1 = maior preferência)',
      'Current Olympics Passenger Score:': 'Pontuação olímpica atual:',
      'Olympics Passenger Score Goal:': 'Objetivo de pontuação olímpica:',
      'Olympics Passenger Score from previous week:': 'Pontuação olímpica da semana anterior:',
      'Target Olympics Passenger Score of this week:': 'Pontuação olímpica pretendida esta semana:',
      'Total Olympics Passengers (all airlines):': 'Total de passageiros olímpicos (todas as companhias):',
      'Total Olympics Passengers transported': 'Total de passageiros olímpicos transportados',
      'Total Olympics Passengers missed': 'Total de passageiros olímpicos perdidos',
      'Phase 1: Select Mission Candidates': 'Fase 1: escolher as candidatas',
      'Phase 2: Mission In Progress': 'Fase 2: missão a decorrer',
      'Year 1 - Host City Voting': 'Ano 1 - votação da cidade anfitriã',
      'Year 2 - Winning City Bid Announcement': 'Ano 2 - anúncio da cidade vencedora',
      'Year 3 - Games Preparation': 'Ano 3 - preparação dos jogos',
      'Year 4 - Year of Summer Olympics': 'Ano 4 - ano dos Jogos de Verão',

      // ------------------------------------------------------- christmas
      'Santa, Where are you?': 'Pai Natal, onde estás?',
      'Santa is at': 'O Pai Natal está em',
      'Search for Santa here': 'Procurar o Pai Natal aqui',
      'Woohoo! You have found Santa!': 'Boa! Encontraste o Pai Natal!',

      // ------------------------------------- the card on a flight in the air
      'Route': 'Rota',
      'Aircraft': 'Avião',
      'Departed': 'Partiu',
      'Lands': 'Aterra',
      'Flown': 'Voado',
      'Earns': 'Rende',
      'Costs': 'Custa',
      'Flights a week': 'Voos por semana',
      'Each way': 'Cada sentido',
      'On the ground': 'Em terra',
      'Next departure': 'Próxima partida',
      'Status': 'Estado',

      // ------------------------------------------- added by this instance
      'search aircraft': 'procurar avião',
      'search airport': 'procurar aeroporto',
      'filter': 'filtrar',

      // -------------------------------------------------------- admin bits
      'Admin Action': 'Ação de administração',
      'Super Admin Action': 'Ação de super-administração',
      'Airlines by IP': 'Companhias por IP',
      'Airlines by UUID': 'Companhias por UUID',
      'Ban': 'Banir',
      'Ban Chat': 'Banir do chat',
      'Ban and reset': 'Banir e repor',
      'Warn': 'Avisar',
      'Warn about ban': 'Avisar sobre banimento',
      'Nerf': 'Enfraquecer',
      'Set user level': 'Definir nível do utilizador',
      'Set banner winner': 'Definir vencedor do banner',
      'Switch to this user': 'Mudar para este utilizador',

      // ------------------------------------- delegates and the route dialog
      'Delegates sent to bring the price down': 'Delegados enviados para baixar o preço',
      'Route cost': 'Custo da rota',
      'Discount': 'Desconto',
      'None': 'Nenhum',
      'Assigned Delegate': 'Delegado destacado',
      'New Route': 'Rota nova',
      'Update Route': 'Alterar rota',
      'Place Order': 'Encomendar',
      'Lock this value': 'Fixar este valor',
      'No airplane model can fly to this destination': 'Nenhum avião consegue chegar a este destino',
      'Delegates (available/total) :': 'Delegados (livres/total) :',
      'Next delegate available in': 'Próximo delegado livre em',
      'Airport Lounge': 'Sala VIP do aeroporto',
      'Add New Configuration': 'Adicionar configuração nova',

      // ------------------------------------------------- states and notices
      'Updating...': 'A atualizar...',
      'Update finished.': 'Atualização terminada.',
      'Starting...': 'A começar...',
      'Asked...': 'Pedido...',
      'Very soon': 'Muito em breve',
      'Round': 'Ronda',
      'Eliminated': 'Eliminado',
      'Unlocked': 'Desbloqueado',
      'Not yet unlocked': 'Ainda por desbloquear',
      'Claimed': 'Recebido',
      'Not selected': 'Não escolhido',
      'Below': 'Abaixo',
      'Blueprint': 'Projeto',
      'Operating': 'Em funcionamento',
      'Under Construction - Complete in': 'Em obras - pronto em',
      'Free at scale': 'Grátis a partir do tamanho',
      'Not yet an Aviation Hub.': 'Ainda não é um centro de aviação.',
      'Do not meet hub scale requirement:': 'Não chega ao tamanho exigido:',
      'Potential Rewards': 'Prémios possíveis',
      'Requirements': 'Requisitos',
      'Benefits': 'Vantagens',
      'Version': 'Versão',
      'Economy:': 'Turística:',
      'Business:': 'Executiva:',
      'First:': 'Primeira:',
      'BEST SELLER': 'MAIS VENDIDO',
      'BEST DEAL': 'MELHOR NEGÓCIO',
      'Reference value from last week:': 'Valor de referência da semana passada:',
      'Outstanding loan of $': 'Empréstimo por pagar de $',
      'Target Reputation: -': 'Reputação pretendida: -',
      'Sorry, no flights available.': 'Não há voos disponíveis.',
      'Only admins can select mission for alliance': 'Só os administradores podem escolher a missão da aliança',
      'Applied - waiting for': 'Candidatura enviada - à espera de',
      'Forming - needs': 'A formar-se - faltam',
      'Feature is only avaiable to Patreons': 'Função reservada a apoiantes',
      'You may only upload airline banner at Reputation': 'Só podes enviar o cartaz da companhia com reputação',
      'You may only upload airline livery at Reputation': 'Só podes enviar a pintura da companhia com reputação',

      // ------------------------------------------------------------- chat
      'Chat Connected': 'Conversa ligada',
      'Chat Disconnected': 'Conversa desligada',
      'No more previous messages': 'Não há mensagens anteriores',
      'Message Not Sent : Rate Filter or Invalid Message': 'Mensagem não enviada: demasiado depressa ou inválida',

      // -------------------------------------------- the board of firsts
      'Firsts': 'Pioneiros',
      'Nobody yet': 'Ainda ninguem',
      'this week': 'esta semana',
      '1 week ago': 'ha 1 semana',
      'Wheels up': 'Levantar voo',
      'Opened the first route on this server': 'Abriu a primeira rota deste servidor',
      'Across a border': 'Passar a fronteira',
      'First to fly between two countries': 'Primeiro a voar entre dois paises',
      'The long way': 'A viagem longa',
      'First to fly between two continents': 'Primeiro a voar entre dois continentes',
      'Three flags': 'Tres bandeiras',
      'First to serve airports in three countries': 'Primeiro a servir aeroportos em tres paises',
      'Three continents': 'Tres continentes',
      'First to serve airports on three continents': 'Primeiro a servir aeroportos em tres continentes',
      'A fleet': 'Uma frota',
      'First to own ten aircraft': 'Primeiro a ter dez avioes',
      'An airline, properly': 'Uma companhia a serio',
      'First to own thirty aircraft': 'Primeiro a ter trinta avioes',
      'A thousand a week': 'Mil por semana',
      'First to carry a thousand passengers in one week': 'Primeiro a transportar mil passageiros numa semana',
      'Ten thousand a week': 'Dez mil por semana',
      'First to carry ten thousand passengers in one week': 'Primeiro a transportar dez mil passageiros numa semana',
      'A hundred thousand a week': 'Cem mil por semana',
      'First to carry a hundred thousand passengers in one week': 'Primeiro a transportar cem mil passageiros numa semana',
      'A hundred million': 'Cem milhoes',
      'First to hold $100,000,000': 'Primeiro a ter $100.000.000',
      'A billion': 'Mil milhoes',
      'First to hold $1,000,000,000': 'Primeiro a ter $1.000.000.000',

      // ------------------------------------------------- deals between us
      'Deals': 'Negocios',
      'Offer something': 'Propor alguma coisa',
      'Cash you give': 'Dinheiro que das',
      'Cash you want': 'Dinheiro que queres',
      'Aircraft you give': 'Avioes que das',
      'Send offer': 'Enviar proposta',
      'Accept': 'Aceitar',
      'Decline': 'Recusar',
      'Withdraw': 'Retirar',
      'Waiting': 'A aguardar',
      'Accepted': 'Aceite',
      'Declined': 'Recusada',
      'Expired': 'Expirada',
      'Nothing yet.': 'Ainda nada.',
      'Offer sent': 'Proposta enviada',
      'what this is for': 'para que e',
      'There is nobody else here to deal with yet.': 'Ainda nao ha mais ninguem com quem negociar.',
      'None idle - aircraft on a route cannot change hands': 'Nenhum livre - avioes numa rota nao mudam de dono',

      // ------------------------------------------- leasing and instalments
      'Lease': 'Alugar',
      'Lease:': 'Aluguer:',
      'Instalments': 'Prestacoes',
      'Instalments:': 'Prestacoes:',
      'Not paid for:': 'Por pagar:',
      'Hand back': 'Devolver',
      'Give up': 'Desistir',
      'Take it off its routes first': 'Tira-o primeiro das rotas',
      'Hand this aircraft back? The weekly rent stops, and nothing comes back.':
        'Devolver este aviao? A renda semanal para, e nao volta nada.',
      'Give this aircraft up? Everything paid on it so far is gone.':
        'Desistir deste aviao? Tudo o que ja pagaste por ele perde-se.',
      'That aircraft is not paid for - hand it back instead': 'Esse aviao nao esta pago - devolve-o em vez de vender',
      'That aircraft is not paid for - hand it back and take a newer one': 'Esse aviao nao esta pago - devolve-o e apanha um mais novo',
      'Aircraft that are not paid for cannot be swapped': 'Avioes por pagar nao podem ser trocados',
      'That aircraft is paid for - it is yours to keep or sell': 'Esse aviao esta pago - e teu para ficar ou vender',
      'Not enough cash for the deposit': 'Nao tens dinheiro para a entrada',
      'Leasing and instalments are switched off in this world': 'Aluguer e prestacoes estao desligados neste mundo',

      // ------------------------------------------------------------- taxes
      'Tax on route profit:': 'Imposto sobre o lucro das rotas:',

      // -------------------------------------------------------------- cargo
      'Cargo': 'Carga',
      'Break it': 'Desistir',
      'On offer': 'Em oferta',
      'Running': 'A decorrer',
      'Completed': 'Cumprido',
      'Cancelled': 'Cancelado',
      'Lapsed': 'Caducou',
      'Cargo is switched off in this world.': 'A carga esta desligada neste mundo.',
      'Nothing offered yet. Offers turn up on routes you already fly.':
        'Ainda nao ha ofertas. Aparecem em rotas que ja voas.',
      'That contract is no longer on the table': 'Esse contrato ja nao esta em cima da mesa',
      'That contract is not running': 'Esse contrato nao esta a decorrer',
      'Not your contract': 'Esse contrato nao e teu',

      // ---------------------------------------------------------- olympics
      'Olympics Voting Active': 'Votação olímpica a decorrer',
      'Unclaimed Olympics passenger reward (': 'Prémio olímpico de passageiros por receber (',
      'Unclaimed Olympics vote reward (': 'Prémio olímpico de votação por receber ('
    }
  };

  // Attributes worth translating: these are the ones the game actually uses
  // for text a player reads. Anything else is left alone.
  var ATTRIBUTES = ['title', 'placeholder', 'alt'];

  // Never descend into these. Script and style are code; textarea and input
  // hold what the player typed; the map is Leaflet's, and rewriting nodes
  // underneath it is asking for trouble.
  var SKIP_TAGS = { SCRIPT: 1, STYLE: 1, TEXTAREA: 1, CODE: 1, PRE: 1 };
  var SKIP_IDS = { map: 1 };

  var dictionary = null;

  function lookup(text) {
    if (!dictionary) return null;
    // Preserve the surrounding whitespace: the game leans on it for spacing,
    // and collapsing it shifts the layout of every table it is used in.
    var match = /^(\s*)([\s\S]*?)(\s*)$/.exec(text);
    var core = match[2];
    if (!core) return null;
    var translated = dictionary[core];
    if (translated === undefined) {
      // A trailing colon is so common that carrying both forms of every label
      // in the dictionary would double it for no benefit.
      if (core.charAt(core.length - 1) === ':' && dictionary[core.slice(0, -1)] !== undefined) {
        translated = dictionary[core.slice(0, -1)] + ':';
      } else {
        return null;
      }
    }
    return match[1] + translated + match[3];
  }

  function translateNode(node) {
    if (node.nodeType === 3) {                     // text
      var replacement = lookup(node.nodeValue);
      if (replacement !== null) node.nodeValue = replacement;
      return;
    }
    if (node.nodeType !== 1) return;               // not an element
    if (SKIP_TAGS[node.tagName]) return;
    if (node.id && SKIP_IDS[node.id]) return;
    if (node.hasAttribute && node.hasAttribute('data-no-translate')) return;

    for (var a = 0; a < ATTRIBUTES.length; a++) {
      var name = ATTRIBUTES[a];
      if (node.hasAttribute && node.hasAttribute(name)) {
        var value = lookup(node.getAttribute(name));
        if (value !== null) node.setAttribute(name, value);
      }
    }
    // Buttons carry their label in value= rather than as text.
    if (node.tagName === 'INPUT') {
      var type = (node.getAttribute('type') || '').toLowerCase();
      if (type === 'button' || type === 'submit') {
        var label = lookup(node.value);
        if (label !== null) node.value = label;
      }
      return;
    }

    for (var child = node.firstChild; child; child = child.nextSibling) {
      translateNode(child);
    }
  }

  /** Translate a subtree. Safe to call repeatedly - already-translated text
   *  simply is not in the dictionary and is left as it is. */
  function translateTree(root) {
    if (!dictionary || !root) return;
    try {
      translateNode(root);
    } catch (e) {
      // A translation failing is never worth breaking a screen over.
      console.log('translation skipped: ' + e.message);
    }
  }

  function currentLanguage() {
    var chosen = (typeof $ !== 'undefined' && $.cookie) ? $.cookie(COOKIE) : null;
    if (!chosen) chosen = global.AIRLINE_LANGUAGE || 'en';
    return DICTIONARIES[chosen] ? chosen : 'en';
  }

  /** Switch language. Reloads, because the English is not kept anywhere once
   *  it has been replaced - and a reload is instant next to re-deriving it. */
  function setLanguage(lang) {
    if (typeof $ !== 'undefined' && $.cookie) {
      $.cookie(COOKIE, lang, { expires: 9999, path: '/' });
    }
    location.reload();
  }

  function start() {
    var lang = currentLanguage();
    dictionary = DICTIONARIES[lang] || null;

    var selector = document.getElementById('languageSelect');
    if (selector) selector.value = lang;

    if (!dictionary) return;

    translateTree(document.body);

    // The game builds nearly every panel after the fact, so translating once
    // would only ever catch the shell. Watch for what it adds later.
    if (typeof MutationObserver === 'undefined') return;
    var pending = [];
    var scheduled = false;
    new MutationObserver(function (records) {
      for (var i = 0; i < records.length; i++) {
        var added = records[i].addedNodes;
        for (var j = 0; j < added.length; j++) pending.push(added[j]);
      }
      if (scheduled || !pending.length) return;
      // Batched: the game can add hundreds of rows in one go, and translating
      // each as it lands would mean a layout pass per row.
      scheduled = true;
      requestAnimationFrame(function () {
        scheduled = false;
        var batch = pending;
        pending = [];
        for (var k = 0; k < batch.length; k++) translateTree(batch[k]);
      });
    }).observe(document.body, { childList: true, subtree: true });
  }

  global.airlineTranslate = {
    apply: translateTree,
    setLanguage: setLanguage,
    current: currentLanguage,
    languages: function () {
      return Object.keys(DICTIONARIES).concat(['en']).sort();
    }
  };
  // Kept short for the onchange attribute in the settings panel.
  global.setGameLanguage = setLanguage;

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start);
  } else {
    start();
  }

})(window);
