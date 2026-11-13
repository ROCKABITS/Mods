#!/usr/bin/env python3
"""Original block geometry and pixel materials, plus bilingual product strings."""
from pathlib import Path
import json, random
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]/'src/main/resources'
ASSETS=ROOT/'assets/realmfitness'
def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')

materials={'iron':(74,77,88),'brass':(184,146,75),'leather':(84,55,79),'wood':(85,59,44),'belt':(29,32,38),'paper':(38,30,49),'accent':(172,149,194)}
for name,color in materials.items():
    rng=random.Random(name);image=Image.new('RGB',(32,32));pixels=image.load()
    for y in range(32):
        for x in range(32):
            n=rng.randint(-7,7)+(6 if name=='wood' and x%7==0 else 0)+(5 if name=='belt' and y%4==0 else 0)
            pixels[x,y]=tuple(max(0,min(255,c+n)) for c in color)
    path=ASSETS/f'textures/block/{name}.png';path.parent.mkdir(parents=True,exist_ok=True);image.save(path)

def box(x,y,z,X,Y,Z,material):return ([x,y,z],[X,Y,Z],material)
def model(parts,half=None):
    elements=[]
    for lo,hi,material in parts:
        if half is not None:
            base=half*16;lo=[lo[0],max(lo[1],base)-base,lo[2]];hi=[hi[0],min(hi[1],base+16)-base,hi[2]]
            if hi[1]<=lo[1]:continue
        elements.append({'from':lo,'to':hi,'faces':{face:{'texture':'#'+material,'uv':[0,0,16,16]} for face in ['north','south','east','west','up','down']}})
    return {'ambientocclusion':True,'textures':{**{m:'realmfitness:block/'+m for m in materials},'particle':'realmfitness:block/iron'},'elements':elements}

from gym_geometry import build_machines
machines=build_machines(ASSETS,ROOT)
for name in machines:
    write(ROOT/f'data/realmfitness/recipe/{name}.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':['IWI','ILI','III'],'key':{'I':{'item':'minecraft:iron_ingot'},'L':{'item':'minecraft:leather'},'W':{'item':{'bench':'minecraft:oak_slab','run':'minecraft:redstone','box':'minecraft:hay_block','pull':'minecraft:chain','row':'minecraft:oak_boat','squat':'minecraft:heavy_weighted_pressure_plate'}[name]}},'result':{'id':'realmfitness:'+name,'count':1}})

