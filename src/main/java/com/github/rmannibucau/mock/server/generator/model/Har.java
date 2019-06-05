package com.github.rmannibucau.mock.server.generator.model;

import java.time.ZonedDateTime;
import java.util.Collection;

import javax.json.bind.annotation.JsonbDateFormat;

import lombok.Data;

// see http://www.softwareishard.com/blog/har-12-spec/
@Data
public class Har {
    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSX";

    private Log log = new Log();

    @Data
    public static class Log {
        private String version = "1.2";
        private Creator creator;
        private Browser browser;
        private Collection<Page> pages;
        private Collection<Entry> entries;
        private String comment = "";
    }

    @Data
    public static class Request {
        private String method;
        private String url;
        private String httpVersion = "HTTP/1.1";
        private Collection<Cookie> cookies;
        private Collection<Header> headers;
        private Collection<Query> queryString;
        private PostData postData;
        private long headerSize = -1;
        private long bodySize;
        private String comment = "";
    }

    @Data
    public static class Response {
        private int status = 200;
        private String statusText = "OK";
        private String httpVersion = "HTTP/1.1";
        private Collection<Cookie> cookies;
        private Collection<Header> headers;
        private Content content;
        private String redirectURL;
        private long headersSize;
        private long bodySize;
        private String comment = "";
    }

    @Data
    public static class Query {
        private String name;
        private String value;
        private String comment = "";
    }

    @Data
    public static class PostData {
        private String mimeType;
        private Collection<Param> params;
        private String text;
        private String comment = "";
    }

    @Data
    public static class Param {
        private String name;
        private String value;
        private String fileName;
        private String contentType;
        private String comment = "";
    }

    @Data
    public static class Cache {
        private BeforeRequest beforeRequest;
        private AfterRequest afterRequest;
        private String comment = "";
    }

    @Data
    public static abstract class CacheRequest {
        @JsonbDateFormat(DATE_FORMAT)
        private ZonedDateTime expires;

        @JsonbDateFormat(DATE_FORMAT)
        private ZonedDateTime lastAccess;

        private String eTag;
        private int hitCount;
        private String comment;
    }

    public static class BeforeRequest extends CacheRequest {
    }

    public static class AfterRequest extends CacheRequest {
    }

    @Data
    public static class Timings {
        private long blocked = -1;
        private long dns = -1;
        private long connect = -1;
        private long send = 0;
        private long wait = 0;
        private long receive = 0;
        private long ssl = -1;
        private String comment = "";
    }

    @Data
    public static class Cookie {
        @JsonbDateFormat(DATE_FORMAT)
        private ZonedDateTime expires;
        private String name;
        private String value;
        private String path;
        private String domain;
        private boolean httpOnly;
        private boolean secure;
        private String comment;
    }

    @Data
    public static class Header {
        private String name;
        private String value;
        private String comment;
    }

    @Data
    public static class Content {
        private long size;
        private int compression;
        private String mimeType;
        private String text;
        private String encoding; // base64 if text is encoded
        private String comment;
    }

    @Data
    public static class Entry {
        @JsonbDateFormat(DATE_FORMAT)
        private ZonedDateTime startedDateTime;
        private String pageref;
        private long time;
        private Request request;
        private Response response;
        private Cache cache;
        private Timings timings;
        private String serverIPAddress;
        private String connection;
        private String comment;
    }

    @Data
    public static class Page {
        @JsonbDateFormat(DATE_FORMAT)
        private ZonedDateTime startedDateTime;
        private String id;
        private String title;
        private PageTiming pageTimings;
        private String comment = "";
    }

    @Data
    public static class PageTiming {
        private long onContentLoad;
        private long onLoad;
        private String comment;
    }

    @Data
    public static abstract class BaseIdentity {
        private String name;
        private String version;
        private String comment = "";
    }

    public static class Creator extends BaseIdentity {
    }

    public static class Browser extends BaseIdentity {
    }
}
