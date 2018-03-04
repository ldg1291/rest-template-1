import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.zip.GZIPInputStream;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.Proxy.Type.HTTP;

public class HttpRequest {

	private final URL url;
	private final String requestMethod;
	private HttpURLConnection connection = null;
	private String httpProxyHost;
	private int httpProxyPort;
	private static HttpRequest.ConnectionFactory CONNECTION_FACTORY = HttpRequest.ConnectionFactory.DEFAULT;
	public static final String CHARSET_UTF8 = "UTF-8";
	private boolean form;
	public static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";
	private RequestOutputStream output;
	public static final String PARAM_CHARSET = "charset";
	public static final String HEADER_CONTENT_TYPE = "Content-Type";
	private int bufferSize = 8192;
	public static final String METHOD_GET = "GET";
	private boolean multipart;
	private boolean ignoreCloseExceptions = true;
	private static final String CRLF = "\r\n";
	private static final String BOUNDARY = "00content0boundary00";
	private UploadProgress progress = UploadProgress.DEFAULT;
	private long totalWritten = 0;
	private long totalSize = -1;
	private boolean uncompress = false;
	public static final String ENCODING_GZIP = "gzip";
	public static final String HEADER_CONTENT_ENCODING = "Content-Encoding";
	public static final String HEADER_CONTENT_LENGTH = "Content-Length";

	public HttpRequest(final CharSequence url, final String method)
		throws HttpRequestException {
		try {
			this.url = new URL(url.toString());
		} catch (MalformedURLException e) {
			throw new HttpRequestException(e);
		}
		this.requestMethod = method;
	}

	public HttpRequest readTimeout(final int timeout) {
		getConnection().setReadTimeout(timeout);
		return this;
	}

	public HttpURLConnection getConnection() {
		if (connection == null)
			connection = createConnection();
		return connection;
	}

	private HttpURLConnection createConnection() {
		try {
			final HttpURLConnection connection;
			if (httpProxyHost != null)
				connection = CONNECTION_FACTORY.create(url, createProxy());
			else
				connection = CONNECTION_FACTORY.create(url);
			connection.setRequestMethod(requestMethod);
			return connection;
		} catch (IOException e) {
			throw new HttpRequestException(e);
		}
	}

	public interface ConnectionFactory {
		HttpURLConnection create(URL url) throws IOException;

		HttpURLConnection create(URL url, Proxy proxy) throws IOException;

		HttpRequest.ConnectionFactory DEFAULT = new HttpRequest.ConnectionFactory() {
			public HttpURLConnection create(URL url) throws IOException {
				return (HttpURLConnection) url.openConnection();
			}

			public HttpURLConnection create(URL url, Proxy proxy)
				throws IOException {
				return (HttpURLConnection) url.openConnection(proxy);
			}
		};
	}

	private Proxy createProxy() {
		return new Proxy(HTTP, new InetSocketAddress(httpProxyHost,
			httpProxyPort));
	}

	/**
	 headers()
	 */
	public HttpRequest headers(final Map<String, String> headers) {
		if (!headers.isEmpty())
			for (Map.Entry<String, String> header : headers.entrySet())
				header(header);
		return this;
	}

	public HttpRequest header(final Map.Entry<String, String> header) {
		return header(header.getKey(), header.getValue());
	}

	public HttpRequest header(final String name, final String value) {
		getConnection().setRequestProperty(name, value);
		return this;
	}

	/**
	 * form()
	 */
	public HttpRequest form(final Map<?, ?> values) throws HttpRequestException {
		return form(values, CHARSET_UTF8);
	}

	public HttpRequest form(final Map<?, ?> values, final String charset)
		throws HttpRequestException {
		if (!values.isEmpty())
			for (Map.Entry<?, ?> entry : values.entrySet())
				form(entry, charset);
		return this;
	}

	public HttpRequest form(final Map.Entry<?, ?> entry, final String charset)
		throws HttpRequestException {
		return form(entry.getKey(), entry.getValue(), charset);
	}

