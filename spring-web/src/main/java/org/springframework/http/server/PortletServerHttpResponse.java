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
import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import javax.portlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * {@link org.springframework.http.server.ServerHttpResponse} implementation that is based on a {@link javax.portlet.PortletResponse}.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @since 3.0
 */
public class PortletServerHttpResponse implements ServerHttpResponse {

    private static final String UNSUPPORTED_OPERATION = "PortletResponse does not support the operation: %s";

	private final PortletResponse portletResponse;

	private final HttpHeaders headers = new HttpHeaders();

	private boolean headersWritten = false;

	/**
	 * Construct a new instance of the PortletServerHttpResponse based on the given {@link javax.portlet.PortletResponse}.
	 * @param portletResponse the servlet response
	 */
	public PortletServerHttpResponse(PortletResponse portletResponse) {
		Assert.notNull(portletResponse, "'portletResponse' must not be null");
		this.portletResponse = portletResponse;
	}


	/**
	 * Return the {@code PortletResponse} this object is based on.
	 */
	public PortletResponse getPortletResponse() {
		return this.portletResponse;
	}

	@Override
	public void setStatusCode(HttpStatus status) {
	    if (this.portletResponse instanceof ResourceResponse) {
            this.portletResponse.setProperty(ResourceResponse.HTTP_STATUS_CODE, String.valueOf(status.value()));
        }
	}

	@Override
	public HttpHeaders getHeaders() {
		return (this.headersWritten ? HttpHeaders.readOnlyHttpHeaders(this.headers) : this.headers);
	}

	@Override
	public OutputStream getBody() throws IOException {
		writeHeaders();

        if (portletResponse instanceof MimeResponse) {
            return ((MimeResponse)this.portletResponse).getPortletOutputStream();
        }

        throw new UnsupportedOperationException(String.format(UNSUPPORTED_OPERATION, "getBody"));
	}

	@Override
	public void flush() throws IOException {
		writeHeaders();

        if (portletResponse instanceof MimeResponse) {
            ((MimeResponse) this.portletResponse).flushBuffer();
        }
	}

	@Override
	public void close() {
		writeHeaders();
	}

	private void writeHeaders() {
		if (!this.headersWritten) {

            for (Map.Entry<String, List<String>> entry : this.headers.entrySet()) {
				String headerName = entry.getKey();
				for (String headerValue : entry.getValue()) {
					if (headerName.equals(HttpHeaders.CONTENT_LENGTH)) {
                        if (portletResponse instanceof ResourceResponse) {
                            ResourceResponse resourceResponse = (ResourceResponse) portletResponse;
                            resourceResponse.setContentLength(Integer.valueOf(headerValue));

                            continue;
                        }
                    }
                    this.portletResponse.setProperty(headerName, headerValue);
				}
			}

            if (portletResponse instanceof MimeResponse) {
                MimeResponse mimeResponse = (MimeResponse) portletResponse;

                if (mimeResponse.getContentType() == null && this.headers.getContentType() != null) {
                    mimeResponse.setContentType(this.headers.getContentType().toString());
                }
            }

			this.headersWritten = true;
		}
	}
}
