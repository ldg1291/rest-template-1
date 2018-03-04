import java.util.HashMap;

public class ClientFrame {


	public static void main(String[] args) {
		Client apiClient = new Client();

		HashMap<String, String> params = new HashMap<>();
		params.put("currency", "btc");

		try {
//			String result = apiClient.callApi("/ticker", params);
			String result = apiClient.callApi("/v2/account/balance", null);
			System.out.println("Result : " + result);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