	public HttpRequest form(final Object name, final Object value,
		String charset) throws HttpRequestException {
		final boolean first = !form;
		if (first) {
			contentType(CONTENT_TYPE_FORM, charset);
			form = true;
		}
		charset = Util.getValidCharset(charset);
		try {
			openOutput();
			if (!first)
				output.write('&');
			output.write(URLEncoder.encode(name.toString(), charset));
			output.write('=');
			if (value != null)
				output.write(URLEncoder.encode(value.toString(), charset));
		} catch (IOException e) {
			throw new HttpRequestException(e);
		}
		return this;
	}

	public HttpRequest contentType(final String contentType,
		final String charset) {
		if (charset != null && charset.length() > 0) {
			final String separator = "; " + PARAM_CHARSET + '=';
			return header(HEADER_CONTENT_TYPE, contentType + separator
				+ charset);
		} else
			return header(HEADER_CONTENT_TYPE, contentType);
	}

	protected HttpRequest openOutput() throws IOException {
		if (output != null)
			return this;
		getConnection().setDoOutput(true);
		final String charset = getParam(
			getConnection().getRequestProperty(HEADER_CONTENT_TYPE),
			PARAM_CHARSET);
		output = new RequestOutputStream(getConnection().getOutputStream(),
			charset, bufferSize);
		return this;
	}

	protected String getParam(final String value, final String paramName) {
		if (value == null || value.length() == 0)
			return null;

		final int length = value.length();
		int start = value.indexOf(';') + 1;
		if (start == 0 || start == length)
			return null;

		int end = value.indexOf(';', start);
		if (end == -1)
			end = length;

		while (start < end) {
			int nameEnd = value.indexOf('=', start);
			if (nameEnd != -1 && nameEnd < end
				&& paramName.equals(value.substring(start, nameEnd).trim())) {
				String paramValue = value.substring(nameEnd + 1, end).trim();
				int valueLength = paramValue.length();
				if (valueLength != 0)
					if (valueLength > 2 && '"' == paramValue.charAt(0)
						&& '"' == paramValue.charAt(valueLength - 1))
						return paramValue.substring(1, valueLength - 1);
					else
						return paramValue;
			}

			start = end + 1;
			end = value.indexOf(';', start);
			if (end == -1)
				end = length;
		}

		return null;
	}

	/**
	 * GET
	 */
	public static HttpRequest get(final CharSequence url)
		throws HttpRequestException {
		return new HttpRequest(url, METHOD_GET);
	}

	/**
	 * OK
	 */
	public boolean ok() throws HttpRequestException {
		return HTTP_OK == code();
	}

	protected HttpRequest closeOutput() throws IOException {
		progress(null);
		if (output == null)
			return this;
		if (multipart)
			output.write(CRLF + "--" + BOUNDARY + "--" + CRLF);
		if (ignoreCloseExceptions)
			try {
				output.close();
			} catch (IOException ignored) {
				// Ignored
			}
		else
			output.close();
		output = null;
		return this;
	}

	public HttpRequest progress(final UploadProgress callback) {
		if (callback == null)
			progress = UploadProgress.DEFAULT;
		else
			progress = callback;
		return this;
	}

	/**
	 * body()
	 */
	public String body() throws HttpRequestException {
		return body(charset());
	}

	public String body(final String charset) throws HttpRequestException {
		final ByteArrayOutputStream output = byteStream();
		try {
			copy(buffer(), output);
			return output.toString(Util.getValidCharset(charset));
		} catch (IOException e) {
			throw new HttpRequestException(e);
		}
	}

	protected HttpRequest copy(final InputStream input,
		final OutputStream output) throws IOException {
		return new HttpRequest.CloseOperation<HttpRequest>(input, ignoreCloseExceptions) {

			@Override
			public HttpRequest run() throws IOException {
				final byte[] buffer = new byte[bufferSize];
				int read;
				while ((read = input.read(buffer)) != -1) {
					output.write(buffer, 0, read);
					totalWritten += read;
					progress.onUpload(totalWritten, totalSize);
				}
				return HttpRequest.this;
			}
		}.call();
	}

