package services.twocam.turnos;
import android.content.Context;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.net.InetAddress;
import org.json.JSONObject;
import services.twocam.turnoscore.LocalQueue;

/** Test APK only: uses the exact native Turnos core and HTTP server from its pinned repository checkout. */
public final class TurnosFixture {
    private LocalQueue queue;
    private LanServer server;
    private final AtomicFile disk;
    private String token;
    private int request;
    public final String base="http://127.0.0.1:19990";
    public TurnosFixture(Context context)throws Exception{
        disk=new AtomicFile(new File(context.getFilesDir(),"turnos-integration-fixture.json"));disk.delete();load(false);
        String code=queue.openPairing("supervisor",1).getString("code");
        token=queue.request("POST","/pair",null,null,new JSONObject().put("name","Test supervisor").put("code",code)).getString("token");
        start();
    }
    private void load(boolean restore)throws Exception{
        queue=new LocalQueue(restore?new String(disk.readFully(),"UTF-8"):null,json->{FileOutputStream out=null;try{out=disk.startWrite();out.write(json.getBytes("UTF-8"));disk.finishWrite(out);}catch(Exception error){if(out!=null)disk.failWrite(out);throw error;}});
    }
    public String viewerCode()throws Exception{return queue.openPairing("viewer",1).getString("code");}
    public String wrongCode()throws Exception{return queue.openPairing("operator",1).getString("code");}
    public JSONObject issue()throws Exception{return command("issue","");}
    public JSONObject call()throws Exception{return command("call","");}
    public JSONObject command(String type,String ticket)throws Exception{return queue.request("POST","/commands",token,"fixture-key-"+(++request),new JSONObject().put("type",type).put("counter",1).put("ticket_id",ticket));}
    public String id()throws Exception{return queue.id();}
    public void stop(){if(server!=null)server.close();server=null;}
    public void start()throws Exception{server=new LanServer(queue);server.start(InetAddress.getByName("127.0.0.1"),19990);}
    public void restore()throws Exception{stop();load(true);start();}
    public void revokeViewers()throws Exception{org.json.JSONArray list=queue.controls();for(int i=0;i<list.length();i++){JSONObject c=list.getJSONObject(i);if(c.getString("role").equals("viewer"))queue.revoke(c.getString("id"));}}
    public void close(){stop();disk.delete();}
}
