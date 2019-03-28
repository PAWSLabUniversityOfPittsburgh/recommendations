package rec;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import rec.reactive.RecDB;

@WebServlet("/LogRecommendations")
public class LogRecommendations extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static String server = "http://adapt2.sis.pitt.edu";//Commented by @Jordan for debugging in localhost
	//private static String server = "http://localhost:8080";
	private static String conceptLevelsServiceURL = server + "/cbum/ReportManager";
	
	private static RecDB rec_db;

	public static void openDBConnections(String rec_dbstring, String rec_dbuser, String rec_dbpass) {
		rec_db = new RecDB(rec_dbstring, rec_dbuser, rec_dbpass);
		rec_db.openConnection();
	}

	public static void closeDBConnections() {
		if (rec_db != null)
			rec_db.closeConnection();
		rec_db = null;
	}

       
    public LogRecommendations() {
        super();
    }

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		RecConfigManager rec_cm = new RecConfigManager(this);
		AggregateConfigManager aggregate_cm = new AggregateConfigManager(this);
		UM2ConfigManager um2_cm = new UM2ConfigManager(this);
		
		try {
			//parse the json in the request
			InputStreamReader is = new InputStreamReader(request.getInputStream());
			JSONParser jsonParser = new JSONParser();
			JSONObject jsonObject = (JSONObject) jsonParser.parse(is);

			String usr = (String) jsonObject.get("usr"); // a string showing user, e.g. rec.test
			String grp = (String) jsonObject.get("grp"); // the class mnemonic, e.g IS172013Fall
			String sid = (String) jsonObject.get("sid"); // a string showing session id, e.g. HHH1
			String cid = (String) jsonObject.get("cid"); // a number showing course id, e.g. 1
			String recMethod = (String) jsonObject.get("recMethod");
			String recLogId = (String) jsonObject.get("logRecId");
			JSONArray recArray = (JSONArray) jsonObject.get("recommendations");
			openDBConnections(rec_cm.rec_dbstring,rec_cm.rec_dbuser,rec_cm.rec_dbpass);
			for (int i = 0; i < recArray.size(); i++) {
				JSONObject jsonobject = (JSONObject) recArray.get(i);
			    double recScore = (double) jsonobject.get("rec_score");
			    String actId = (String) jsonobject.get("id");
			    int isRec = Integer.parseInt((String)jsonobject.get("isRecommended"));
			    String topic = (String) jsonobject.get("topic");
			    rec_db.addRecommendation(recLogId,usr,grp,sid,topic,actId,recMethod,recScore,isRec);
			}
			//generate output for the post request
			String output = null;
			PrintWriter out = response.getWriter();
			out.print("true");
			
		}catch (ParseException e1) {
			e1.printStackTrace();
		}
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}
}