	public BufferedInputStream buffer() throws HttpRequestException {
		return new BufferedInputStream(stream(), bufferSize);
	}

	public InputStream stream() throws HttpRequestException {
		InputStream stream;
		if (code() < HTTP_BAD_REQUEST)
			try {
				stream = getConnection().getInputStream();
			} catch (IOException e) {
				throw new HttpRequestException(e);
			}
		else {
			stream = getConnection().getErrorStream();
			if (stream == null)
				try {
					stream = getConnection().getInputStream();
				} catch (IOException e) {
					if (contentLength() > 0)
						throw new HttpRequestException(e);
					else
						stream = new ByteArrayInputStream(new byte[0]);
				}
		}

		if (!uncompress || !ENCODING_GZIP.equals(contentEncoding()))
			return stream;
		else
			try {
				return new GZIPInputStream(stream);
			} catch (IOException e) {
				throw new HttpRequestException(e);
			}
	}

	public String contentEncoding() {
		return header(HEADER_CONTENT_ENCODING);
	}

	public int contentLength() {
		return intHeader(HEADER_CONTENT_LENGTH);
	}

	public int intHeader(final String name) throws HttpRequestException {
		return intHeader(name, -1);
	}

	public int intHeader(final String name, final int defaultValue)
		throws HttpRequestException {
		closeOutputQuietly();
		return getConnection().getHeaderFieldInt(name, defaultValue);
	}

	protected ByteArrayOutputStream byteStream() {
		final int size = contentLength();
		if (size > 0)
			return new ByteArrayOutputStream(size);
		else
			return new ByteArrayOutputStream();
	}

	public String charset() {
		return parameter(HEADER_CONTENT_TYPE, PARAM_CHARSET);
	}

	public String parameter(final String headerName, final String paramName) {
		return getParam(header(headerName), paramName);
	}

	public String header(final String name) throws HttpRequestException {
		closeOutputQuietly();
		return getConnection().getHeaderField(name);
	}

	protected HttpRequest closeOutputQuietly() throws HttpRequestException {
		try {
			return closeOutput();
		} catch (IOException e) {
			throw new HttpRequestException(e);
		}
	}

	/**
	 * code()
	 */
	public int code() throws HttpRequestException {
		try {
			closeOutput();
			return getConnection().getResponseCode();
		} catch (IOException e) {
			throw new HttpRequestException(e);
		}
	}

	/**
	 * disconnect()
	 */
	public HttpRequest disconnect() {
		getConnection().disconnect();
		return this;
	}

	protected static abstract class Operation<V> implements Callable<V> {

		protected abstract V run() throws HttpRequestException, IOException;

		protected abstract void done() throws IOException;

		public V call() throws HttpRequestException {
			boolean thrown = false;
			try {
				return run();
			} catch (HttpRequestException e) {
				thrown = true;
				throw e;
			} catch (IOException e) {
				thrown = true;
				throw new HttpRequestException(e);
			} finally {
				try {
					done();
				} catch (IOException e) {
					if (!thrown)
						throw new HttpRequestException(e);
				}
			}
		}
	}

	protected static abstract class CloseOperation<V> extends HttpRequest.Operation<V> {

		private final Closeable closeable;

		private final boolean ignoreCloseExceptions;

		protected CloseOperation(final Closeable closeable,
			final boolean ignoreCloseExceptions) {
			this.closeable = closeable;
			this.ignoreCloseExceptions = ignoreCloseExceptions;
		}

		@Override
		protected void done() throws IOException {
			if (closeable instanceof Flushable)
				((Flushable) closeable).flush();
			if (ignoreCloseExceptions)
				try {
					closeable.close();
				} catch (IOException e) {
					// Ignored
				}
			else
				closeable.close();
		}
	}
}
