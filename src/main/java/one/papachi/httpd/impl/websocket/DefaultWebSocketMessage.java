package one.papachi.httpd.impl.websocket;

import one.papachi.httpd.api.websocket.WebSocketMessage;
import one.papachi.httpd.impl.net.AsynchronousInputChannel;

import java.io.InputStream;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.ReadableByteChannel;

public class DefaultWebSocketMessage extends AsynchronousInputChannel implements WebSocketMessage {

    public static class DefaultBuilder implements Builder {

        private Type type = Type.TEXT;
        private long length = Long.MAX_VALUE;
        private Object object;

        @Override
        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        @Override
        public Builder payload(AsynchronousByteChannel channel) {
            object = channel;
            return this;
        }

        @Override
        public Builder payload(AsynchronousFileChannel channel) {
            object = channel;
            return this;
        }

        @Override
        public Builder payload(ReadableByteChannel channel) {
            object = channel;
            return this;
        }

        @Override
        public Builder payload(InputStream inputStream) {
            object = inputStream;
            return this;
        }

        @Override
        public WebSocketMessage build() {
            return new DefaultWebSocketMessage(type, object, length);
        }
    }

    private final Type type;

    DefaultWebSocketMessage(Type type, Object input, Long maxLength) {
        super(input, maxLength);
        this.type = type;
    }

    @Override
    public Type getType() {
        return type;
    }

}
