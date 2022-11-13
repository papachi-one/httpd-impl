package one.papachi.httpd.impl.websocket;

import one.papachi.httpd.api.websocket.WebSocketStream;
import one.papachi.httpd.impl.net.AsynchronousInputChannel;

import java.io.InputStream;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.ReadableByteChannel;

public class DefaultWebSocketStream extends AsynchronousInputChannel implements WebSocketStream {

    public static class DefaultBuilder implements Builder {

        private Object object;
        private long length = Long.MAX_VALUE;

        @Override
        public Builder input(AsynchronousByteChannel channel) {
            object = channel;
            return this;
        }

        @Override
        public Builder input(AsynchronousFileChannel channel) {
            object = channel;
            return this;
        }

        @Override
        public Builder input(ReadableByteChannel channel) {
            object = channel;
            return this;
        }

        @Override
        public Builder input(InputStream inputStream) {
            object = inputStream;
            return this;
        }

        @Override
        public WebSocketStream build() {
            return new DefaultWebSocketStream(object, length);
        }
    }

    DefaultWebSocketStream(Object input, long length) {
        super(input, length);
    }

}
