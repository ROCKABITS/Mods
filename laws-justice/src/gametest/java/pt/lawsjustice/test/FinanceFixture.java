package pt.lawsjustice.test;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import pt.lawsjustice.compat.JusticeWallet;
import pt.lawsjustice.domain.FinanceLedger;
import pt.lawsjustice.service.FinanceService;
import pt.lawsjustice.service.MoneySettlement;

final class FinanceFixture implements AutoCloseable {
 final CourtFixture court;final GameTestHelper h;
 FinanceFixture(GameTestHelper h){this.h=h;court=new CourtFixture(h);for(var actor:court.base.actors.actors){actor.getInventory().clearContent();actor.getInventory().add(new ItemStack(Items.EMERALD,64));}}
 UUID fine(){UUID id=court.file();court.hear(id);JsonObject req=court.verdict(id);req.addProperty("amount","20");court.act(court.judge,"court_fine",req);h.assertTrue(court.court().cases().get(id).status().equals("FINED"),"A heard court verdict did not create a real fine");return court.base.data.finance().charges().keySet().iterator().next();}
 UUID bail(){if(court.base.sentence()==null)court.base.convict(36000);open(court.judge);JsonObject req=mutation(court.judge);req.addProperty("sentence",court.base.sentence().id().toString());req.addProperty("amount","20");req.addProperty("reason","Authorized native bail");act(court.judge,"finance_bail",req);h.assertTrue(court.base.data.finance().charges().size()==1,"Live authority did not authorize bail at its physical desk");return court.base.data.finance().charges().keySet().iterator().next();}
 void open(JusticeActors.Actor actor){actor.setServerLevel(h.getLevel());actor.setPos(court.desk.getX()+.5,court.desk.getY(),court.desk.getZ()-1.5);FinanceService.open(actor,court.desk);h.assertTrue(view(actor).get("open").getAsBoolean(),"No real physical finance session");}
 JsonObject view(JusticeActors.Actor actor){return FinanceService.snapshot(actor);}
 JsonObject mutation(JusticeActors.Actor actor){JsonObject state=view(actor),req=new JsonObject();req.addProperty("nonce",state.get("nonce").getAsString());req.addProperty("version",state.get("version").getAsLong());return req;}
 void act(JusticeActors.Actor actor,String op,JsonObject req){FinanceService.handle(actor,op,req.toString());}
 FinanceLedger finance(){return court.base.data.finance();}
 long balance(JusticeActors.Actor actor){return JusticeWallet.balance(actor,JusticeWallet.currentCurrency());}
 UUID prepare(UUID charge,JusticeActors.Actor payer){UUID id=UUID.randomUUID();court.base.data.replaceFinance(finance().prepare(id,charge,payer.getUUID(),payer.getGameProfile().getName(),payer.getGameProfile().getName(),System.currentTimeMillis()));return id;}
 @Override public void close(){MoneySettlement.stop(court.base.admin.server);FinanceService.stop(court.base.admin.server);for(var actor:court.base.actors.actors){try{Files.deleteIfExists(actor.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(actor.getUUID()+".dat"));Files.deleteIfExists(actor.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(actor.getUUID()+".dat_old"));}catch(java.io.IOException e){throw new IllegalStateException(e);}}court.close();}
}