parts=[box(0,1,2,16,16,4,'wood'),box(1,2,1.1,15,15,2,'paper'),box(0,0,1,16,2,4,'brass'),box(0,15,1,16,16,4,'brass'),box(0,1,1,1,16,4,'brass'),box(15,1,1,16,16,4,'brass')]
write(ASSETS/'models/block/record_board.json',model(parts));write(ASSETS/'models/item/record_board.json',{'parent':'realmfitness:block/record_board','display':{'gui':{'rotation':[15,210,0],'scale':[.75,.75,.75]}}})
write(ASSETS/'blockstates/record_board.json',{'variants':{f'facing={f}':{'model':'realmfitness:block/record_board','y':a} for f,a in [('north',0),('east',90),('south',180),('west',270)]}})
write(ROOT/'data/realmfitness/loot_table/blocks/record_board.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'realmfitness:record_board'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
write(ROOT/'data/realmfitness/recipe/record_board.json',{'type':'minecraft:crafting_shaped','pattern':['GGG','PBP','III'],'key':{'G':{'item':'minecraft:gold_nugget'},'P':{'item':'minecraft:paper'},'B':{'item':'minecraft:oak_sign'},'I':{'item':'minecraft:iron_ingot'}},'result':{'id':'realmfitness:record_board','count':1}})
write(ASSETS/'models/item/athlete_card.json',{'parent':'minecraft:item/generated','textures':{'layer0':'realmfitness:item/athlete_card'}})
card=Image.new('RGBA',(16,16));p=card.load()
for y in range(3,13):
 for x in range(1,15):p[x,y]=(184,146,75,255) if y in (3,12) or x in (1,14) else (48,37,61,255)
for x in range(4,12):p[x,7]=(199,182,222,255)
for x in (3,4,11,12):
 for y in range(5,10):p[x,y]=(199,182,222,255)
(ASSETS/'textures/item').mkdir(parents=True,exist_ok=True);card.save(ASSETS/'textures/item/athlete_card.png')
write(ROOT/'data/realmfitness/recipe/athlete_card.json',{'type':'minecraft:crafting_shapeless','ingredients':[{'item':'minecraft:paper'},{'item':'minecraft:gold_nugget'}],'result':{'id':'realmfitness:athlete_card','count':1}})
write(ROOT/'data/minecraft/tags/block/mineable/pickaxe.json',{'replace':False,'values':['realmfitness:'+n for n in [*machines,'record_board']]})

en={
 'load_changed':'%s · %s — right-click to train', 'replace_legacy':'Pick up and place this older machine again to use its full-size riding positions.', 'set_result':'%s/%s reps · %s%% technique · +%s XP', 'leave_machine':'SHIFT: leave · F5: camera', 'compact_controls.bench':'SPACE: push / release · SHIFT: leave', 'compact_controls.run':'A / D: rhythm · SHIFT: leave', 'compact_controls.box':'M1 / M2: punch · A / D: dodge', 'compact_controls.pull':'SPACE: pull · A / D: balance', 'compact_controls.row':'W / S: stroke · SHIFT: leave', 'compact_controls.squat':'W: lower · S: rise · SHIFT: leave',
 'countdown':'Starting in %s…','title':'Athlete profile','training':'Training','close':'Close','refresh':'Refresh','back':'Back','save':'Save','delete':'Delete','edit':'Edit','stop':'Stop','begin':'Start workout','new_plan':'New routine','edit_plan':'Routine editor','plan_name':'Routine name','add_exercise':'Add exercise','remove_step':'Remove exercise','end_workout':'Finish workout','workout_done':'Workout complete','start_series':'Start set','series_ready':'Ready to train','athlete_level':'Athlete level %s','repetitions':'%s reps','sets':'%s sets','resistance':'Resistance %s','rest_seconds':'Rest: %ss','duration':'About %ss per set','daily_progress':'Today: %s/%s sets · %s/%s exercises','xp_today':'Daily XP: %s / %s','xp':'XP: %s / %s','fatigue':'Fatigue: %s%%','nearby':'Available nearby','not_nearby':'Find this machine in a gym','no_history':'Your first set will appear here.','no_records':'No records in this category.','last_28_days':'Training calendar · last 28 days','attribute_progress':'Permanent attribute levels','plan_summary':'%s sets · %s different exercises','workout_progress':'Workout: %s/%s sets','next_exercise':'Next: %s','live_reps':'Set: %s/%s attempts · %s valid','grip':'Grip: %s%%','cue_hint':'Press as the cue crosses the centre line','records_title':'HALL OF ATHLETES','pin_board':'Apply to board','plan_balanced':'Balanced training','plan_strength':'Strength training',
 'tab.profile':'Athlete','tab.routines':'Routines','tab.catalog':'Machines','tab.history':'Journal','tab.leaders':'Rankings','tab.records':'Records','tab.progress':'Progress','period.all':'All time','period.week':'This week','period.month':'This month','scope.gym':'Gym zone','scope.server':'Server','weekday.0':'Any day','weekday.1':'Monday','weekday.2':'Tuesday','weekday.3':'Wednesday','weekday.4':'Thursday','weekday.5':'Friday','weekday.6':'Saturday','weekday.7':'Sunday',
 'stat.bench':'Strength','stat.run':'Fitness','stat.box':'Coordination','stat.pull':'Body control','stat.row':'Aquatic endurance','stat.squat':'Leg power','exercise.bench':'Bench press','exercise.run':'Treadmill','exercise.box':'Punching bag','exercise.pull':'Pull-up bar','exercise.row':'Rowing machine','exercise.squat':'Squat rack',
 'attribute.bench':'Strength · melee damage','attribute.run':'Fitness · movement speed','attribute.box':'Coordination · attack speed','attribute.pull':'Body control · fall damage reduction','attribute.row':'Aquatic endurance · swimming speed','attribute.squat':'Leg power · jump height',
 'help.bench':'Hold SPACE to push, release to lower. Follow the green target with the lilac marker. Higher loads need greater precision.',
 'help.run':'Alternate A and D as each cue reaches the centre. Follow the rhythm rather than holding both keys.',
 'help.box':'Follow the sequence: left/right mouse buttons for punches, A/D for dodges. Hit the cue on the centre line.',
 'help.pull':'Hold SPACE to pull, release to lower and recover grip. Use A/D to keep your body balanced.',
 'help.row':'W pulls, S recovers. Coordinate both phases as the cue crosses the centre line.',
 'help.squat':'W lowers and S raises. Keep the marker inside the moving green depth zone.',
 'controls.bench':'Hold SPACE: push · Release: lower','controls.pull':'SPACE: pull / release · A/D: balance','controls.squat':'W: lower · S: rise',
 'settings_reloaded':'Fitness settings reloaded.','series_saved':'Set saved.','series_practice':'Practice set saved; improve your technique to earn XP.','daily_bonus':'Daily workout complete! Bonus XP awarded.',
 'invalid_request':'This action could not be completed.','invalid_plan':'Check the routine values (maximum 24 sets).','plan_limit':'You can save up to 12 routines.','plan_missing':'This routine no longer exists.','load_locked':'This load requires a higher attribute level.','resting':'Rest before starting the next set.','follow_plan':'Use the next exercise and load in your active workout.','workout_active':'Finish your active workout first.','station_missing':'This machine is no longer available.','too_far':'Move closer to the machine.','session_closed':'Reopen your athlete profile or machine.','survival_required':'Training requires a living Survival player, on foot.','machine_busy':'Someone is already using this machine.','already_training':'Finish your current set first.','training_interrupted':'Training stopped because you moved or changed state.','forbidden':'An operator can configure the public board.','invalid_settings':'Check config/realm-fitness.json.','storage_error':'Athlete data could not be saved.','capacity':'Athlete storage has reached its limit.','already_saved':'This set was already saved.','series_unfinished':'Finish the set before saving.'
}
pt={
 'load_changed':'%s · %s — botão direito para treinar', 'replace_legacy':'Recolhe e volta a colocar esta máquina antiga para usar a montagem com as novas dimensões.', 'set_result':'%s/%s rep. · %s%% técnica · +%s XP', 'leave_machine':'SHIFT: sair · F5: câmara', 'compact_controls.bench':'ESPAÇO: subir / largar · SHIFT: sair', 'compact_controls.run':'A / D: ritmo · SHIFT: sair', 'compact_controls.box':'M1 / M2: socos · A / D: esquivas', 'compact_controls.pull':'ESPAÇO: puxar · A / D: equilíbrio', 'compact_controls.row':'W / S: remada · SHIFT: sair', 'compact_controls.squat':'W: descer · S: subir · SHIFT: sair',
 'countdown':'Começa em %s…','title':'Perfil de Atleta','training':'Treino','close':'Fechar','refresh':'Atualizar','back':'Voltar','save':'Guardar','delete':'Apagar','edit':'Editar','stop':'Parar','begin':'Iniciar treino','new_plan':'Nova rotina','edit_plan':'Editor de rotinas','plan_name':'Nome da rotina','add_exercise':'Adicionar exercício','remove_step':'Remover exercício','end_workout':'Terminar treino','workout_done':'Treino concluído','start_series':'Iniciar série','series_ready':'Pronto para treinar','athlete_level':'Nível de atleta %s','repetitions':'%s repetições','sets':'%s séries','resistance':'Resistência %s','rest_seconds':'Descanso: %ss','duration':'Cerca de %ss por série','daily_progress':'Hoje: %s/%s séries · %s/%s exercícios','xp_today':'XP diário: %s / %s','xp':'XP: %s / %s','fatigue':'Fadiga: %s%%','nearby':'Disponível por perto','not_nearby':'Procura esta máquina num ginásio','no_history':'A tua primeira série aparecerá aqui.','no_records':'Sem recordes nesta categoria.','last_28_days':'Calendário de treinos · últimos 28 dias','attribute_progress':'Níveis permanentes dos atributos','plan_summary':'%s séries · %s exercícios diferentes','workout_progress':'Treino: %s/%s séries','next_exercise':'A seguir: %s','live_reps':'Série: %s/%s tentativas · %s válidas','grip':'Pega: %s%%','cue_hint':'Carrega quando o indicador cruza a linha central','records_title':'QUADRO DE ATLETAS','pin_board':'Aplicar ao painel','plan_balanced':'Treino equilibrado','plan_strength':'Treino de força',
 'tab.profile':'Atleta','tab.routines':'Treinos','tab.catalog':'Máquinas','tab.history':'Diário','tab.leaders':'Recordes','tab.records':'Recordes','tab.progress':'Evolução','period.all':'Sempre','period.week':'Esta semana','period.month':'Este mês','scope.gym':'Zona do ginásio','scope.server':'Servidor','weekday.0':'Qualquer dia','weekday.1':'Segunda','weekday.2':'Terça','weekday.3':'Quarta','weekday.4':'Quinta','weekday.5':'Sexta','weekday.6':'Sábado','weekday.7':'Domingo',
 'stat.bench':'Força','stat.run':'Condição física','stat.box':'Coordenação','stat.pull':'Controlo corporal','stat.row':'Resistência aquática','stat.squat':'Potência das pernas','exercise.bench':'Supino','exercise.run':'Passadeira','exercise.box':'Saco de boxe','exercise.pull':'Elevações','exercise.row':'Remo','exercise.squat':'Agachamentos',
 'attribute.bench':'Força · dano corpo a corpo','attribute.run':'Condição física · velocidade','attribute.box':'Coordenação · velocidade de ataque','attribute.pull':'Controlo corporal · redução de dano de queda','attribute.row':'Resistência aquática · natação','attribute.squat':'Potência das pernas · altura de salto',
 'help.bench':'Segura ESPAÇO para fazer força e larga para descer. Acompanha a zona verde com o marcador lilás. Mais carga exige maior precisão.',
 'help.run':'Alterna A e D quando cada indicador chega ao centro. Segue o ritmo das passadas.',
 'help.box':'Segue a sequência: botões esquerdo/direito do rato para os socos, A/D para esquivar. Acerta na linha central.',
 'help.pull':'Segura ESPAÇO para subir e larga para descer e recuperar a pega. Usa A/D para manter o equilíbrio.',
 'help.row':'W para puxar, S para recuperar. Coordena as duas fases na linha central.',
 'help.squat':'W para descer e S para subir. Mantém o marcador dentro da zona verde em movimento.',
 'controls.bench':'Segura ESPAÇO: subir · Larga: descer','controls.pull':'ESPAÇO: puxar / largar · A/D: equilíbrio','controls.squat':'W: descer · S: subir',
 'settings_reloaded':'Configuração do ginásio atualizada.','series_saved':'Série guardada.','series_practice':'Série de prática guardada; melhora a técnica para ganhar XP.','daily_bonus':'Treino diário concluído! Recebeste XP adicional.',
 'invalid_request':'Não foi possível concluir esta ação.','invalid_plan':'Verifica os valores da rotina (máximo de 24 séries).','plan_limit':'Podes guardar até 12 rotinas.','plan_missing':'Esta rotina já não existe.','load_locked':'Esta carga exige um nível superior no atributo.','resting':'Descansa antes da próxima série.','follow_plan':'Segue o próximo exercício e a carga do treino ativo.','workout_active':'Termina primeiro o treino ativo.','station_missing':'Esta máquina já não está disponível.','too_far':'Aproxima-te da máquina.','session_closed':'Abre novamente o perfil ou a máquina.','survival_required':'O treino exige um jogador vivo em Sobrevivência, a pé.','machine_busy':'Alguém já está a usar esta máquina.','already_training':'Termina primeiro a série atual.','training_interrupted':'O treino parou porque te moveste ou mudaste de estado.','forbidden':'Um operador pode configurar o painel público.','invalid_settings':'Verifica config/realm-fitness.json.','storage_error':'Não foi possível guardar os dados do atleta.','capacity':'O registo de atletas atingiu o limite.','already_saved':'Esta série já foi guardada.','series_unfinished':'Termina a série antes de guardar.'
}
assert en.keys()==pt.keys()
for lang,strings in [('en_us',en),('pt_pt',pt),('pt_br',pt)]:
    data={'fitness.'+k:v for k,v in strings.items()};data.update({'itemGroup.realmfitness':'Realm Fitness','item.realmfitness.athlete_card':'Cartão de atleta' if lang.startswith('pt') else 'Athlete card','block.realmfitness.record_board':'Painel de recordes' if lang.startswith('pt') else 'Record board','key.realmfitness.profile':'Perfil de Atleta' if lang.startswith('pt') else 'Athlete profile','key.categories.realmfitness':'Realm Fitness'})
    for n in machines:data['block.realmfitness.'+n]=strings['exercise.'+n]
    write(ASSETS/f'lang/{lang}.json',data)
print('Generated six original machines, record board, athlete card, recipes and three language files.')
