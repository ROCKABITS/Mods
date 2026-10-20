#!/usr/bin/env python3
from pathlib import Path
import json
rows='''title|Realm Marriage · Bonds of the Realm|Realm Marriage · Laços do Reino
certificate|Marriage certificate|Certidão de casamento
home|Our bond|A nossa união
ceremony|Ceremony|Cerimónia
terms|Agreement|Acordo
account|Accounts|Contas
journal|Journal|Registos
effects|Effects|Efeitos
admin|Administration|Administração
none|None|Nenhum
yes|Accepted|Aceite
no|Not yet accepted|Por aceitar
on|Enabled|Ativado
off|Disabled|Desativado
normal|Normal|Normais
reduced|Reduced|Reduzidas
couple|The couple|Os noivos
priest|Chosen celebrant|Celebrante escolhido
witness|Witness|Testemunha
phase|Ceremony stage|Etapa da cerimónia
mode|Financial regime|Regime financeiro
date|Date · UTC|Data · UTC
verified|Verified registry reference|Referência verificada no registo
privacy|Spouse on identity documents|Cônjuge nos documentos de identidade
public|Visible|Visível
private|Private|Privado
civil_status|Civil status|Estado civil
proposal_hint|A new beginning|Um novo começo
proposal_instructions|Hold an engagement ring and right-click the other player.|Segura um anel de noivado e clica com o botão direito no outro jogador.
proposal_received|Marriage proposal|Pedido de casamento
waiting|Waiting for a reply|A aguardar uma resposta
recipient|Only the recipient can accept this proposal.|Só o destinatário pode aceitar este pedido.
accept|Accept|Aceitar
decline|Decline|Rejeitar
agree|Accept this agreement|Aceitar este acordo
agreement|Agreement of both partners|Acordo dos dois noivos
accepted_a|First partner's consent|Consentimento do primeiro noivo
accepted_b|Second partner's consent|Consentimento do segundo noivo
priest_accepted|Celebrant's invitation|Convite do celebrante
invitation|Your ceremony invitation|O teu convite para a cerimónia
save|Save|Guardar
view|View|Consultar
refresh|Refresh|Atualizar
back|Back|Voltar
close|Close|Fechar
cancel|Cancel the ceremony|Cancelar a cerimónia
divorce|Request divorce|Pedir divórcio
confirmation|Confirm your decision|Confirma a tua decisão
confirm_hint|This decision affects the relationship. Reserved money stays assigned to its rightful owners.|Esta decisão afeta a relação. O dinheiro reservado continua atribuído aos seus titulares.
confirm|Confirm|Confirmar
documents|Rings and certificates|Alianças e certidões
claim_documents|Collect my documents|Recolher os meus documentos
engagement_first|Accept a proposal before preparing a ceremony.|Aceita um pedido antes de preparar uma cerimónia.
share_a|First partner's share · %|Parte do primeiro noivo · %
share_hint|Division rule|Regra da divisão
split_rule|The first partner receives the rounded-down percentage; the second receives the remaining cents.|O primeiro recebe a percentagem arredondada por defeito; o segundo recebe os cêntimos restantes.
fee_quote|Current fee for a new agreement|Taxa atual para um novo acordo
frozen_fee|Total fee in this agreement|Taxa total neste acordo
joint_hint|About joint money|Sobre o dinheiro conjunto
joint_details|Joint money consolidates both wallets when marrying. New personal income may be consolidated at the altar; other mods must opt into the payment API.|O dinheiro conjunto reúne as duas carteiras ao casar. Novos ganhos pessoais podem ser reunidos no altar; os outros mods têm de aderir à API de pagamentos.
personal_balance|My personal wallet|A minha carteira pessoal
shared_balance|Our shared balance|O nosso saldo partilhado
pending_credits|My reserved credits|Os meus créditos reservados
amount|Amount · €|Valor · €
deposit|Deposit|Depositar
withdraw|Withdraw|Levantar
consolidate|Consolidate my new income|Reunir os meus novos ganhos
claim_credits|Collect reserved credit|Receber crédito reservado
api_hint|Supported purchases|Compras suportadas
payment_api_hint|Shared purchases require an explicit integration. Existing mods keep their own payment rules.|As compras partilhadas exigem uma integração explícita. Os mods existentes mantêm as suas regras de pagamento.
decision|The couple's decision|Decisão dos noivos
continue|I agree to continue|Concordo em continuar
resume|Resume with both consents|Retomar com os dois consentimentos
start|Open the ceremony|Abrir a cerimónia
next|Continue the ceremony|Avançar a cerimónia
vows|Wedding vows|Votos de casamento
ask|Ask the next partner|Perguntar ao próximo noivo
i_do|I do|Sim, aceito
sign|Sign as a witness|Confirmar como testemunha
seal|Celebrate the marriage|Celebrar o casamento
speaker|The floor is given to|É dada a palavra a
speaker.objector|The objector|Quem apresentou a objeção
speaker.couple|The couple|Os noivos
speaker.|Awaiting the celebrant|A aguardar o celebrante
reason|Reason|Justificação
objection|Objection to the marriage|Objeção ao casamento
remaining|Time remaining|Tempo restante
seconds|%s seconds|%s segundos
reason_hint|Your objection pauses the ceremony; it does not cancel it.|A tua objeção suspende a cerimónia; não a cancela.
send|Submit reason|Enviar justificação
error|Pending issue|Situação pendente
fee|Marriage fee · €|Taxa de casamento · €
fee_current|Current wedding fee|Taxa de casamento atual
settings|Administrator settings|Configuração administrativa
setting.objectionSeconds|Objection window · seconds|Objeções · segundos
setting.guestRadius|Guest radius · blocks|Raio dos convidados · blocos
setting.participantRadius|Participant radius · blocks|Raio dos intervenientes · blocos
setting.ceremonyMinutes|Ceremony lifetime · minutes|Duração máxima · minutos
setting.witnessLimit|Maximum witnesses|Máximo de testemunhas
setting.proposalMinutes|Proposal lifetime · minutes|Validade do pedido · minutos
setting.feeCap|Maximum fee · cents|Taxa máxima · cêntimos
recovery|Financial recovery|Recuperação financeira
retry|Retry existing receipts|Repetir recibos existentes
union_id|Marriage UUID|UUID do casamento
admin_cancel|Emergency cancellation|Cancelamento de emergência
record|Marriage registry|Registo de casamentos
animations|Ceremony animations|Animações da cerimónia
particles|Particles|Partículas
volume|Sound volume|Volume dos sons
hud.opening|LET THE CEREMONY BEGIN|QUE COMECE A CERIMÓNIA
hud.introduction|WELCOME, FRIENDS OF THE REALM|BEM-VINDOS, AMIGOS DO REINO
hud.objections|Speak now, or forever hold your peace!|Se alguém se opõe a esta união, fale agora ou cale-se para sempre!
hud.press|Press %s to raise an objection|Carrega em %s para apresentar uma objeção
hud.party_hint|You may withdraw from the ceremony at any time.|Podes desistir da cerimónia a qualquer momento.
hud.interrupted|CEREMONY INTERRUPTED!|CERIMÓNIA INTERROMPIDA!
hud.objector|%s has raised an objection!|%s levantou uma objeção ao casamento!
hud.discussion|The couple decides whether to continue.|Os dois noivos decidem se querem continuar.
hud.vows|A PROMISE BEFORE THE REALM|UMA PROMESSA PERANTE O REINO
hud.married|MARRIAGE CELEBRATED!|CASAMENTO CELEBRADO!
hud.official|Officially married, with the realm as witness.|Oficialmente casados, com o reino como testemunha.
sound.opening|Wedding bells|Sinos de casamento
sound.countdown|Objection countdown|Contagem das objeções
sound.objection|Ceremony interrupted|Cerimónia interrompida
sound.married|Celebration bells|Sinos de celebração
notice.proposal|%s has proposed to you!|%s pediu-te em casamento!
notice.invitation|You have a ceremony invitation from %s.|Tens um convite para a cerimónia de %s.
notice.married|%s are officially married!|%s estão oficialmente casados!
help|Use an engagement ring on a player or prepare your ceremony at a Wedding Altar.|Usa um anel de noivado num jogador ou prepara a cerimónia num Altar de Casamento.'''
en={};pt={}
for row in rows.splitlines():
 k,a,b=row.split('|',2);en['marriage.'+k]=a;pt['marriage.'+k]=b
