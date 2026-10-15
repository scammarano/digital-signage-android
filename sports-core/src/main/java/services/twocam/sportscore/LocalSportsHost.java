package services.twocam.sportscore;

import org.json.JSONArray;
import org.json.JSONObject;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.UUID;

/** Autoridad de Sports embebible. Un host por pantalla primaria; todas las mutaciones serializadas. */
public final class LocalSportsHost {
    public interface Persistence {void write(String json)throws Exception;}
    public static final class Failure extends Exception {public final int status;public Failure(int code,String message){super(message);status=code;}}
    private final Persistence persistence;private final SecureRandom random=new SecureRandom();private JSONObject data;private String pairCode="",pairRole="";private long pairExpiry;private int attempts;
    public LocalSportsHost(String saved,Persistence persistence)throws Exception{
        this.persistence=persistence;
        if(saved!=null){data=new JSONObject(saved);if(data.getInt("schema")!=1)throw new Exception("Versión de Sports incompatible");}
        else {data=new JSONObject().put("schema",1).put("screenId",UUID.randomUUID().toString()).put("screenName","Fire · Sports").put("matchId",UUID.randomUUID().toString()).put("names",new JSONArray().put("Lado A").put("Lado B")).put("court","Cancha").put("format",RacketRules.standard("tennis",3,"advantage",6,false)).put("firstServer",0).put("points",new JSONArray()).put("version",0).put("configured",false).put("controls",new JSONArray()).put("requests",new JSONObject()).put("events",new JSONArray()).put("sequence",0).put("matches",new JSONArray());persistence.write(data.toString());}
    }
    private interface Change{JSONObject apply()throws Exception;}
    private JSONObject transaction(Change action)throws Exception {JSONObject old=RacketRules.copy(data);try{JSONObject result=action.apply();persistence.write(data.toString());return RacketRules.copy(result);}catch(Exception e){data=old;throw e;}}
    private String secret(){byte[] b=new byte[32];random.nextBytes(b);StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(java.util.Locale.ROOT,"%02x",v&255));return s.toString();}
    private static String sha(String s)throws Exception{byte[] d=MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));StringBuilder out=new StringBuilder();for(byte b:d)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
    private static boolean same(String a,String b)throws Exception{return MessageDigest.isEqual(a.getBytes("UTF-8"),b.getBytes("UTF-8"));}
    private static String label(String s)throws Failure{if(s==null||s.trim().isEmpty()||s.trim().length()>100)throw new Failure(422,"Texto: entre 1 y 100 caracteres");return s.trim();}
    private void event(String type,String actor)throws Exception {long seq=data.getLong("sequence")+1;data.put("sequence",seq);data.getJSONArray("events").put(new JSONObject().put("schema","2cam.events.v1").put("event_id",UUID.randomUUID().toString()).put("sequence",seq).put("occurred_at_ms",System.currentTimeMillis()).put("source","sports").put("type",type).put("scope",new JSONObject().put("match_id",data.getString("matchId"))).put("payload",new JSONObject().put("version",data.getLong("version")).put("actor",actor)));}
    public synchronized String screenId()throws Exception{return data.getString("screenId");}
    public synchronized String name(){return data.optString("screenName");}
    public synchronized JSONArray controls()throws Exception{JSONArray a=new JSONArray();JSONArray c=data.getJSONArray("controls");for(int i=0;i<c.length();i++){JSONObject o=c.getJSONObject(i);a.put(new JSONObject().put("id",o.getString("id")).put("name",o.getString("name")).put("role",o.getString("role")));}return a;}
    public synchronized void revoke(String id)throws Exception{transaction(()->{JSONArray c=data.getJSONArray("controls"),next=new JSONArray();for(int i=0;i<c.length();i++)if(!c.getJSONObject(i).getString("id").equals(id))next.put(c.getJSONObject(i));data.put("controls",next);event("sports.control_revoked","local-admin");return new JSONObject();});}
    public synchronized JSONObject configure(String screenName,String court,String a,String b,JSONObject format,int firstServer)throws Exception{
        final String sn=label(screenName),cn=label(court),an=label(a),bn=label(b);final JSONObject f=RacketRules.validate(format);RacketRules.replay(f,new JSONArray(),firstServer);
        return transaction(()->{if(data.getBoolean("configured")){JSONArray archive=data.optJSONArray("matches");if(archive==null){archive=new JSONArray();data.put("matches",archive);}archive.put(match().put("points",new JSONArray(data.getJSONArray("points").toString())));}data.put("screenName",sn).put("court",cn).put("names",new JSONArray().put(an).put(bn)).put("format",f).put("firstServer",firstServer).put("points",new JSONArray()).put("matchId",UUID.randomUUID().toString()).put("version",data.getLong("version")+1).put("configured",true);event("sports.match_started","local-admin");return displaySnapshot();});
    }
    public synchronized JSONObject openPairing(String role)throws Exception{
        if(!role.equals("official")&&!role.equals("home")&&!role.equals("away"))throw new Failure(422,"Rol inválido");if(data.getJSONArray("controls").length()>=3)throw new Failure(409,"Máximo tres controles. Desvincula uno.");
        pairCode=String.valueOf(100000+random.nextInt(900000));pairRole=role;pairExpiry=System.currentTimeMillis()+120000;attempts=0;return new JSONObject().put("code",pairCode).put("expiresAt",pairExpiry);
    }
    private JSONObject match()throws Exception{
        JSONObject m=RacketRules.replay(data.getJSONObject("format"),data.getJSONArray("points"),data.getInt("firstServer"));
        m.put("id",data.getString("matchId")).put("courtId",data.getString("court")).put("names",data.getJSONArray("names")).put("format",data.getJSONObject("format")).put("version",data.getLong("version")).put("status",!data.getBoolean("configured")?"scheduled":m.getJSONObject("state").isNull("winner")?"live":"finished");return m;
    }
    public synchronized JSONObject displaySnapshot()throws Exception{return snapshotFor("display",true);}
    private JSONObject snapshotFor(String role,boolean display)throws Exception {
        return new JSONObject().put("schema","2cam.sports.snapshot.v1").put("screen",new JSONObject().put("id",screenId()).put("name",name()).put("mode","sports")).put("match",match()).put("role",role).put("lastSequence",data.getLong("sequence")).put("pairing",display&&pairExpiry>System.currentTimeMillis()&&attempts<5?new JSONObject().put("code",pairCode).put("expiresAt",pairExpiry):JSONObject.NULL);
    }
    private JSONObject access(String screen,String token)throws Exception{
        if(!screenId().equals(screen))throw new Failure(404,"Pantalla no encontrada");if(token==null||token.isEmpty())throw new Failure(401,"Control no autorizado");JSONArray controls=data.getJSONArray("controls");String hash=sha(token);for(int i=0;i<controls.length();i++){JSONObject c=controls.getJSONObject(i);if(same(hash,c.getString("tokenHash")))return c;}throw new Failure(401,"Control revocado o no autorizado");
    }
    public synchronized Object request(String method,String path,String token,String key,JSONObject body)throws Exception {
        if(method.equals("GET")&&path.equals("/health"))return new JSONObject().put("status","ok").put("version","0.3.0-fire").put("host","fire");
        if(method.equals("GET")&&path.equals("/discovery"))return new JSONArray().put(new JSONObject().put("id",screenId()).put("name",name()).put("mode","sports").put("slotsUsed",data.getJSONArray("controls").length()).put("slotsMax",3));
        String base="/screens/"+screenId();
        if(method.equals("POST")&&path.equals(base+"/pair"))return pair(body);
        if(method.equals("GET")&&path.equals(base+"/snapshot")){JSONObject c=access(screenId(),token);return snapshotFor(c.getString("role"),false);}
        if(method.equals("POST")&&path.equals(base+"/commands"))return command(token,key,body);
        throw new Failure(404,"Ruta Sports no encontrada");
    }
    private JSONObject pair(JSONObject b)throws Exception {
        if(data.getJSONArray("controls").length()>=3)throw new Failure(409,"Máximo tres controles por pantalla");
        if(pairExpiry<System.currentTimeMillis()||attempts>=5||pairCode.isEmpty())throw new Failure(403,"Código cerrado o vencido");
        if(!same(b.optString("code"),pairCode)){attempts++;throw new Failure(403,"Código incorrecto");}
        final String name=label(b.optString("name"));if(pairRole.equals("official")){JSONArray c=data.getJSONArray("controls");for(int i=0;i<c.length();i++)if(c.getJSONObject(i).getString("role").equals("official"))throw new Failure(409,"Ya existe anotador oficial");}
        JSONObject response=transaction(()->{String token=secret(),id=UUID.randomUUID().toString();data.getJSONArray("controls").put(new JSONObject().put("id",id).put("name",name).put("role",pairRole).put("tokenHash",sha(token)));event("sports.control_paired",id);return new JSONObject().put("controlToken",token).put("controlId",id).put("screenId",screenId()).put("role",pairRole);});pairExpiry=0;pairCode="";return response;
    }
    private JSONObject command(String token,String key,JSONObject b)throws Exception{
        JSONObject c=access(screenId(),token);String role=c.getString("role"),type=b.optString("type");int side=b.optInt("side",-1);
        if(!type.equals("point")&&!type.equals("undo"))throw new Failure(422,"Comando inválido");
        if(type.equals("point")&&(!(b.opt("side") instanceof Integer)||side<0||side>1))throw new Failure(422,"Lado inválido");
        if(!role.equals("official")&&(!type.equals("point")||side!=(role.equals("home")?0:1)))throw new Failure(403,"Acción fuera de permisos");
        if(key==null||!key.matches("[A-Za-z0-9_-]{8,100}"))throw new Failure(422,"Falta clave idempotente");
        String identity=c.getString("id")+":"+key;String fingerprint=sha(type+":"+side+":"+b.optString("expectedVersion"));JSONObject requests=data.getJSONObject("requests"),prior=requests.optJSONObject(identity);
        if(prior!=null){if(!same(prior.getString("fingerprint"),fingerprint))throw new Failure(409,"Clave usada para otra operación");return RacketRules.copy(prior.getJSONObject("response"));}
        Object version=b.opt("expectedVersion");if(!(version instanceof Number)||((Number)version).doubleValue()!=data.getLong("version"))throw new Failure(409,"El marcador cambió. Revisa antes de repetir.");
        JSONArray points=data.getJSONArray("points");if(!data.getBoolean("configured"))throw new Failure(409,"Configura el encuentro en el Fire");if(type.equals("undo")&&points.length()==0)throw new Failure(409,"Sin puntos que deshacer");if(points.length()>=20000&&type.equals("point"))throw new Failure(409,"Límite técnico de puntos");
        return transaction(()->{JSONArray next=new JSONArray();for(int i=0;i<points.length()-(type.equals("undo")?1:0);i++)next.put(points.getInt(i));if(type.equals("point"))next.put(side);RacketRules.replay(data.getJSONObject("format"),next,data.getInt("firstServer"));data.put("points",next).put("version",data.getLong("version")+1);event(type.equals("point")?"sports.point_awarded":"sports.point_undone",c.getString("id"));JSONObject response=new JSONObject().put("sequence",data.getLong("sequence")).put("event_id",data.getJSONArray("events").getJSONObject(data.getJSONArray("events").length()-1).getString("event_id")).put("match",match());requests.put(identity,new JSONObject().put("fingerprint",fingerprint).put("response",response));return response;});
    }
}
