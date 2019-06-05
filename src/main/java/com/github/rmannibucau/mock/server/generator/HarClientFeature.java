package com.github.rmannibucau.mock.server.generator;

import static java.util.Arrays.asList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.RuntimeType.CLIENT;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.AbstractMap;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import javax.annotation.Priority;
import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.Priorities;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Providers;

import com.github.rmannibucau.mock.server.generator.model.Har;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@ConstrainedTo(CLIENT)
public class HarClientFeature implements Feature {
    @Getter
    private final Har har = new Har();

    @Context
    private Providers providers;

    @Override
    public boolean configure(final FeatureContext context) {
        context.register(new ResponseFilter(har, providers));
        return true;
    }

    @RequiredArgsConstructor
    @Priority(Priorities.ENTITY_CODER)
    public static class ResponseFilter implements ClientResponseFilter, ClientRequestFilter {
        private final Har container;
        private final Providers providers;

        @Override
        public void filter(final ClientRequestContext requestContext) throws IOException {
            final CapturingStream stream = new CapturingStream(requestContext.getEntityStream());
            requestContext.setProperty(CapturingStream.class.getName(), stream);
            requestContext.setEntityStream(stream);
        }

        @Override
        public void filter(final ClientRequestContext requestContext,
                           final ClientResponseContext responseContext) {
            final Har.Entry entry = new Har.Entry();
            entry.setRequest(createRequest(requestContext));
            entry.setResponse(createResponse(responseContext, CapturingStream.class.cast(requestContext.getProperty(CapturingStream.class.getName()))));
            addEntry(entry);
        }

        private Har.Response createResponse(final ClientResponseContext responseContext,
                                            final CapturingStream output) {
            final Har.Response response = new Har.Response();

            if (output != null) {
                final byte[] body = output.buffer.toByteArray();
                response.setBodySize(body.length);

                final Har.Content content = new Har.Content();
                if (responseContext.getMediaType() != null) {
                    content.setMimeType(responseContext.getMediaType().toString());
                    if (asList(MediaType.APPLICATION_OCTET_STREAM_TYPE, MediaType.MULTIPART_FORM_DATA_TYPE).contains(responseContext.getMediaType())) {
                        content.setEncoding("base64");
                        content.setText(Base64.getEncoder().encodeToString(body));
                    }
                }
                if (content.getText() == null) {
                    content.setText(new String(body, StandardCharsets.UTF_8 /*todo: read from the response*/));
                }
                content.setSize(body.length);
                response.setContent(content);
            } else {
                response.setBodySize(-1);
            }

            response.setStatus(responseContext.getStatus());
            if (responseContext.getStatus() == Response.Status.TEMPORARY_REDIRECT.getStatusCode()
                    || responseContext.getStatus() == HttpURLConnection.HTTP_MOVED_PERM
                    || responseContext.getStatus() == HttpURLConnection.HTTP_MOVED_TEMP) {
                response.setRedirectURL(responseContext.getHeaderString("Location"));
            }
            response.setHeaders(responseContext.getHeaders().entrySet().stream()
                    .map(it -> new AbstractMap.SimpleEntry<>(it.getKey(), it.getValue().stream().map(Object.class::cast).collect(toList())))
                    .map(this::mapHeader)
                    .collect(toList()));
            response.setHeadersSize(response.getHeaders().stream().mapToLong(it -> it.getName().getBytes(StandardCharsets.UTF_8).length + it.getValue().getBytes(StandardCharsets.UTF_8).length + ": \r\n".length()).sum());
            ofNullable(responseContext.getCookies())
                    .map(Map::values)
                    .map(this::toCookies)
                    .filter(it -> !it.isEmpty())
                    .ifPresent(response::setCookies);

            return response;
        }

        private Har.Request createRequest(final ClientRequestContext requestContext) {
            final Har.Request request = new Har.Request();

            if (requestContext.hasEntity() && InputStream.class.isInstance(requestContext.getEntity())) {
                final InputStream entityStream = InputStream.class.cast(requestContext.getEntity());
                final byte[] body = read(entityStream,
                        ofNullable(requestContext.getHeaders().getFirst("Content-Length"))
                                .map(String::valueOf)
                                .map(Integer::parseInt)
                                .orElse(1));
                requestContext.setEntity(new ByteArrayInputStream(body));
                request.setBodySize(body.length);

                final Har.PostData postData = new Har.PostData();
                if (requestContext.getMediaType() != null) {
                    postData.setMimeType(requestContext.getMediaType().toString());
                    if (requestContext.getMediaType() == MediaType.MULTIPART_FORM_DATA_TYPE) {
                        postData.setParams(mapParams(body, requestContext.getMediaType(), requestContext.getStringHeaders()));
                    } else {
                        postData.setText(new String(body, StandardCharsets.UTF_8 /*todo: read from the request*/));
                    }
                }
                request.setPostData(postData);
            } else {
                request.setBodySize(-1);
            }

            request.setMethod(requestContext.getMethod());
            request.setQueryString(mapQuery(requestContext.getUri().getQuery()));
            request.setUrl(mapUrl(requestContext.getUri()));
            request.setHeaders(requestContext.getHeaders().entrySet().stream().map(this::mapHeader).collect(toList()));
            ofNullable(requestContext.getCookies())
                    .map(Map::values)
                    .map(this::toCookies)
                    .filter(it -> !it.isEmpty())
                    .ifPresent(request::setCookies);
            return request;
        }

