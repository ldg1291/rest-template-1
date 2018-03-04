import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.codehaus.jackson.map.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

public class Client {
	protected String api_url = "https://api.coinone.co.kr";
	protected String api_key = "";
	protected String api_secret = "";

	private static final String DEFAULT_ENCODING = "UTF-8";
	private static final String HMAC_SHA512 = "HmacSHA512";

	private String usecTime() {
		return String.valueOf(System.currentTimeMillis());
	}

	private String request(String strHost, HashMap<String, String> rgParams, HashMap<String, String> httpHeaders) {
		String response = "";
		HttpRequest request = null;

		if (httpHeaders != null) {
			request = new HttpRequest(strHost, "POST");
			request.readTimeout(10000);

			if (httpHeaders != null && !httpHeaders.isEmpty()) {
				request.headers(httpHeaders);
			}
			if (rgParams != null && !rgParams.isEmpty()) {
				request.form(rgParams);
			}
		} else {
			request = HttpRequest.get(strHost
				+ Util.mapToQueryString(rgParams));
			request.readTimeout(10000);
		}

		if (request.ok()) {
			Document doc = Jsoup.parse(request.body());
			Elements elements = doc.select("pre");
			response = elements.get(1).ownText();
		} else {
			response = "error : " + request.code() + ", message : "
				+ request.body();
		}
		request.disconnect();

		return response;
	}

	public static byte[] hmacSha512(String value, String key){
		try {
			SecretKeySpec keySpec = new SecretKeySpec(
				key.getBytes(DEFAULT_ENCODING),
				HMAC_SHA512);

			Mac mac = Mac.getInstance(HMAC_SHA512);
			mac.init(keySpec);

			final byte[] macData = mac.doFinal( value.getBytes( ) );
			byte[] hex = new Hex().encode( macData );
			return hex;

		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		} catch (InvalidKeyException e) {
			throw new RuntimeException(e);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	public static String asHex(byte[] bytes){
		return new String(Base64.encodeBase64(bytes));
	}

	private HashMap<String, String> getHttpHeaders() {
		HashMap<String, String> tmp = new HashMap<>();
		HashMap<String, String> result = new HashMap<>();
		String nNonce = usecTime();
		String payload = "";
		tmp.put("access_token", api_key);
		tmp.put("nonce", nNonce);

		try {
			String encodedStr = new ObjectMapper().writeValueAsString(tmp);
			payload = asHex(encodedStr.getBytes());
		} catch (IOException e) {
			e.printStackTrace();
		}
		result.put("Content-type", "application/json");
		result.put("X-COINONE-PAYLOAD", payload);

		String signature = new String(hmacSha512(payload, api_secret.toUpperCase()));
		result.put("X-COINONE-SIGNATURE", signature.toLowerCase());

		return result;
	}

	public String callApi(String uri, HashMap<String, String> params) {
		String rgResultDecode = "";

		String api_host = api_url + uri;
		HashMap<String, String> httpHeaders = params == null ? getHttpHeaders() : null;
		rgResultDecode = request(api_host, params, httpHeaders);

		if (!rgResultDecode.startsWith("error")) {
			// json 파싱
			HashMap<String, String> result;
			try {
				result = new ObjectMapper().readValue(rgResultDecode,
					HashMap.class);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return rgResultDecode;
	}
}
