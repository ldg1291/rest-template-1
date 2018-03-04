import java.util.Map;
import java.util.Map.Entry;

public class Util {
	public static final String CHARSET_UTF8 = "UTF-8";

	public static String mapToQueryString(Map<String, String> map) {
		StringBuilder string = new StringBuilder();

		if (map.size() > 0) {
			string.append("?");
		}

		for (Entry<String, String> entry : map.entrySet()) {
			string.append(entry.getKey());
			string.append("=");
			string.append(entry.getValue());
			string.append("&");
		}

		return string.toString();
	}

	public static String getValidCharset(final String charset) {
		if (charset != null && charset.length() > 0)
			return charset;
		else
			return CHARSET_UTF8;
	}
}
