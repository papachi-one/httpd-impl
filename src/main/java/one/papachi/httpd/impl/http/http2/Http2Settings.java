package one.papachi.httpd.impl.http.http2;

public class Http2Settings {

    private int headerTableSize = 4096;

    private int enablePush = 1;

    private int maxConcurrentStreams = Integer.MAX_VALUE;

    private int initialWindowSize = 65535;

    private int maxFrameSize = 16384;

    private int maxHeaderListSize = Integer.MAX_VALUE;

    public Http2Settings() {
    }
    public int getHeaderTableSize() {
        return headerTableSize;
    }

    public int getEnablePush() {
        return enablePush;
    }

    public int getMaxConcurrentStreams() {
        return maxConcurrentStreams;
    }

    public int getInitialWindowSize() {
        return initialWindowSize;
    }

    public int getMaxFrameSize() {
        return maxFrameSize;
    }

    public int getMaxHeaderListSize() {
        return maxHeaderListSize;
    }

    public void setHeaderTableSize(int headerTableSize) {
        this.headerTableSize = headerTableSize;
    }

    public void setEnablePush(int enablePush) {
        this.enablePush = enablePush;
    }

    public void setMaxConcurrentStreams(int maxConcurrentStreams) {
        this.maxConcurrentStreams = maxConcurrentStreams;
    }

    public void setInitialWindowSize(int initialWindowSize) {
        this.initialWindowSize = initialWindowSize;
    }

    public void setMaxFrameSize(int maxFrameSize) {
        this.maxFrameSize = maxFrameSize;
    }

    public void setMaxHeaderListSize(int maxHeaderListSize) {
        this.maxHeaderListSize = maxHeaderListSize;
    }

}
