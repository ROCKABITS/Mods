#!/usr/bin/env python3
"""Original voxel research table, pixel materials and translations. No external art."""
from pathlib import Path
import json, math, random
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources'
ASSETS=ROOT/'assets/realmprogression'
def write(path,data):
 path.parent.mkdir(parents=True,exist_ok=True)
 path.write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n')
palette={'walnut':(82,53,41),'navy':(29,61,76),'brass':(190,146,69),'paper':(230,212,171),'pages':(217,196,155),'jade':(47,111,103),'ink':(31,38,47)}
for name,color in palette.items():
 image=Image.new('RGB',(32,32),color);d=ImageDraw.Draw(image);rng=random.Random(name)
 for y in range(32):
  for x in range(32):
   noise=rng.randrange(-9,10)+(5 if name=='walnut' and y%7==0 else 0)
   image.putpixel((x,y),tuple(max(0,min(255,c+noise)) for c in color))
 if name in ('paper','pages'):
  for y in range(5,29,5):d.line((4,y,27-rng.randrange(8),y),fill=(132,103,67),width=1)
 if name=='navy':
  d.line((0,0,31,0),fill=(207,166,84));d.line((0,31,31,31),fill=(207,166,84))
  for x in range(4,32,8):d.point((x,16),fill=(144,166,160))
 path=ASSETS/'textures/block'/f'{name}.png';path.parent.mkdir(parents=True,exist_ok=True);image.save(path)
elements=[]
def box(a,b,texture,rotation=None):
 element={'from':a,'to':b,'faces':{f:{'texture':'#'+texture,'uv':[0,0,16,16]} for f in ('north','south','east','west','up','down')}}
 if rotation:element['rotation']=rotation
 elements.append(element)
for x in (1,12):
 for z in (1,12):
  box([x,0,z],[x+3,1,z+3],'brass');box([x+.4,1,z+.4],[x+2.6,8,z+2.6],'walnut');box([x+.2,5.7,z+.2],[x+2.8,6.3,z+2.8],'brass')
box([2,2.4,7],[14,3.3,9],'walnut');box([7,2.5,2],[9,3.5,14],'walnut')
box([0,8,0],[16,9.4,16],'walnut');box([0,9.4,0],[16,9.8,16],'brass');box([.4,9.8,.4],[15.6,10.2,15.6],'navy')
for x in (1,14):
 for z in (1,14):box([x,10.2,z],[x+.7,10.4,z+.7],'brass')
# Open illuminated atlas, leather binding, ivory page stacks and central bookmark.
box([1.3,10.2,4],[9.3,10.5,12],'walnut');box([1.5,10.5,4.2],[5.1,10.9,11.8],'pages');box([5.4,10.5,4.2],[9.1,10.9,11.8],'pages')
box([1.6,10.9,4.3],[5,11.05,11.7],'paper');box([5.5,10.9,4.3],[9,11.05,11.7],'paper');box([5.1,10.45,3.8],[5.4,11.1,12.4],'jade')
for x in (1.9,5.8):
 for z in (5.1,6.5,7.9,9.3):box([x,11.06,z],[x+2.4,11.1,z+.13],'ink')
# Astrolabe: pedestal, intersecting brass rings, central crystal and pointer.
box([10,10.2,9],[14.8,10.7,13.8],'walnut');box([10.6,10.7,9.6],[14.2,11,13.2],'brass');box([12,11,11.1],[12.6,12,11.7],'brass')
cx,cy,cz=12.3,13,11.4
for i in range(8):
 angle=i*math.pi/4;x=cx+2.45*math.sin(angle);y=cy+2.45*math.cos(angle)
 if i in (2,6):box([x-.18,y-1.04,cz-.18],[x+.18,y+1.04,cz+.18],'brass')
 else:box([x-1.04,y-.18,cz-.18],[x+1.04,y+.18,cz+.18],'brass',{'origin':[x,y,cz],'axis':'z','angle':(0,-45,0,45,0,-45,0,45)[i]})
