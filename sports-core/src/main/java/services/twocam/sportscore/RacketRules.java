package services.twocam.sportscore;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Componente Sports portable: la pantalla y el control no calculan tanteos. */
public final class RacketRules {
    private RacketRules() { }
    public static JSONObject copy(JSONObject o) { try { return new JSONObject(o.toString()); } catch(Exception e) { throw new IllegalArgumentException("JSON inválido",e); } }
    private static int number(JSONObject o,String key,int min,int max) throws Exception {
        Object v=o.get(key);if(!(v instanceof Number))throw new Exception("Número inválido: "+key);
        double n=((Number)v).doubleValue();if(n!=Math.floor(n)||n<min||n>max)throw new Exception("Valor inválido: "+key);return (int)n;
    }
    public static JSONObject validate(JSONObject input) throws Exception {
        JSONObject f=copy(input);String d=f.getString("discipline"),deuce=f.getString("deuce");
        if(!d.equals("tennis")&&!d.equals("padel"))throw new Exception("Disciplina inválida");
        int best=number(f,"bestOf",1,5);if(best!=1&&best!=3&&best!=5)throw new Exception("Usa mejor de 1, 3 o 5 sets");
        if(!deuce.equals("advantage")&&!deuce.equals("golden")&&!deuce.equals("star"))throw new Exception("Regla de iguales inválida");
        if(d.equals("tennis")&&deuce.equals("star"))throw new Exception("Star Point solo para pádel");
        for(String field:new String[]{"set","decidingSet"}){
            JSONObject r=f.getJSONObject(field);String kind=r.getString("kind");
            if(kind.equals("match_tiebreak")){if(!field.equals("decidingSet")||best==1)throw new Exception("Supertiebreak decisivo inválido");number(r,"points",2,50);}
            else if(kind.equals("games")){int games=number(r,"games",2,20);number(r,"tiebreakPoints",2,50);if(!r.has("tiebreakAt"))throw new Exception("Falta regla de tiebreak");if(!r.isNull("tiebreakAt"))number(r,"tiebreakAt",games-1,games);}
            else throw new Exception("Tipo de set inválido");
        }
        return f;
    }
    public static JSONObject standard(String discipline,int bestOf,String deuce,int games,boolean superTie) throws Exception {
        JSONObject set=new JSONObject().put("kind","games").put("games",games).put("tiebreakAt",games).put("tiebreakPoints",7);
        return validate(new JSONObject().put("discipline",discipline).put("bestOf",bestOf).put("deuce",deuce).put("set",set).put("decidingSet",superTie?new JSONObject().put("kind","match_tiebreak").put("points",10):copy(set)));
    }
    private static final class State {
        int[] points={0,0},games={0,0},sets={0,0};int server,tieFirst=-1;Integer winner;String phase="game";List<JSONObject> complete=new ArrayList<>();
    }
    private static JSONObject rule(State s,JSONObject f)throws Exception{return f.getJSONObject(s.complete.size()==f.getInt("bestOf")-1?"decidingSet":"set");}
    private static JSONArray array(int[] a){return new JSONArray().put(a[0]).put(a[1]);}
    private static void complete(State s,JSONObject f,int side,String kind,int[] tie)throws Exception{
        s.complete.add(new JSONObject().put("games",array(s.games)).put("kind",kind).put("tiePoints",tie==null?JSONObject.NULL:array(tie)).put("winner",side));s.sets[side]++;s.points=new int[]{0,0};s.games=new int[]{0,0};s.tieFirst=-1;
        if(s.sets[side]>=f.getInt("bestOf")/2+1){s.phase="finished";s.winner=side;return;}
        s.phase=rule(s,f).getString("kind").equals("match_tiebreak")?"match_tiebreak":"game";if(s.phase.equals("match_tiebreak"))s.tieFirst=s.server;
    }
    private static void point(State s,JSONObject f,int side)throws Exception{
        if(side!=0&&side!=1)throw new Exception("Lado inválido");if(s.winner!=null)throw new Exception("Encuentro terminado");int other=1-side;s.points[side]++;
        if(s.phase.equals("game")){
            String deuce=f.getString("deuce");boolean decisive=deuce.equals("golden")&&s.points[other]>=3||deuce.equals("star")&&s.points[other]>=5;
            if(s.points[side]>=4&&(s.points[side]-s.points[other]>=2||decisive&&s.points[side]>s.points[other])){
                s.games[side]++;s.points=new int[]{0,0};s.server=1-s.server;JSONObject r=rule(s,f);
                if(s.games[side]>=r.getInt("games")&&s.games[side]-s.games[other]>=2)complete(s,f,side,"games",null);
                else if(!r.isNull("tiebreakAt")&&s.games[0]==r.getInt("tiebreakAt")&&s.games[1]==r.getInt("tiebreakAt")){s.phase="tiebreak";s.tieFirst=s.server;}
            }
        }else{
            JSONObject r=rule(s,f);int target=r.getInt(s.phase.equals("match_tiebreak")?"points":"tiebreakPoints");
            if(s.points[side]>=target&&s.points[side]-s.points[other]>=2){String kind=s.phase;int[] tie=s.points.clone();s.server=1-s.tieFirst;if(kind.equals("tiebreak"))s.games[side]++;complete(s,f,side,kind,tie);}
            else s.server=((s.points[0]+s.points[1]+1)/2)%2==0?s.tieFirst:1-s.tieFirst;
        }
    }
    public static JSONObject replay(JSONObject format,JSONArray points,int firstServer)throws Exception{
        JSONObject f=validate(format);if(firstServer!=0&&firstServer!=1)throw new Exception("Primer saque inválido");State s=new State();s.server=firstServer;
        for(int i=0;i<points.length();i++){Object p=points.get(i);if(!(p instanceof Integer))throw new Exception("Punto inválido");point(s,f,(Integer)p);}
        JSONArray completed=new JSONArray();for(JSONObject c:s.complete)completed.put(c);
        JSONArray labels;
        if(!s.phase.equals("game"))labels=new JSONArray().put(String.valueOf(s.points[0])).put(String.valueOf(s.points[1]));
        else if(s.points[0]>=3&&s.points[1]>=3)labels=new JSONArray().put(s.points[0]>s.points[1]?"AD":"40").put(s.points[1]>s.points[0]?"AD":"40");
        else{String[] values={"0","15","30","40"};labels=new JSONArray().put(values[s.points[0]]).put(values[s.points[1]]);}
        boolean decisive=s.phase.equals("game")&&s.points[0]==s.points[1]&&(f.getString("deuce").equals("golden")&&s.points[0]==3||f.getString("deuce").equals("star")&&s.points[0]==5);
        JSONObject state=new JSONObject().put("points",array(s.points)).put("games",array(s.games)).put("setsWon",array(s.sets)).put("completedSets",completed).put("phase",s.phase).put("server",s.server).put("tiebreakFirstServer",s.tieFirst<0?JSONObject.NULL:s.tieFirst).put("winner",s.winner==null?JSONObject.NULL:s.winner);
        return new JSONObject().put("state",state).put("labels",labels).put("decidingPoint",decisive);
    }
}
