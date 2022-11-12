package one.papachi.httpd.impl.http;

public class Http2Settings {

    private int headerTableSize = 4096;

    private int enablePush = 1;

    private int maxConcurrentStreams = Integer.MAX_VALUE;

    private int initialWindowSize = 65535;

    private int maxFrameSize = 16384;

    private int maxHeaderListSize = Integer.MAX_VALUE;

    public Http2Settings() {
    }
    synchronized public int getHeaderTableSize() {
        return headerTableSize;
    }

    synchronized public int getEnablePush() {
        return enablePush;
    }

    synchronized public int getMaxConcurrentStreams() {
        return maxConcurrentStreams;
    }

    synchronized public int getInitialWindowSize() {
        return initialWindowSize;
    }

    synchronized public int getMaxFrameSize() {
        return maxFrameSize;
    }

    synchronized public int getMaxHeaderListSize() {
        return maxHeaderListSize;
    }

    synchronized public void setHeaderTableSize(int headerTableSize) {
        this.headerTableSize = headerTableSize;
    }

    synchronized public void setEnablePush(int enablePush) {
        this.enablePush = enablePush;
    }

    synchronized public void setMaxConcurrentStreams(int maxConcurrentStreams) {
        this.maxConcurrentStreams = maxConcurrentStreams;
    }

    synchronized public void setInitialWindowSize(int initialWindowSize) {
        this.initialWindowSize = initialWindowSize;
    }

    synchronized public void setMaxFrameSize(int maxFrameSize) {
        this.maxFrameSize = maxFrameSize;
    }

    synchronized public void setMaxHeaderListSize(int maxHeaderListSize) {
        this.maxHeaderListSize = maxHeaderListSize;
    }

}