for key,a,b in [('SINGLE','Single','Solteiro'),('ENGAGED','Engaged','Noivo'),('MARRIED','Married','Casado'),('DIVORCED','Divorced','Divorciado')]:en['marriage.status.'+key]=a;pt['marriage.status.'+key]=b
phases=[('PROPOSED','Proposal awaiting consent','Pedido a aguardar consentimento'),('ENGAGED','Preparing the wedding','Preparação do casamento'),('OPENING','Opening','Abertura'),('INTRODUCTION','Welcome','Introdução'),('OBJECTIONS','Objections','Objeções'),('SUSPENDED','Suspended for discussion','Suspensa para discussão'),('VOWS','Individual vows','Votos individuais'),('COMMITTING','Confirming funded receipts','A confirmar recibos financeiros'),('MARRIED','Officially married','Oficialmente casados'),('DIVORCED','Divorced','Divorciados'),('CANCELLED','Cancelled','Cancelada')]
for k,a,b in phases:en['marriage.phase.'+k]=a;pt['marriage.phase.'+k]=b
for k,a,b in [('JOINT','Joint money','Dinheiro conjunto'),('SEPARATE','Separate money','Dinheiro separado'),('SHARED','Personal wallets + shared account','Carteiras pessoais + conta conjunta')]:en['marriage.mode.'+k]=a;pt['marriage.mode.'+k]=b
errors='''thread|A server-thread player is required.|É necessário um jogador no thread do servidor.
settings|Invalid settings or limits.|Configuração ou limites inválidos.
player|Unknown player.|Jogador desconhecido.
capacity|Registry capacity reached. Existing receipts were preserved.|Foi atingida a capacidade do registo. Os recibos existentes foram preservados.
clock|Invalid server clock.|Relógio do servidor inválido.
party|Only the authorised partners may make this decision.|Só os noivos autorizados podem tomar esta decisão.
self|You cannot propose to yourself.|Não podes pedir-te a ti próprio em casamento.
occupied|A player or this altar already has an active ceremony or relationship.|Um jogador ou este altar já tem uma relação ou cerimónia ativa.
union|This relationship is unavailable.|Esta relação está indisponível.
proposal|This proposal cannot be accepted here or has expired.|Este pedido não pode ser aceite aqui ou já expirou.
planning|The agreement may only change before the ceremony.|O acordo só pode ser alterado antes da cerimónia.
priest|The chosen celebrant must accept and be present.|O celebrante escolhido tem de aceitar e estar presente.
witnesses|Check the invited witnesses and their consent.|Verifica as testemunhas convidadas e os seus consentimentos.
agreement|Both partners must accept a valid financial agreement.|Os dois noivos têm de aceitar um acordo financeiro válido.
invitation|Both partners must choose you before you can accept.|Os dois noivos têm de te escolher antes de aceitares.
ready|The couple, celebrant and invited witnesses must accept first.|Os noivos, o celebrante e as testemunhas convidadas têm de aceitar primeiro.
phase|This action is not available at this ceremony stage.|Esta ação não está disponível nesta etapa da cerimónia.
objection|The objection window is closed or an objection is already registered.|O prazo das objeções terminou ou já existe uma objeção registada.
reason|Only the objector may submit a reason, up to 256 characters.|Só quem se opôs pode enviar uma justificação, até 256 caracteres.
resume|Both partners must agree before the celebrant resumes.|Os dois noivos têm de concordar antes de o celebrante retomar.
vows|Both individual vows and the invited witnesses are required.|São necessários os dois votos individuais e as testemunhas convidadas.
pending|An earlier financial receipt is still pending. Retry after recovery.|Existe um recibo financeiro pendente. Repete após a recuperação.
married|This action requires your current official marriage.|Esta ação exige o teu casamento oficial atual.
amount|Enter a valid positive euro amount within the limits.|Introduz um valor válido em euros, positivo e dentro dos limites.
transaction|The financial receipt is invalid or its original payload changed.|O recibo financeiro é inválido ou os seus dados originais mudaram.
funds|Insufficient funds or the account is locked for settlement.|Saldo insuficiente ou conta bloqueada para liquidação.
consumer|Only the registered payment integration may use this receipt.|Só a integração de pagamentos registada pode usar este recibo.
storage|Persistent storage is unavailable. Preserve the data and restart after fixing storage.|O armazenamento está indisponível. Preserva os dados e reinicia depois de corrigir o armazenamento.
wallet|The Chronicles wallet could not be updated safely.|Não foi possível atualizar a carteira Chronicles em segurança.
chronicles|This financial regime requires Chronicles RP.|Este regime financeiro exige o Chronicles RP.
profile|Create a Chronicles RP character first.|Cria primeiro uma personagem no Chronicles RP.
ambiguous|The saved receipt does not match the current player state. No payment was repeated.|O recibo guardado não corresponde ao estado atual do jogador. Nenhum pagamento foi repetido.
altar|Use an intact nearby Wedding Altar.|Utiliza um Altar de Casamento intacto e próximo.
cooldown|Wait before sending another proposal.|Aguarda antes de enviar outro pedido.
presence|The couple, chosen celebrant and invited witnesses must be alive and near the altar.|Os noivos, o celebrante e as testemunhas convidadas têm de estar vivos e próximos do altar.
request|Invalid or expired request. Refresh the menu.|Pedido inválido ou expirado. Atualiza o menu.
stale|The registry changed. Refresh and confirm the current information.|O registo mudou. Atualiza e confirma os dados atuais.
governor|Only the sworn President or an authorised administrator may change the fee.|Só o Presidente empossado ou um administrador autorizado pode alterar a taxa.
fee|The fee exceeds the permitted limit or the treasury is unavailable.|A taxa excede o limite permitido ou o tesouro está indisponível.
crown|This fee requires Crown Ledger 0.2.7 or a compatible marriage API. Funds remain reserved.|Esta taxa exige Crown Ledger 0.2.7 ou uma API de casamentos compatível. Os fundos continuam reservados.
council|Realm Council is unavailable or incompatible.|O Realm Council está indisponível ou é incompatível.
admin|Administrative permission is required.|É necessária autorização administrativa.
documents|Only married partners may claim their own uncollected documents.|Só os noivos casados podem recolher os seus documentos ainda não emitidos.
inventory|Free two inventory slots for your ring and certificate.|Liberta dois espaços no inventário para a tua aliança e certidão.
forged|This item is not an authentic marriage document.|Este item não é um documento de casamento autêntico.'''
for row in errors.splitlines():k,a,b=row.split('|',2);en['marriage.error.'+k]=a;pt['marriage.error.'+k]=b
for k,a,b in [('block.realmmarriage.wedding_altar','Wedding Altar','Altar de Casamento'),('item.realmmarriage.engagement_ring','Engagement Ring','Anel de Noivado'),('item.realmmarriage.wedding_ring','Wedding Ring','Aliança de Casamento'),('item.realmmarriage.marriage_certificate','Marriage Certificate','Certidão de Casamento'),('itemGroup.realmmarriage','Realm Marriage','Realm Marriage'),('key.realmmarriage.object','Raise an objection','Levantar uma objeção'),('key.categories.realmmarriage','Realm Marriage','Realm Marriage')]:en[k]=a;pt[k]=b
audit_rows="""proposal|Proposal sent|Pedido enviado
engaged|Engagement accepted|Noivado aceite
agreement_changed|Agreement updated|Acordo atualizado
agreement_accepted|Agreement accepted|Acordo aceite
invitation_accepted|Invitation accepted|Convite aceite
invitation_declined|Invitation declined|Convite recusado
opening|Ceremony opened|Abertura da cerimónia
ceremony_step|Ceremony advanced|Avanço da cerimónia
objection|Guest objection|Objeção de convidado
objection_reason|Objection statement|Justificação da objeção
suspended|Ceremony suspended|Cerimónia suspensa
continue_vote|Continuation accepted|Continuação aceite
resumed|Ceremony resumed|Cerimónia retomada
ask_vow|Individual vow requested|Voto individual solicitado
vow_accepted|Vow accepted|Voto aceite
witness_signed|Witness signed|Testemunha assinou
financial_seal|Settlement started|Liquidação iniciada
married|Marriage registered|Casamento registado
cancel_pending|Cancellation requested|Cancelamento solicitado
cancelled|Engagement cancelled|Noivado cancelado
financial_cancelled|Contributions reserved for refund|Contribuições reservadas para devolução
cancelled_after_fee_attempt|Union cancelled during settlement|União cancelada durante a liquidação
cancelled_fee_settled|Authorised fee settled|Taxa autorizada liquidada
divorced|Divorce registered|Divórcio registado
privacy|Privacy updated|Privacidade atualizada
wedding_fee|Wedding fee updated|Taxa de casamento atualizada
admin_settings|Settings updated|Configuração atualizada
admin_cancel|Administrative cancellation|Cancelamento administrativo
admin_retry|Recovery attempted|Recuperação tentada
speaker|Speaker selected|Uso da palavra
transaction_FUND|Marriage contribution|Contribuição para o casamento
transaction_DEPOSIT|Shared deposit|Depósito na conta comum
transaction_WITHDRAW|Shared withdrawal|Levantamento da conta comum
transaction_CREDIT|Personal credit collected|Crédito pessoal recolhido
transaction_DOCUMENTS|Documents issued|Documentos emitidos
payment_reserved|Purchase funds reserved|Dinheiro reservado para compra
payment_committed|Purchase confirmed|Compra confirmada
payment_refunded|Purchase refunded|Compra reembolsada"""
for row in audit_rows.splitlines():
 k,a,b=row.split('|',2);en['marriage.audit.'+k]=a;pt['marriage.audit.'+k]=b
root=Path(__file__).resolve().parents[1]/'src/main/resources/assets/realmmarriage/lang';root.mkdir(parents=True,exist_ok=True)
for name,d in [('en_us',en),('pt_pt',pt),('pt_br',pt)]: (root/(name+'.json')).write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n')
