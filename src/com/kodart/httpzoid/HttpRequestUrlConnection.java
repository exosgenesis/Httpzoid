package com.kodart.httpzoid;

import android.os.AsyncTask;
import com.google.gson.Gson;

import java.io.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * (c) Artur Sharipov
 */
public class HttpRequestUrlConnection implements HttpRequest {

    private static final int DEFAULT_TIMEOUT = 10000;
    private WeakReference<ResponseHandler> handlerRef = new WeakReference<ResponseHandler>(new ResponseHandler());

    private Gson mapper = new Gson();
    private Proxy proxy = Proxy.NO_PROXY;

    private URL url;
    private String method;
    private int timeout = DEFAULT_TIMEOUT;

    private Map<String, String> headers = new HashMap<String, String>();
    private Class type;
    private Object data;

    public HttpRequestUrlConnection(URL url, String method) {
        this.url = url;
        this.method = method;
    }

    @Override
    public HttpRequest data(Object data) throws IOException {
        this.data = data;
        return this;
    }

    @Override
    public HttpRequest headers(Map<String, String> headers) {
        this.headers = headers;
        return this;
    }

    private Class findType(ResponseHandler handler) {
        Method[] methods = handler.getClass().getMethods();
        for(Method method : methods) {
            if (method.getName().equals("success")) {
                Class param = method.getParameterTypes()[0];
                if (!param.equals(Object.class))
                    return param;
            }
        }
        return Object.class;
    }

    @Override
    public HttpRequest handler(ResponseHandler handler) {
        handlerRef = new WeakReference<ResponseHandler>(handler);
        type = findType(handler);
        return this;
    }

    @Override
    public HttpRequest timeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    @Override
    public HttpRequest proxy(Proxy proxy) {
        this.proxy = proxy;
        return this;
    }

    @Override
    public void execute() throws IOException {
        new AsyncTask<Void, Void, HttpResponse>() {
            @Override
            protected HttpResponse doInBackground(Void... params) {
                HttpURLConnection connection = null;
                try {
                    connection = (HttpURLConnection)url.openConnection(proxy);
                    init(connection);
                    sendData(connection);
                    return new HttpResponse(readData(connection), connection);
                }
                catch (IOException e) {
                    return new HttpResponse();
                }
                finally {
                    if (connection != null)
                        connection.disconnect();
                }
            }

            @Override
            protected void onPostExecute(HttpResponse response) {
                ResponseHandler handler = handlerRef.get();
                if (handler == null)
                    return;

                handler.complete();
            }

        }.execute();
    }

    private Object readData(HttpURLConnection connection) throws IOException {
        InputStream input = new BufferedInputStream(connection.getInputStream());
        validate(connection);

        if (InputStream.class.isAssignableFrom(type))
            return input;

        return mapper.fromJson(new InputStreamReader(input), type);
    }

    private void sendData(HttpURLConnection connection) throws IOException {
        if (data == null)
            return;

        connection.setDoOutput(true);
        OutputStream outputStream = connection.getOutputStream();
        try {
            if (data instanceof InputStream) {
                byte[] buffer = new byte[64 * 1024];
                int bytes;
                while ((bytes = ((InputStream)data).read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytes);
                }
            } else {
                OutputStreamWriter writer = new OutputStreamWriter(outputStream);
                mapper.toJson(data, writer);
            }
        }
        finally {
            outputStream.flush();
            outputStream.close();
        }
    }

    private void init(HttpURLConnection connection) throws ProtocolException {
        connection.setRequestMethod(method);
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            connection.setRequestProperty(entry.getKey(), entry.getValue());
        }
    }

    private void validate(HttpURLConnection connection) {
        if (!url.getHost().equals(connection.getURL().getHost())) {
//            throw new NetworkAuthenticationException();
        }
    }
}
