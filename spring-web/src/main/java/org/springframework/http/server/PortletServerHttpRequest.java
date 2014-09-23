/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.http.server;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;

import javax.portlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.security.Principal;
import java.util.*;

/**
 * {@link org.springframework.http.server.ServerHttpRequest} implementation that is based on a {@link javax.portlet.PortletRequest}.
 *
 * @author Nick Cavallo
 * @since 4.1
 */
public class PortletServerHttpRequest implements ServerHttpRequest {

	protected static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";

	protected static final String FORM_CHARSET = "UTF-8";

    private static final String UNSUPPORTED_OPERATION = "PortletRequest does not support the operation: %s";

    private static final String METHOD_POST = "POST";

	private final PortletRequest portletRequest;

	private HttpHeaders headers;

	/**
	 * Construct a new instance of the PortletServerHttpRequest based on the given {@link javax.portlet.PortletRequest}.
	 * @param portletRequest the servlet request
	 */
	public PortletServerHttpRequest(PortletRequest portletRequest) {
		Assert.notNull(portletRequest, "portletRequest must not be null");
		this.portletRequest = portletRequest;
	}


	/**
	 * Returns the {@code PortletRequest} this object is based on.
	 */
	public PortletRequest getPortletRequest() {
		return this.portletRequest;
	}

	@Override
	public HttpMethod getMethod() {
        String httpMethod = null;

        if (this.portletRequest instanceof ClientDataRequest) {
            httpMethod = ((ClientDataRequest)this.portletRequest).getMethod();
        }

        if (this.portletRequest instanceof EventRequest) {
            httpMethod = ((EventRequest)this.portletRequest).getMethod();
        }

        if (httpMethod != null) {
            return HttpMethod.valueOf(httpMethod);
        }

        throw new UnsupportedOperationException(String.format(UNSUPPORTED_OPERATION, "getMethod"));
	}

	@Override
	public URI getURI() {
        throw new UnsupportedOperationException(String.format(UNSUPPORTED_OPERATION, "getURI"));
	}

	@Override
	public HttpHeaders getHeaders() {
		if (this.headers == null) {
			this.headers = new HttpHeaders();
			for (Enumeration<?> headerNames = this.portletRequest.getPropertyNames(); headerNames.hasMoreElements();) {
				String headerName = (String) headerNames.nextElement();
				for (Enumeration<?> headerValues = this.portletRequest.getProperties(headerName);
						headerValues.hasMoreElements();) {
					String headerValue = (String) headerValues.nextElement();
					this.headers.add(headerName, headerValue);
				}
			}

            String clientDataContentType = null;
            String characterEncoding = null;
            int contentLength = -1;
            if (this.portletRequest instanceof ClientDataRequest) {
                clientDataContentType = ((ClientDataRequest) this.portletRequest).getContentType();
                characterEncoding = ((ClientDataRequest)this.portletRequest).getCharacterEncoding();
                contentLength = ((ClientDataRequest)this.portletRequest).getContentLength();
            }

			if (this.headers.getContentType() == null && clientDataContentType != null) {
				MediaType contentType = MediaType.parseMediaType(clientDataContentType);
				this.headers.setContentType(contentType);
			}
			if (this.headers.getContentType() != null && this.headers.getContentType().getCharSet() == null &&
					characterEncoding != null) {
				MediaType oldContentType = this.headers.getContentType();
				Charset charSet = Charset.forName(characterEncoding);
				Map<String, String> params = new HashMap<String, String>(oldContentType.getParameters());
				params.put("charset", charSet.toString());
				MediaType newContentType = new MediaType(oldContentType.getType(), oldContentType.getSubtype(), params);
				this.headers.setContentType(newContentType);
			}
			if (this.headers.getContentLength() == -1 && contentLength != -1) {
				this.headers.setContentLength(contentLength);
			}
		}
		return this.headers;
	}

	@Override
	public Principal getPrincipal() {
		return this.portletRequest.getUserPrincipal();
	}

	@Override
	public InetSocketAddress getLocalAddress() {
		throw new UnsupportedOperationException(String.format(UNSUPPORTED_OPERATION, "getLocalAddress"));
	}

	@Override
	public InetSocketAddress getRemoteAddress() {
		throw new UnsupportedOperationException(String.format(UNSUPPORTED_OPERATION, "getRemoteAddress"));
	}

    @Override
    public ServerHttpAsyncRequestControl getAsyncRequestControl(ServerHttpResponse response) {
        throw new UnsupportedOperationException(String.format(UNSUPPORTED_OPERATION, "getAsyncRequestControl"));
    }

    @Override
	public InputStream getBody() throws IOException {
		if (!(this.portletRequest instanceof ClientDataRequest)) {
            throw new UnsupportedOperationException(String.format(UNSUPPORTED_OPERATION, "getBody"));
        }

        ClientDataRequest clientDataRequest = (ClientDataRequest)this.portletRequest;

        if (isFormPost(clientDataRequest)) {
			return getBodyFromServletRequestParameters(clientDataRequest);
		}
		else {
			return clientDataRequest.getPortletInputStream();
		}
	}

	private boolean isFormPost(ClientDataRequest request) {
		return (request.getContentType() != null && request.getContentType().contains(FORM_CONTENT_TYPE) &&
				METHOD_POST.equalsIgnoreCase(request.getMethod()));
	}

	/**
	 * Use {@link javax.servlet.ServletRequest#getParameterMap()} to reconstruct the
	 * body of a form 'POST' providing a predictable outcome as opposed to reading
	 * from the body, which can fail if any other code has used ServletRequest
	 * to access a parameter thus causing the input stream to be "consumed".
	 */
	private InputStream getBodyFromServletRequestParameters(ClientDataRequest request) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);
		Writer writer = new OutputStreamWriter(bos, FORM_CHARSET);

		Map<String, String[]> form = request.getParameterMap();
		for (Iterator<String> nameIterator = form.keySet().iterator(); nameIterator.hasNext();) {
			String name = nameIterator.next();
			List<String> values = Arrays.asList(form.get(name));
			for (Iterator<String> valueIterator = values.iterator(); valueIterator.hasNext();) {
				String value = valueIterator.next();
				writer.write(URLEncoder.encode(name, FORM_CHARSET));
				if (value != null) {
					writer.write('=');
					writer.write(URLEncoder.encode(value, FORM_CHARSET));
					if (valueIterator.hasNext()) {
						writer.write('&');
					}
				}
			}
			if (nameIterator.hasNext()) {
				writer.append('&');
			}
		}
		writer.flush();

		return new ByteArrayInputStream(bos.toByteArray());
	}

}