        private Collection<Har.Param> mapParams(final byte[] body, final MediaType type, final MultivaluedMap<String, String> headers) {
            final Annotation[] annotations = new Annotation[0];
            try {
                return providers.getMessageBodyReader(Form.class, Form.class, annotations, type)
                        .readFrom(Form.class, Form.class, annotations, type, headers, new ByteArrayInputStream(body))
                        .asMap().entrySet().stream()
                        .map(it -> {
                            final Har.Param param = new Har.Param();
                            param.setName(it.getKey());
                            param.setValue(it.getValue().stream().map(String::valueOf).collect(joining(",")));
                            // todo: complete, see FormEncodingProvider
                            return param;
                        })
                        .collect(toList());
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }

        private String mapUrl(final URI uri) {
            final String query = uri.getQuery();
            final String url = uri.toASCIIString();
            if (query == null || query.isEmpty()) {
                return url;
            }
            return url.replace(query, "");
        }

        private Collection<Har.Query> mapQuery(final String query) {
            return query == null || query.isEmpty() ?
                    null :
                    Stream.of(query.split("&"))
                        .map(it -> {
                            final int sep = it.indexOf('=');
                            if (sep > 0) {
                                return new String[]{it.substring(0, sep), it.substring(sep + 1)};
                            }
                            return new String[]{it, ""};
                        })
                        .map(arr -> {
                            final Har.Query q = new Har.Query();
                            q.setName(arr[0]);
                            q.setValue(arr[1]);
                            return q;
                        })
                        .collect(toList());
        }

        private Har.Header mapHeader(final Map.Entry<String, List<Object>> header) {
            final Har.Header copy = new Har.Header();
            copy.setName(header.getKey());
            copy.setValue(header.getValue().stream().map(String::valueOf).collect(joining(",")));
            return copy;
        }

        private Collection<Har.Cookie> toCookies(final Collection<? extends Cookie> cookies) {
            return cookies.stream()
                    .map(this::mapCookie)
                    .collect(toList());
        }

        private Har.Cookie mapCookie(final Cookie it) {
            final Har.Cookie cookie = new Har.Cookie();
            cookie.setName(it.getName());
            cookie.setValue(it.getValue());
            cookie.setDomain(it.getDomain());
            cookie.setPath(it.getPath());
            if (NewCookie.class.isInstance(it)) {
                final NewCookie nc = NewCookie.class.cast(it);
                cookie.setHttpOnly(nc.isHttpOnly());
                cookie.setSecure(nc.isSecure());
                if (nc.getExpiry() != null) {
                    final ZoneId utc = ZoneId.of("UTC");
                    cookie.setExpires(ZonedDateTime.of(LocalDateTime.ofInstant(nc.getExpiry().toInstant(), utc), utc));
                }
            }
            return cookie;
        }

        private byte[] read(final InputStream entityStream, final int estimatedSize) {
            final ByteArrayOutputStream bufferized = new ByteArrayOutputStream(Math.max(1, estimatedSize));
            byte[] buffer = new byte[1024];
            int read;
            try {
                while ((read = entityStream.read(buffer)) >= 0) {
                    bufferized.write(buffer, 0, read);
                }
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
            return bufferized.toByteArray();
        }

        private void addEntry(final Har.Entry entry) {
            final Har.Log log = container.getLog();
            if (log.getEntries() == null) {
                log.setEntries(new CopyOnWriteArrayList<>());
            }
            log.getEntries().add(entry);
        }
    }

    @RequiredArgsConstructor
    private static class CapturingStream extends OutputStream {
        private final OutputStream delegate;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        @Override
        public void write(final int b) throws IOException {
            delegate.write(b);
            buffer.write(b);
        }

        @Override
        public void write(final byte[] b) throws IOException {
            delegate.write(b);
            buffer.write(b);
        }

        @Override
        public void write(final byte[] b, final int off, final int len) throws IOException {
            delegate.write(b, off, len);
            buffer.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
            buffer.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
            buffer.close();
        }
    }
}