for i in range(8):
 angle=i*math.pi/4;x=cx+2.25*math.cos(angle);z=cz+2.25*math.sin(angle)
 if i in (0,4):box([x-.18,cy-.18,z-.95],[x+.18,cy+.18,z+.95],'brass')
 else:box([x-.95,cy-.18,z-.18],[x+.95,cy+.18,z+.18],'brass',{'origin':[x,cy,z],'axis':'y','angle':(0,45,0,-45,0,45,0,-45)[i]})
box([cx-.58,cy-.58,cz-.58],[cx+.58,cy+.58,cz+.58],'jade',{'origin':[cx,cy,cz],'axis':'z','angle':45})
box([cx-.1,cy-1.7,cz-.1],[cx+.1,cy+1.7,cz+.1],'brass')
# Rolled charter, wax seal, quill and ink pot.
box([1,10.2,1.1],[7,10.65,3.2],'paper');box([1,10.2,.9],[1.6,11,3.4],'pages');box([6.4,10.2,.9],[7,11,3.4],'pages');box([3.3,10.65,1.8],[4.3,10.9,2.6],'jade')
box([11.8,10.2,2],[13.6,11.2,3.8],'ink');box([12.15,11.2,2.35],[13.25,11.5,3.45],'brass');box([12.55,11.5,2.7],[12.85,14,3],'paper',{'origin':[12.7,11.5,2.85],'axis':'z','angle':-22.5})
write(ASSETS/'models/block/research_table.json',{'textures':dict({n:'realmprogression:block/'+n for n in palette},particle='realmprogression:block/walnut'),'elements':elements,'display':{'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.7,.7,.7]},'ground':{'translation':[0,3,0],'scale':[.35,.35,.35]},'fixed':{'rotation':[0,180,0],'scale':[.65,.65,.65]}}})
write(ASSETS/'blockstates/research_table.json',{'variants':{'facing='+d:{'model':'realmprogression:block/research_table','y':y} for d,y in [('north',0),('east',90),('south',180),('west',270)]}})
write(ASSETS/'models/item/research_table.json',{'parent':'realmprogression:block/research_table'})
write(ROOT/'data/realmprogression/recipe/research_table.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':['AGA','PBP','P P'],'key':{'A':{'item':'minecraft:amethyst_shard'},'G':{'item':'minecraft:gold_ingot'},'P':{'item':'minecraft:dark_oak_planks'},'B':{'item':'minecraft:writable_book'}},'result':{'id':'realmprogression:research_table','count':1}})
write(ROOT/'data/realmprogression/loot_table/blocks/research_table.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'realmprogression:research_table'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
write(ROOT/'data/minecraft/tags/block/mineable/axe.json',{'replace':False,'values':['realmprogression:research_table']})
en={
'title':'Kingdom Research','treasury':'Treasury: %s €','loading':'Opening the royal atlas…','search':'Search mods…','browse':'All installed mods','goals':'Kingdom goals','back':'Back','confirm':'Confirm','details':'Details','history':'Records','goal':'Treasury target (€)','namespaces':'Item namespaces','namespace_help':'Advanced: comma-separated item namespaces. The actual crafted result is checked. An approved scope cannot be expanded.','save':'Save goal','free':'Disable lock','empty':'No matching mods. Administrators can browse installed mods and select their crafting goals.','absent':'Not installed','protected':'Essential system','unlocked':'Unlocked','locked':'Crafting locked','available':'Free crafting','changed':'Goal updated','target':'Target: %s €','permanent':'Unlocks are permanent for the whole kingdom. The money remains in the treasury.','coverage':'%s standard crafting recipes detected. The actual output is checked, including alternative datapack recipes.','scope':'Inventory, crafting table and Vanilla crafter. Loot, trades and existing items remain usable. Custom machines may require an integration.','treasury_error':'Treasury unavailable; awaiting confirmation.','tooltip':'Crafting awaits kingdom research. Visit the Research Table.','unlocked_title':'A NEW AGE BEGINS','confirmation':'Save %s with a treasury goal of %s €? Status: %s. No money is charged. Previous permanent approvals are preserved.'}
pt={
'title':'Investigação do Reino','treasury':'Tesouro: %s €','loading':'A abrir o atlas real…','search':'Pesquisar mods…','browse':'Todos os mods','goals':'Metas do reino','back':'Voltar','confirm':'Confirmar','details':'Detalhes','history':'Registos','goal':'Meta do tesouro (€)','namespaces':'Namespaces dos itens','namespace_help':'Avançado: namespaces dos itens, separados por vírgulas. É verificado o resultado real do fabrico. Um âmbito já aprovado não pode ser ampliado.','save':'Guardar meta','free':'Desativar bloqueio','empty':'Nenhum mod encontrado. A administração pode consultar os mods instalados e definir as metas de fabrico.','absent':'Não instalado','protected':'Sistema essencial','unlocked':'Desbloqueado','locked':'Fabrico bloqueado','available':'Fabrico livre','changed':'Meta atualizada','target':'Meta: %s €','permanent':'O desbloqueio é permanente para todo o reino. O dinheiro fica no tesouro.','coverage':'%s receitas normais detetadas. É verificado o resultado real, incluindo receitas alternativas de datapacks.','scope':'Inventário, mesa de trabalho e crafter Vanilla. Saques, trocas e itens existentes continuam utilizáveis. Máquinas próprias podem precisar de integração.','treasury_error':'Tesouro indisponível; a aguardar confirmação.','tooltip':'O fabrico aguarda a investigação do reino. Consulta a Mesa de Investigação.','unlocked_title':'COMEÇA UMA NOVA ERA','confirmation':'Guardar %s com uma meta de %s €? Estado: %s. Não é cobrado dinheiro. As aprovações permanentes anteriores são preservadas.'}
errors={'storage':('The progression ledger cannot be read or saved. Preserve the world and recover its data.','Não foi possível ler ou guardar a progressão. Preserva o mundo e recupera os dados.'),'capacity':('The progression ledger has reached its capacity.','A progressão atingiu a capacidade de armazenamento.'),'request':('Invalid request or unsupported rule.','Pedido inválido ou regra não suportada.'),'permission':('Only authorised administrators can change crafting goals.','Só a administração autorizada pode alterar as metas.'),'protected':('This mod is essential or is no longer installed.','Este mod é essencial ou já não está instalado.'),'coverage':('No supported standard crafting recipes in the selected item namespaces.','Não existem receitas normais suportadas nos namespaces selecionados.'),'stale':('The kingdom data changed. Review the current values and try again.','Os dados do reino mudaram. Revê os valores e tenta novamente.'),'amount':('Enter a positive euro value with at most two decimal places.','Introduz um valor positivo em euros com até duas casas decimais.'),'distance':('Return to the Research Table to use this menu.','Volta à Mesa de Investigação para utilizar o menu.'),'treasury':('The public balance could not be confirmed. Existing locks are preserved.','Não foi possível confirmar o saldo público. Os bloqueios são preservados.')}
for lang,base in [('en_us',en),('pt_pt',pt),('pt_br',dict(pt,search='Pesquisar mods…',save='Salvar meta',history='Registros',title='Pesquisa do Reino'))]:
 data={'block.realmprogression.research_table':'Research Table' if lang=='en_us' else 'Mesa de Investigação' if lang=='pt_pt' else 'Mesa de Pesquisa','itemGroup.realmprogression':'Realm Progression'}
 data.update({'gui.realmprogression.'+k:v for k,v in base.items()});data.update({'error.realmprogression.'+k:v[0 if lang=='en_us' else 1] for k,v in errors.items()});write(ASSETS/'lang'/f'{lang}.json',data)
print('Generated original table:',len(elements),'elements; seven 32×32 materials and three translations.')
