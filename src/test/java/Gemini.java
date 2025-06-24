import com.gdxsoft.ai.providers.gemini.request.Request;
import com.gdxsoft.ai.providers.gemini.response.ApiResponse;

public class Gemini {

	public static void main(String[] args) {
		
		String endPoint = "https://gdx/api/gemini/";
		String model = "gemini-2.5-flash:generateContent";
		
		Request r = new Request(endPoint, null, model);
		
		ApiResponse resp = r.doRequstSimple("介绍下你自己");
		
		System.out.println(resp.getResponseText());
		System.out.println(resp.getResponseCodes());
		
	}
}
