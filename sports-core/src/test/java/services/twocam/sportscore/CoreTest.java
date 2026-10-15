package services.twocam.sportscore;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;

public class CoreTest {
    @Test public void matchesJavaScriptEnginePointByPoint()throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=new java.util.zip.GZIPInputStream(getClass().getResourceAsStream("/conformance.json.gz"))){byte[] b=new byte[8192];int n;while((n=in.read(b))>=0)out.write(b,0,n);}
        JSONArray fixtures=new JSONArray(new String(out.toByteArray(),"UTF-8"));
        for(int i=0;i<fixtures.length();i++){JSONObject f=fixtures.getJSONObject(i);JSONArray points=new JSONArray(),all=f.getJSONArray("points"),expected=f.getJSONArray("expected");for(int p=0;p<all.length();p++){points.put(all.getInt(p));assertTrue(f.getString("id")+" point "+p,RacketRules.replay(f.getJSONObject("format"),points,0).similar(expected.getJSONObject(p)));}}
    }
    private String pin(LocalSportsHost h,String role)throws Exception{return h.openPairing(role).getString("code");}
    private JSONObject pair(LocalSportsHost h,String role)throws Exception{return (JSONObject)h.request("POST","/screens/"+h.screenId()+"/pair",null,null,new JSONObject().put("name",role).put("code",pin(h,role)));}
    @Test public void authorityIsPersistentScopedAndIdempotent()throws Exception{
        final String[] disk={null};LocalSportsHost h=new LocalSportsHost(null,s->disk[0]=s);h.configure("TV","Pista","A","B",RacketRules.standard("padel",3,"star",6,false),0);
        JSONObject official=pair(h,"official"),aux=pair(h,"home");pair(h,"away");
        try{h.openPairing("home");fail("Cuarto control");}catch(LocalSportsHost.Failure e){assertEquals(409,e.status);}
        String path="/screens/"+h.screenId();JSONObject body=new JSONObject().put("type","point").put("side",0).put("expectedVersion",1);
        JSONObject accepted=(JSONObject)h.request("POST",path+"/commands",official.getString("controlToken"),"same-point-0001",body);
        assertTrue(accepted.similar(h.request("POST",path+"/commands",official.getString("controlToken"),"same-point-0001",body)));
        try{h.request("POST",path+"/commands",aux.getString("controlToken"),"wrong-side-0001",new JSONObject().put("type","point").put("side",1).put("expectedVersion",2));fail("Permisos");}catch(LocalSportsHost.Failure e){assertEquals(403,e.status);}
        LocalSportsHost restarted=new LocalSportsHost(disk[0],s->disk[0]=s);assertTrue(accepted.similar(restarted.request("POST",path+"/commands",official.getString("controlToken"),"same-point-0001",body)));
        restarted.revoke(official.getString("controlId"));try{restarted.request("GET",path+"/snapshot",official.getString("controlToken"),null,null);fail("Revocado");}catch(LocalSportsHost.Failure e){assertEquals(401,e.status);}
    }
    @Test public void diskFailureDoesNotConfirmOrKeepPoint()throws Exception{
        final boolean[] failDisk={false};LocalSportsHost h=new LocalSportsHost(null,s->{if(failDisk[0])throw new IOException("disk full");});h.configure("TV","Pista","A","B",RacketRules.standard("tennis",3,"advantage",6,false),0);JSONObject c=pair(h,"official");failDisk[0]=true;
        try{h.request("POST","/screens/"+h.screenId()+"/commands",c.getString("controlToken"),"disk-failure-001",new JSONObject().put("type","point").put("side",0).put("expectedVersion",1));fail("No debe confirmar");}catch(IOException expected){}
        assertEquals("0",h.displaySnapshot().getJSONObject("match").getJSONArray("labels").getString(0));assertEquals(1,h.displaySnapshot().getJSONObject("match").getLong("version"));
    }
}
